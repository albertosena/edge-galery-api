/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.google.ai.edge.gallery.server

import io.ktor.http.HttpStatusCode
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.response.respondTextWriter
import io.ktor.server.request.receive
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope

@Serializable
data class HealthResponse(
  val status: String = "ok",
  val server: String = "ai-edge-gallery",
  @SerialName("model_loaded") val modelLoaded: Boolean,
  val model: String?,
)

@Serializable
data class OpenAiModel(
  val id: String,
  @SerialName("object") val objectType: String = "model",
  val created: Long = 0,
  @SerialName("owned_by") val ownedBy: String = "local",
)

@Serializable
data class OpenAiModelList(
  @SerialName("object") val objectType: String = "list",
  val data: List<OpenAiModel>,
)

@Singleton
class LocalApiServer @Inject constructor(
  private val activeModelRegistry: ActiveModelRegistry,
  private val inferenceGateway: OpenAiInferenceGateway,
  private val logs: LocalApiLogStore,
) {
  private val lock = Any()
  private var server: EmbeddedServer<*, *>? = null

  fun start(host: String, port: Int) {
    synchronized(lock) {
      check(server == null) { "Local API server is already running." }
      server =
        embeddedServer(CIO, host = host, port = port) {
          localApiModule(activeModelRegistry, inferenceGateway, logs)
        }
          .start(wait = false)
    }
  }

  fun stop() {
    synchronized(lock) {
      server?.stop(gracePeriodMillis = 500, timeoutMillis = 2_000)
      server = null
    }
  }
}

internal fun Application.localApiModule(
  activeModelRegistry: ActiveModelRegistry,
  inferenceGateway: OpenAiInferenceGateway,
  logs: LocalApiLogStore,
) {
  val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
  install(ContentNegotiation) { json(json) }
  routing {
    get("/health") {
      val model = activeModelRegistry.activeLiteRtModel()
      call.respond(
        HttpStatusCode.OK,
        HealthResponse(modelLoaded = model != null, model = model?.name),
      )
    }
    get("/v1/models") {
      val model = activeModelRegistry.activeLiteRtModel()
      call.respond(
        HttpStatusCode.OK,
        OpenAiModelList(data = model?.let { listOf(OpenAiModel(id = it.name)) } ?: emptyList()),
      )
    }
    post("/v1/chat/completions") {
      val requestId = UUID.randomUUID().toString().take(8)
      try {
        val request = call.receive<OpenAiChatRequest>()
        val inferenceStartedAt = System.currentTimeMillis()
        val multimodal = request.messages.any { it.content.toString().contains("\"image_url\"") }
        logs.add("New chat $requestId • ${request.model} • ${if (multimodal) "multimodal" else "text"}")
        if (!request.stream) {
          var generatedTokens = 0
          var firstTokenAt = 0L
          val response = inferenceGateway.complete(request) {
            generatedTokens += 1
            if (firstTokenAt == 0L) firstTokenAt = System.currentTimeMillis()
          }
          logs.recordInference(inferenceStartedAt, firstTokenAt, generatedTokens)
          logs.add("Chat $requestId completed")
          call.respond(HttpStatusCode.OK, response)
        } else {
          logs.add("Chat $requestId streaming${if (request.exposeThinking) " + thinking" else ""}")
          call.respondTextWriter(contentType = ContentType.Text.EventStream) {
            coroutineScope {
              val streamId = "chatcmpl-${UUID.randomUUID()}"
              val created = System.currentTimeMillis() / 1000
              val deltas = Channel<InferenceDelta>(Channel.UNLIMITED)
              var firstDeltaAt = 0L
              var generatedTokens = 0
              val inference = async {
                try {
                  inferenceGateway.complete(request) { deltas.trySend(it) }
                } finally {
                  deltas.close()
                }
              }
              for (delta in deltas) {
                generatedTokens += 1
                if (firstDeltaAt == 0L) {
                  firstDeltaAt = System.currentTimeMillis()
                  logs.add("Chat $requestId first chunk")
                }
                val chunk = OpenAiChatChunk(
                  id = streamId,
                  created = created,
                  model = request.model,
                  choices = listOf(
                    OpenAiChunkChoice(
                      delta = OpenAiDelta(
                        role = "assistant",
                        content = delta.content,
                        reasoningContent = delta.reasoningContent.takeIf { request.exposeThinking },
                      )
                    )
                  ),
                )
                write("data: ${json.encodeToString(chunk)}\n\n")
                flush()
              }
              inference.await()
              logs.recordInference(inferenceStartedAt, firstDeltaAt, generatedTokens)
              val finalChunk = OpenAiChatChunk(
                id = streamId,
                created = created,
                model = request.model,
                choices = listOf(OpenAiChunkChoice(delta = OpenAiDelta(), finishReason = "stop")),
              )
              write("data: ${json.encodeToString(finalChunk)}\n\n")
              write("data: [DONE]\n\n")
              flush()
              logs.add("Chat $requestId completed")
            }
          }
        }
      } catch (e: OpenAiRequestException) {
        logs.add("Chat $requestId rejected: ${e.code}")
        call.respond(
          HttpStatusCode.BadRequest,
          OpenAiErrorBody(error = OpenAiError(message = e.message ?: "Invalid request.", code = e.code)),
        )
      } catch (e: Exception) {
        logs.add("Chat $requestId failed: ${e.message ?: e.javaClass.simpleName}")
        call.respond(
          HttpStatusCode.InternalServerError,
          OpenAiErrorBody(error = OpenAiError(message = e.message ?: "Inference failed.", type = "server_error", code = "inference_error")),
        )
      }
    }
  }
}
