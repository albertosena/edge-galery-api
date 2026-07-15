/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.google.ai.edge.gallery.server

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class OpenAiModelsSerializationTest {
  private val json = Json { encodeDefaults = true }

  @Test
  fun activeModelUsesOpenAiListShape() {
    val encoded = json.encodeToString(OpenAiModelList(data = listOf(OpenAiModel(id = "gemma"))))
    val root = json.parseToJsonElement(encoded).jsonObject
    val model = root.getValue("data").jsonArray.single().jsonObject

    assertEquals("list", root.getValue("object").jsonPrimitive.content)
    assertEquals("gemma", model.getValue("id").jsonPrimitive.content)
    assertEquals("model", model.getValue("object").jsonPrimitive.content)
    assertEquals("local", model.getValue("owned_by").jsonPrimitive.content)
    assertEquals("0", model.getValue("created").jsonPrimitive.content)
  }

  @Test
  fun noActiveModelProducesEmptyList() {
    val encoded = json.encodeToString(OpenAiModelList(data = emptyList()))
    val root = json.parseToJsonElement(encoded).jsonObject

    assertEquals(0, root.getValue("data").jsonArray.size)
  }
}
