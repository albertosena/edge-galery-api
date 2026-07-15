/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.google.ai.edge.gallery.server

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.ai.edge.gallery.MainActivity
import com.google.ai.edge.gallery.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class LocalApiForegroundService : Service() {
  @Inject lateinit var server: LocalApiServer
  @Inject lateinit var controller: LocalApiController
  @Inject lateinit var activeModelRegistry: ActiveModelRegistry

  override fun onCreate() {
    super.onCreate()
    val manager = getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(
      NotificationChannel(CHANNEL_ID, "Local API Server", NotificationManager.IMPORTANCE_LOW)
    )
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_STOP -> stopServer()
      ACTION_START -> {
        val host = intent.getStringExtra(EXTRA_HOST) ?: "127.0.0.1"
        val port = intent.getIntExtra(EXTRA_PORT, 8080).coerceIn(1024, 65535)
        startForeground(NOTIFICATION_ID, notification(host, port))
        try {
          server.start(host, port)
          controller.onStarted(host, port)
        } catch (e: Exception) {
          controller.onStopped(e.message ?: "Failed to start server.")
          stopSelf()
        }
      }
    }
    return START_NOT_STICKY
  }

  private fun stopServer() {
    server.stop()
    controller.onStopped()
    stopForeground(STOP_FOREGROUND_REMOVE)
    stopSelf()
  }

  override fun onDestroy() {
    server.stop()
    controller.onStopped()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private fun notification(host: String, port: Int): android.app.Notification {
    val openIntent =
      PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    val stopIntent =
      PendingIntent.getService(
        this,
        1,
        Intent(this, LocalApiForegroundService::class.java).apply { action = ACTION_STOP },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    val model = activeModelRegistry.activeLiteRtModel()?.name ?: "No model loaded"
    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.drawable.icon)
      .setContentTitle("Local API Server")
      .setContentText("$host:$port • $model")
      .setOngoing(true)
      .setContentIntent(openIntent)
      .addAction(0, "Stop", stopIntent)
      .build()
  }

  companion object {
    const val ACTION_START = "com.google.ai.edge.gallery.server.START"
    const val ACTION_STOP = "com.google.ai.edge.gallery.server.STOP"
    const val EXTRA_HOST = "host"
    const val EXTRA_PORT = "port"
    private const val CHANNEL_ID = "local_api_server"
    private const val NOTIFICATION_ID = 8080
  }
}
