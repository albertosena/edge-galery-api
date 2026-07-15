/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.google.ai.edge.gallery.runtime

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes every generation that uses Gallery's single LiteRT-LM conversation. */
object SingleConversationCoordinator {
  private val mutex = Mutex()

  suspend fun <T> withConversation(block: suspend () -> T): T = mutex.withLock { block() }
}
