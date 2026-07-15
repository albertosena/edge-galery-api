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

@Singleton
class LocalApiLogStore @Inject constructor() {
  private val _entries = MutableStateFlow<List<String>>(emptyList())
  val entries = _entries.asStateFlow()

  fun add(message: String) {
    val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
    _entries.value = (_entries.value + "[$time] $message").takeLast(100)
  }

  fun clear() {
    _entries.value = emptyList()
  }
}
