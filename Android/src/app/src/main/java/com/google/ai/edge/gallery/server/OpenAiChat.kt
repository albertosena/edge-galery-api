/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.google.ai.edge.gallery.server

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.google.ai.edge.gallery.runtime.SingleConversationCoordinator
import com.google.ai.edge.gallery.runtime.runtimeHelper
import com.google.ai.edge.gallery.ui.llmchat.LlmModelInstance
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import java.util.UUID
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Serializable data class OpenAiChatMessage(val role: String, val content: JsonElement)

@Serializable
data class OpenAiChatRequest(
  val model: String,
  val messages: List<OpenAiChatMessage>,
  val stream: Boolean = false,
  val temperature: Double? = null,
  @SerialName("top_p") val topP: Double? = null,
  @SerialName("max_tokens") val maxTokens: Int? = null,
)

@Serializable data class OpenAiAssistantMessage(val role: String = "assistant", val content: String)
@Serializable data class OpenAiChoice(val index: Int = 0, val message: OpenAiAssistantMessage, @SerialName("finish_reason") val finishReason: String = "stop")
@Serializable data class OpenAiUsage(@SerialName("prompt_tokens") val promptTokens: Int = 0, @SerialName("completion_tokens") val completionTokens: Int = 0, @SerialName("total_tokens") val totalTokens: Int = 0)
@Serializable
data class OpenAiChatResponse(
  val id: String,
  @SerialName("object") val objectType: String = "chat.completion",
  val created: Long,
  val model: String,
  val choices: List<OpenAiChoice>,
  val usage: OpenAiUsage = OpenAiUsage(),
)

@Serializable data class OpenAiDelta(val role: String? = null, val content: String? = null)
@Serializable data class OpenAiChunkChoice(val index: Int = 0, val delta: OpenAiDelta, @SerialName("finish_reason") val finishReason: String? = null)
@Serializable data class OpenAiChatChunk(val id: String, @SerialName("object") val objectType: String = "chat.completion.chunk", val created: Long, val model: String, val choices: List<OpenAiChunkChoice>)

@Serializable data class OpenAiErrorBody(val error: OpenAiError)
@Serializable data class OpenAiError(val message: String, val type: String = "invalid_request_error", val param: String? = null, val code: String? = null)

@Singleton
class OpenAiInferenceGateway @Inject constructor(private val registry: ActiveModelRegistry) {
  suspend fun complete(request: OpenAiChatRequest): OpenAiChatResponse {
    val model = registry.activeLiteRtModel() ?: throw OpenAiRequestException("No model is loaded.", "model_not_loaded")
    if (request.model != model.name) throw OpenAiRequestException("The requested model is not active.", "model_not_found")
    val system =
      request.messages.filter { it.role == "system" }.joinToString("\n") { it.textContent() }
        .takeIf(String::isNotBlank)
    val conversational = request.messages.filter { it.role != "system" }
    val last = conversational.lastOrNull()
    if (last == null || last.role != "user") throw OpenAiRequestException("The final message must have role 'user'.", "invalid_messages")
    val history = conversational.dropLast(1).map {
      when (it.role) {
        "user" -> Message.user(it.textOnlyContent())
        "assistant" -> Message.model(it.textOnlyContent())
        else -> throw OpenAiRequestException("Unsupported message role: ${it.role}", "invalid_messages")
      }
    }

    val output = SingleConversationCoordinator.withConversation {
      model.runtimeHelper.resetConversation(
        model = model,
        systemInstruction = system?.let { Contents.of(it) },
        initialMessages = history,
      )
      val instance = model.instance as? LlmModelInstance
        ?: throw OpenAiRequestException("No LiteRT-LM model is loaded.", "model_not_loaded")
      val imageBytes = last.decodedImages()
      if (imageBytes.isNotEmpty() && !model.llmSupportImage) {
        throw OpenAiRequestException(
          "The active model does not support image input.",
          "vision_not_supported",
        )
      }
      val contents = imageBytes.mapTo(mutableListOf<Content>()) { Content.ImageBytes(it) }
      last.textContent().takeIf(String::isNotBlank)?.let { contents.add(Content.Text(it)) }
      if (contents.isEmpty()) {
        throw OpenAiRequestException("The final user message is empty.", "invalid_messages")
      }
      suspendCancellableCoroutine<String> { continuation ->
        val text = StringBuilder()
        continuation.invokeOnCancellation { instance.conversation.cancelProcess() }
        instance.conversation.sendMessageAsync(
          Contents.of(contents),
          object : MessageCallback {
            override fun onMessage(message: Message) { text.append(message.toString()) }
            override fun onDone() { if (continuation.isActive) continuation.resume(text.toString()) }
            override fun onError(throwable: Throwable) { if (continuation.isActive) continuation.resumeWithException(throwable) }
          },
        )
      }
    }
    return OpenAiChatResponse(
      id = "chatcmpl-${UUID.randomUUID()}",
      created = System.currentTimeMillis() / 1000,
      model = model.name,
      choices = listOf(OpenAiChoice(message = OpenAiAssistantMessage(content = output))),
    )
  }
}

class OpenAiRequestException(message: String, val code: String) : IllegalArgumentException(message)

private fun OpenAiChatMessage.textContent(): String {
  return when (val value = content) {
    is JsonPrimitive -> value.contentOrNull.orEmpty()
    is JsonArray ->
      value.mapNotNull { part ->
        (part as? JsonObject)?.takeIf { (it["type"] as? JsonPrimitive)?.content == "text" }
          ?.get("text")?.let { it as? JsonPrimitive }?.contentOrNull
      }.joinToString("\n")
    else -> ""
  }
}

private fun OpenAiChatMessage.textOnlyContent(): String {
  if (decodedImageUrls().isNotEmpty()) {
    throw OpenAiRequestException(
      "Image input is supported only in the final user message.",
      "invalid_messages",
    )
  }
  return textContent()
}

private fun OpenAiChatMessage.decodedImageUrls(): List<String> {
  val parts = content as? JsonArray ?: return emptyList()
  return parts.mapNotNull { part ->
    val obj = part as? JsonObject ?: return@mapNotNull null
    if ((obj["type"] as? JsonPrimitive)?.content != "image_url") return@mapNotNull null
    val imageUrl = obj["image_url"] as? JsonObject ?: return@mapNotNull null
    (imageUrl["url"] as? JsonPrimitive)?.contentOrNull
  }
}

private fun OpenAiChatMessage.decodedImages(): List<ByteArray> {
  return decodedImageUrls().map { dataUrl ->
    val match = DATA_IMAGE_REGEX.matchEntire(dataUrl)
      ?: throw OpenAiRequestException("Invalid or unsupported image data URL.", "invalid_image")
    val encoded = match.groupValues[2]
    val decoded =
      try {
        Base64.decode(encoded, Base64.DEFAULT)
      } catch (_: IllegalArgumentException) {
        throw OpenAiRequestException("Invalid Base64 image.", "invalid_image")
      }
    if (decoded.size > MAX_IMAGE_BYTES) {
      throw OpenAiRequestException("Image exceeds the 8 MB limit.", "image_too_large")
    }
    val bitmap = BitmapFactory.decodeByteArray(decoded, 0, decoded.size)
      ?: throw OpenAiRequestException("Invalid image data.", "invalid_image")
    ByteArrayOutputStream().use { output ->
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
      bitmap.recycle()
      output.toByteArray()
    }
  }
}

private val DATA_IMAGE_REGEX = Regex("^data:(image/(?:jpeg|png|webp));base64,(.+)$", RegexOption.DOT_MATCHES_ALL)
private const val MAX_IMAGE_BYTES = 8 * 1024 * 1024
