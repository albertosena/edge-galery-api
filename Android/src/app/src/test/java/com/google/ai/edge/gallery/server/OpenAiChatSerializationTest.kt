/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.google.ai.edge.gallery.server

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiChatSerializationTest {
  private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

  @Test
  fun parsesSystemUserAndAssistantRoles() {
    val request =
      json.decodeFromString<OpenAiChatRequest>(
        """{"model":"gemma","messages":[{"role":"system","content":"s"},{"role":"user","content":"u"},{"role":"assistant","content":"a"},{"role":"user","content":"next"}],"stream":true}"""
      )
    assertEquals(listOf("system", "user", "assistant", "user"), request.messages.map { it.role })
    assertTrue(request.stream)
  }

  @Test
  fun chunkUsesOpenAiObjectField() {
    val chunk = OpenAiChatChunk(id = "id", created = 0, model = "gemma", choices = emptyList())
    val root = json.parseToJsonElement(json.encodeToString(chunk)).jsonObject
    assertEquals("chat.completion.chunk", root.getValue("object").jsonPrimitive.content)
  }
}
