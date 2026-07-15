/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.google.ai.edge.gallery.server

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LocalApiState(
  val running: Boolean = false,
  val host: String = "127.0.0.1",
  val port: Int = 8080,
  val error: String? = null,
) {
  val baseUrl: String
    get() = "http://${if (host == "0.0.0.0") localIpv4Address() ?: "IP_DO_ANDROID" else "127.0.0.1"}:$port/v1"
}

@Singleton
class LocalApiController
@Inject
constructor(
  @ApplicationContext private val context: Context,
  val logs: LocalApiLogStore,
) {
  private val _state = MutableStateFlow(LocalApiState())
  val state: StateFlow<LocalApiState> = _state.asStateFlow()

  fun start(port: Int, allowLan: Boolean) {
    if (_state.value.running) return
    val intent =
      Intent(context, LocalApiForegroundService::class.java).apply {
        action = LocalApiForegroundService.ACTION_START
        putExtra(LocalApiForegroundService.EXTRA_PORT, port)
        putExtra(LocalApiForegroundService.EXTRA_HOST, if (allowLan) "0.0.0.0" else "127.0.0.1")
      }
    ContextCompat.startForegroundService(context, intent)
    logs.add("Starting server on ${if (allowLan) "LAN" else "device only"}:$port")
  }

  fun stop() {
    logs.add("Stopping server")
    context.startService(
      Intent(context, LocalApiForegroundService::class.java).apply {
        action = LocalApiForegroundService.ACTION_STOP
      }
    )
  }

  internal fun onStarted(host: String, port: Int) {
    _state.value = LocalApiState(running = true, host = host, port = port)
    logs.add("Server ready at ${_state.value.baseUrl}")
  }

  internal fun onStopped(error: String? = null) {
    _state.value = _state.value.copy(running = false, error = error)
    logs.add(error?.let { "Server stopped: $it" } ?: "Server stopped")
  }

  fun log(message: String) = logs.add(message)
}

private fun localIpv4Address(): String? {
  return runCatching {
      NetworkInterface.getNetworkInterfaces().toList().asSequence()
        .flatMap { it.inetAddresses.toList().asSequence() }
        .filterIsInstance<Inet4Address>()
        .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }
        ?.hostAddress
    }
    .getOrNull()
}
