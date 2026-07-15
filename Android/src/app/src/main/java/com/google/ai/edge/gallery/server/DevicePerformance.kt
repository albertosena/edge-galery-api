/* Copyright 2026 Google LLC. Licensed under the Apache License, Version 2.0. */
package com.google.ai.edge.gallery.server

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Debug
import android.os.PowerManager

data class DevicePerformance(
  val appPssMb: Int,
  val appGraphicsMb: Int,
  val availableRamMb: Long,
  val totalRamMb: Long,
  val lowMemory: Boolean,
  val batteryCelsius: Float,
  val thermalStatus: Int,
) {
  val thermalLabel: String
    get() = when (thermalStatus) {
      PowerManager.THERMAL_STATUS_NONE -> "None"
      PowerManager.THERMAL_STATUS_LIGHT -> "Light"
      PowerManager.THERMAL_STATUS_MODERATE -> "Moderate"
      PowerManager.THERMAL_STATUS_SEVERE -> "Severe"
      PowerManager.THERMAL_STATUS_CRITICAL -> "Critical"
      PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency"
      PowerManager.THERMAL_STATUS_SHUTDOWN -> "Shutdown"
      else -> "Unknown"
    }
}

fun readDevicePerformance(context: Context): DevicePerformance {
  val activityManager = context.getSystemService(ActivityManager::class.java)
  val memory = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
  val processMemory = Debug.MemoryInfo().also(Debug::getMemoryInfo)
  val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
  return DevicePerformance(
    appPssMb = processMemory.totalPss / 1024,
    appGraphicsMb = (processMemory.getMemoryStat("summary.graphics")?.toIntOrNull() ?: 0) / 1024,
    availableRamMb = memory.availMem / 1024 / 1024,
    totalRamMb = memory.totalMem / 1024 / 1024,
    lowMemory = memory.lowMemory,
    batteryCelsius = (battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f,
    thermalStatus = context.getSystemService(PowerManager::class.java).currentThermalStatus,
  )
}
