/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.google.ai.edge.gallery.server

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class InferenceMetrics(
  val tokensPerSecond: Double = 0.0,
  val timeToFirstTokenMs: Long = 0,
  val generatedTokens: Int = 0,
)

@Singleton
class LocalApiLogStore @Inject constructor() {
  private val _entries = MutableStateFlow<List<String>>(emptyList())
  val entries = _entries.asStateFlow()
  private val _metrics = MutableStateFlow(InferenceMetrics())
  val metrics = _metrics.asStateFlow()

  fun add(message: String) {
    val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
    _entries.value = (_entries.value + "[$time] $message").takeLast(100)
  }

  fun clear() {
    _entries.value = emptyList()
  }

  fun recordInference(startedAtMs: Long, firstTokenAtMs: Long, generatedTokens: Int) {
    val elapsedSeconds = ((System.currentTimeMillis() - startedAtMs).coerceAtLeast(1)) / 1000.0
    _metrics.value =
      InferenceMetrics(
        tokensPerSecond = generatedTokens / elapsedSeconds,
        timeToFirstTokenMs = (firstTokenAtMs - startedAtMs).coerceAtLeast(0),
        generatedTokens = generatedTokens,
      )
  }
}
