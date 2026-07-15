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
        val multimodal = request.messages.any { it.content.toString().contains("\"image_url\"") }
        logs.add("New chat $requestId • ${request.model} • ${if (multimodal) "multimodal" else "text"}")
        val response = inferenceGateway.complete(request)
        logs.add("Chat $requestId completed")
        if (!request.stream) {
          call.respond(HttpStatusCode.OK, response)
        } else {
          call.respondTextWriter(contentType = ContentType.Text.EventStream) {
            val choice = response.choices.single()
            val contentChunk =
              OpenAiChatChunk(
                id = response.id,
                created = response.created,
                model = response.model,
                choices = listOf(OpenAiChunkChoice(delta = OpenAiDelta(role = "assistant", content = choice.message.content))),
              )
            val finalChunk =
              OpenAiChatChunk(
                id = response.id,
                created = response.created,
                model = response.model,
                choices = listOf(OpenAiChunkChoice(delta = OpenAiDelta(), finishReason = "stop")),
              )
            write("data: ${json.encodeToString(contentChunk)}\n\n")
            flush()
            write("data: ${json.encodeToString(finalChunk)}\n\n")
            write("data: [DONE]\n\n")
            flush()
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
