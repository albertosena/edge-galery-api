/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.google.ai.edge.gallery.server

import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.RuntimeType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Application-scoped view of the model instance already owned by Gallery. */
@Singleton
class ActiveModelRegistry @Inject constructor() {
  private val _selectedModel = MutableStateFlow<Model?>(null)
  val selectedModel: StateFlow<Model?> = _selectedModel.asStateFlow()

  fun select(model: Model) {
    _selectedModel.value = model.takeUnless { it.name == "empty" }
  }

  fun clearIfSame(model: Model) {
    if (_selectedModel.value === model) {
      _selectedModel.value = null
    }
  }

  fun activeLiteRtModel(): Model? {
    return _selectedModel.value?.takeIf {
      it.runtimeType == RuntimeType.LITERT_LM && it.instance != null
    }
  }
}
