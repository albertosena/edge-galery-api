/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.google.ai.edge.gallery.server

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.data.Accelerator
import com.google.ai.edge.gallery.data.BuiltInTaskId
import com.google.ai.edge.gallery.data.ConfigKeys
import com.google.ai.edge.gallery.data.ModelDownloadStatusType
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalApiServerScreen(
  modelManagerViewModel: ModelManagerViewModel,
  navigateUp: () -> Unit,
  navigateToModels: () -> Unit,
) {
  val controller = modelManagerViewModel.localApiController
  val state by controller.state.collectAsState()
  val modelState by modelManagerViewModel.uiState.collectAsState()
  val logEntries by controller.logs.entries.collectAsState()
  val inferenceMetrics by controller.logs.metrics.collectAsState()
  var allowLan by remember { mutableStateOf(state.host == "0.0.0.0") }
  var portText by remember { mutableStateOf(state.port.toString()) }
  var starting by remember { mutableStateOf(false) }
  val context = LocalContext.current
  val model = modelState.selectedModel.takeUnless { it.name == "empty" }
  var memorySaver by remember(model?.name) {
    mutableStateOf(model?.name?.contains("E4B", ignoreCase = true) == true)
  }
  var performance by remember { mutableStateOf(readDevicePerformance(context)) }
  LaunchedEffect(Unit) {
    while (true) {
      performance = withContext(Dispatchers.Default) { readDevicePerformance(context) }
      delay(3_000)
    }
  }
  val models =
    modelManagerViewModel.getAllModels().filter {
      it.isLlm &&
        modelState.modelDownloadStatus[it.name]?.status == ModelDownloadStatusType.SUCCEEDED
    }
  val modelDownloaded =
    model?.let {
      modelState.modelDownloadStatus[it.name]?.status == ModelDownloadStatusType.SUCCEEDED
    } == true
  val backend =
    model?.getStringConfigValue(ConfigKeys.ACCELERATOR, Accelerator.GPU.label) ?: "—"

  Scaffold(
    topBar = {
      CenterAlignedTopAppBar(
        title = { Text("Local API Server") },
        navigationIcon = {
          IconButton(onClick = navigateUp) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
          }
        },
      )
    }
  ) { padding ->
    Column(
      modifier =
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Text(
        if (state.running) "Running" else "Stopped",
        style = MaterialTheme.typography.headlineSmall,
        color = if (state.running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
      )

      Text("Active model: ${model?.name ?: "No model selected"}")
      Text("Backend: $backend")

      Text("Models", style = MaterialTheme.typography.titleMedium)
      if (models.isEmpty()) {
        Text("No downloaded models. Download a model from the Models screen first.")
      }
      models.forEach { candidate ->
        val downloaded = true
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(candidate.displayName.ifEmpty { candidate.name })
            Text(
              when {
                candidate === model -> if (downloaded) "Selected • Downloaded" else "Selected"
                downloaded -> "Downloaded"
                else -> "Available to download"
              },
              style = MaterialTheme.typography.bodySmall,
            )
          }
          OutlinedButton(
            enabled = !state.running,
            onClick = {
              if (downloaded) {
                val select = {
                  modelManagerViewModel.selectModel(candidate)
                  controller.log("Selected model: ${candidate.name}")
                }
                val task = modelManagerViewModel.getTaskById(BuiltInTaskId.LLM_CHAT)
                val loadedModels =
                  modelManagerViewModel.getAllModels().filter {
                    it !== candidate && it.instance != null
                  }.distinctBy { System.identityHashCode(it.instance) }
                if (loadedModels.isNotEmpty() && task != null) {
                  controller.log("Unloading ${loadedModels.size} previous model instance(s)")
                  var remaining = loadedModels.size
                  loadedModels.forEach { loaded ->
                    modelManagerViewModel.cleanupModel(
                      context = context,
                      task = task,
                      model = loaded,
                      onDone = {
                        controller.log("Unloaded: ${loaded.name}")
                        remaining -= 1
                        if (remaining == 0) select()
                      },
                    )
                  }
                } else {
                  select()
                }
              } else {
                modelManagerViewModel.downloadModel(
                  task = modelManagerViewModel.getTaskById(BuiltInTaskId.LLM_CHAT),
                  model = candidate,
                )
              }
            },
          ) {
            Text(if (candidate === model) "Selected" else "Select")
          }
        }
      }
      OutlinedButton(onClick = navigateToModels, modifier = Modifier.fillMaxWidth()) {
        Text("Browse all models")
      }

      if (model != null && model.accelerators.isNotEmpty()) {
        Text("Runtime", style = MaterialTheme.typography.titleMedium)
        Text("Accelerator", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          model.accelerators.forEach { accelerator ->
            OutlinedButton(
              enabled = !state.running,
              onClick = {
                model.configValues =
                  model.configValues.toMutableMap().apply {
                    put(ConfigKeys.ACCELERATOR.label, accelerator.label)
                  }
                modelManagerViewModel.updateConfigValuesUpdateTrigger()
                controller.log(
                  "Backend selected: ${accelerator.label.uppercase()}" +
                    if (accelerator == Accelerator.NPU) " (NPU requested)" else ""
                )
                modelManagerViewModel.getTaskById(BuiltInTaskId.LLM_CHAT)?.let { task ->
                  modelManagerViewModel.cleanupModel(context = context, task = task, model = model)
                }
              },
            ) {
              Text(accelerator.label.uppercase())
            }
          }
        }
        if (model.llmSupportImage) {
          Text("Vision input supported • ${model.visionAccelerator.label.uppercase()} encoder")
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text("Memory saver")
          Text("1024 output tokens; image/audio encoders off. Recommended for E4B.", style = MaterialTheme.typography.bodySmall)
        }
        Switch(
          checked = memorySaver,
          enabled = !state.running && !starting,
          onCheckedChange = {
            memorySaver = it
            controller.log("Memory saver ${if (it) "enabled" else "disabled"}")
          },
        )
      }

      Text("Network", style = MaterialTheme.typography.titleMedium)
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text("Allow LAN access")
          Text(
            if (allowLan) "Listen on all network interfaces" else "Only this Android device",
            style = MaterialTheme.typography.bodySmall,
          )
        }
        Switch(
          checked = allowLan,
          enabled = !state.running,
          onCheckedChange = { allowLan = it },
        )
      }

      OutlinedTextField(
        value = portText,
        onValueChange = { portText = it.filter(Char::isDigit).take(5) },
        enabled = !state.running,
        label = { Text("Port") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
      )

      if (state.running) {
        Text("API connection", style = MaterialTheme.typography.titleMedium)
        Text("OpenAI Base URL")
        Text(state.baseUrl, style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(
          onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("OpenAI Base URL", state.baseUrl))
          },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("Copy URL")
        }
      }

      state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

      Spacer(modifier = Modifier.height(8.dp))
      Button(
        onClick = {
          if (state.running) {
            controller.stop()
          } else if (model?.instance != null) {
            controller.start(portText.toIntOrNull() ?: 8080, allowLan)
          } else if (model != null) {
            val task = modelManagerViewModel.getTaskById(BuiltInTaskId.LLM_CHAT)
            if (task != null) {
              starting = true
              if (memorySaver) {
                model.configValues = model.configValues.toMutableMap().apply {
                  put(ConfigKeys.MAX_TOKENS.label, 1024)
                }
                controller.log("Memory saver applied: 1024 tokens, multimodal off")
              }
              modelManagerViewModel.initializeModel(
                context = context,
                task = task,
                model = model,
                localApiMode = true,
                enableMultimodal = !memorySaver,
                onDone = {
                  starting = false
                  val loadedBackend =
                    model.getStringConfigValue(ConfigKeys.ACCELERATOR, Accelerator.CPU.label)
                  controller.log(
                    "Model loaded • backend ${loadedBackend.uppercase()} • " +
                      (if (loadedBackend == Accelerator.NPU.label) "NPU active" else "NPU not active") +
                      if (model.llmSupportImage && !memorySaver) " • multimodal enabled" else " • multimodal off"
                  )
                  controller.start(portText.toIntOrNull() ?: 8080, allowLan)
                },
                onError = {
                  starting = false
                  controller.log("Model load failed: $it")
                },
              )
            }
          }
        },
        enabled = !starting && (state.running || modelDownloaded),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(if (state.running) "Stop Server" else if (starting) "Loading Model…" else "Start Server")
      }

      if (model?.instance == null) {
        Text(
          "The selected model will be loaded when the server starts.",
          style = MaterialTheme.typography.bodySmall,
        )
      }

      ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text("Device performance", style = MaterialTheme.typography.titleMedium)
          Text(
            if (inferenceMetrics.generatedTokens > 0)
              "%.1f tokens/s | First token: %d ms".format(
                inferenceMetrics.tokensPerSecond,
                inferenceMetrics.timeToFirstTokenMs,
              )
            else "Tokens/s: waiting for the first chat",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
          )
          if (inferenceMetrics.generatedTokens > 0) {
            Text("Generated fragments: ${inferenceMetrics.generatedTokens} (approximate tokens)", style = MaterialTheme.typography.bodySmall)
          }
          HorizontalDivider()
          Text("App memory: ${performance.appPssMb} MB | Graphics: ${performance.appGraphicsMb} MB")
          Text("Available RAM: ${performance.availableRamMb} / ${performance.totalRamMb} MB")
          Text(
            "Battery: %.1f C | Thermal: %s%s".format(
              performance.batteryCelsius,
              performance.thermalLabel,
              if (performance.lowMemory) " | LOW MEMORY" else "",
            ),
            color = if (performance.thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE || performance.lowMemory)
              MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
          )
          OutlinedButton(
            onClick = {
              val powerManager = context.getSystemService(PowerManager::class.java)
              if (!powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
                runCatching {
                  context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")))
                }.onFailure {
                  context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                }
              } else controller.log("Battery optimization is already unrestricted")
            },
            modifier = Modifier.fillMaxWidth(),
          ) { Text("Allow unrestricted battery use") }
        }
      }

      HorizontalDivider()
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("Server logs", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { controller.logs.clear() }) { Text("Clear") }
      }
      if (logEntries.isEmpty()) {
        Text("No server activity yet.", style = MaterialTheme.typography.bodySmall)
      } else {
        logEntries.takeLast(30).forEach {
          Text(it, style = MaterialTheme.typography.bodySmall)
        }
      }
    }
  }
}
