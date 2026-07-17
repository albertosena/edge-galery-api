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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.data.Accelerator
import com.google.ai.edge.gallery.data.BuiltInTaskId
import com.google.ai.edge.gallery.data.ConfigKeys
import com.google.ai.edge.gallery.data.ModelDownloadStatusType
import com.google.ai.edge.gallery.data.requiresNpuBackend
import com.google.ai.edge.gallery.context.ModelGenerationSettingsStore
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
  var portText by remember { mutableStateOf(state.port.toString()) }
  var starting by remember { mutableStateOf(false) }
  var backendError by remember { mutableStateOf<String?>(null) }
  val context = LocalContext.current
  val model = modelState.selectedModel.takeUnless { it.name == "empty" }
  var contextLengthText by remember(model?.name) {
    mutableStateOf(model?.getIntConfigValue(ConfigKeys.CONTEXT_LENGTH, 4096)?.toString() ?: "4096")
  }
  var maximumResponseTokensText by remember(model?.name) {
    mutableStateOf(model?.getIntConfigValue(ConfigKeys.MAX_OUTPUT_TOKENS, 2048)?.toString() ?: "2048")
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
                  candidate.configValues = candidate.configValues.toMutableMap().apply {
                    put(ConfigKeys.CONTEXT_LENGTH.label, 4096)
                    put(
                      ConfigKeys.ACCELERATOR.label,
                      if (candidate.requiresNpuBackend) Accelerator.NPU.label
                      else if (candidate.accelerators.contains(Accelerator.GPU)) Accelerator.GPU.label
                      else Accelerator.CPU.label,
                    )
                  }
                  contextLengthText = "4096"
                  ModelGenerationSettingsStore.save(context, candidate)
                  modelManagerViewModel.selectModel(candidate)
                  modelManagerViewModel.updateConfigValuesUpdateTrigger()
                  controller.log(
                    "Selected model: ${candidate.name} • 4K context" +
                      if (candidate.requiresNpuBackend) " • NPU required" else ""
                  )
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
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Use NPU acceleration", style = MaterialTheme.typography.titleMedium)
            Text(
              if (model.requiresNpuBackend) "Required by this Qualcomm-compiled model"
              else if (backend == Accelerator.NPU.label) "Enabled • experimental"
              else "Disabled • using ${backend.uppercase()}",
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Switch(
            checked = backend == Accelerator.NPU.label,
            enabled = !model.requiresNpuBackend && !state.running && !starting,
            onCheckedChange = { enabled ->
              backendError = null
              val selected =
                if (enabled) Accelerator.NPU
                else if (model.accelerators.contains(Accelerator.GPU)) Accelerator.GPU
                else Accelerator.CPU
              model.configValues = model.configValues.toMutableMap().apply {
                put(ConfigKeys.ACCELERATOR.label, selected.label)
              }
              modelManagerViewModel.updateConfigValuesUpdateTrigger()
              controller.log("${selected.label.uppercase()} backend selected")
              modelManagerViewModel.getTaskById(BuiltInTaskId.LLM_CHAT)?.let { task ->
                modelManagerViewModel.cleanupModel(context = context, task = task, model = model)
              }
            },
          )
        }
        if (model.llmSupportImage) {
          Text("Vision input supported • ${model.visionAccelerator.label.uppercase()} encoder")
        }
      }

      if (model != null) {
        ElevatedCard(
          modifier = Modifier.fillMaxWidth(),
          colors =
            CardDefaults.elevatedCardColors(
              containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
            ),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
          ) {
            Text(
              "Generation settings",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary,
            )
            Text(
              "Balance conversation memory, answer length and device performance.",
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val contextValue = contextLengthText.toIntOrNull() ?: 0
            OutlinedTextField(
              value = contextLengthText,
              onValueChange = { contextLengthText = it.filter(Char::isDigit).take(5) },
              enabled = !state.running && !starting,
              label = { Text("Context length", style = MaterialTheme.typography.bodyLarge) },
              textStyle = MaterialTheme.typography.titleMedium,
              supportingText = {
                Text(
                  when {
                    contextValue > 32768 -> "Maximum supported setting is 32,768 tokens."
                    contextValue > 24576 -> "High memory use: 32K can be slower or fail to load."
                    contextValue > 16384 -> "Higher memory use and longer prompt processing."
                    else -> "Conversation capacity. Changing it reloads the model."
                  },
                  style = MaterialTheme.typography.bodyMedium,
                  color =
                    if (contextValue > 24576) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
              },
              isError = contextValue !in 2048..32768,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              modifier = Modifier.fillMaxWidth(),
            )
            Text("Quick presets", style = MaterialTheme.typography.titleSmall)
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
              maxItemsInEachRow = 3,
            ) {
              listOf(4096, 8192, 16384, 24576, 32768).forEach { preset ->
                val selected = contextValue == preset
                if (selected) {
                  FilledTonalButton(
                    enabled = !state.running && !starting,
                    onClick = { contextLengthText = preset.toString() },
                    modifier = Modifier.widthIn(min = 88.dp),
                  ) { Text("${preset / 1024}K", style = MaterialTheme.typography.labelLarge) }
                } else {
                  OutlinedButton(
                    enabled = !state.running && !starting,
                    onClick = { contextLengthText = preset.toString() },
                    modifier = Modifier.widthIn(min = 88.dp),
                  ) { Text("${preset / 1024}K", style = MaterialTheme.typography.labelLarge) }
                }
              }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val outputValue = maximumResponseTokensText.toIntOrNull() ?: 0
            OutlinedTextField(
              value = maximumResponseTokensText,
              onValueChange = { maximumResponseTokensText = it.filter(Char::isDigit).take(4) },
              enabled = !state.running && !starting,
              label = { Text("Maximum response tokens", style = MaterialTheme.typography.bodyLarge) },
              textStyle = MaterialTheme.typography.titleMedium,
              supportingText = {
                Text(
                  "Tokens reserved for each answer. Recommended: 2,048.",
                  style = MaterialTheme.typography.bodyMedium,
                )
              },
              isError = outputValue !in 1..4096,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
              maxItemsInEachRow = 3,
            ) {
              listOf(512, 1024, 2048, 4096).forEach { preset ->
                val selected = outputValue == preset
                if (selected) {
                  FilledTonalButton(
                    enabled = !state.running && !starting,
                    onClick = { maximumResponseTokensText = preset.toString() },
                    modifier = Modifier.widthIn(min = 88.dp),
                  ) { Text(preset.toString(), style = MaterialTheme.typography.labelLarge) }
                } else {
                  OutlinedButton(
                    enabled = !state.running && !starting,
                    onClick = { maximumResponseTokensText = preset.toString() },
                    modifier = Modifier.widthIn(min = 88.dp),
                  ) { Text(preset.toString(), style = MaterialTheme.typography.labelLarge) }
                }
              }
            }
          }
        }
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
            // The model may have been initialized from another screen. Re-register it immediately
            // before starting the foreground service so API requests see the existing instance.
            modelManagerViewModel.selectModel(model)
            controller.start(portText.toIntOrNull() ?: 8080, true)
          } else if (model != null) {
            val task = modelManagerViewModel.getTaskById(BuiltInTaskId.LLM_CHAT)
            val requestedContext = contextLengthText.toIntOrNull()
            val requestedOutput = maximumResponseTokensText.toIntOrNull()
            if (task != null && requestedContext in 2048..32768 && requestedOutput in 1..4096) {
              starting = true
              backendError = null
              model.configValues = model.configValues.toMutableMap().apply {
                put(ConfigKeys.CONTEXT_LENGTH.label, requestedContext!!)
                put(ConfigKeys.MAX_OUTPUT_TOKENS.label, requestedOutput!!)
              }
              controller.log(
                "${model.getStringConfigValue(ConfigKeys.ACCELERATOR, Accelerator.NPU.label).uppercase()} backend requested for ${model.name}"
              )
              ModelGenerationSettingsStore.save(context, model)
              modelManagerViewModel.initializeModel(
                context = context,
                task = task,
                model = model,
                localApiMode = true,
                enableMultimodal = true,
                onDone = {
                  starting = false
                  // Keep registry publication and server startup ordered. The foreground service
                  // reads this singleton as soon as its start intent is delivered.
                  modelManagerViewModel.selectModel(model)
                  val loadedBackend =
                    model.getStringConfigValue(ConfigKeys.ACCELERATOR, Accelerator.CPU.label)
                  controller.log(
                    "Model loaded • backend ${loadedBackend.uppercase()} • " +
                      (if (loadedBackend == Accelerator.NPU.label) "NPU active" else "NPU not active") +
                      if (model.llmSupportImage) " • multimodal enabled" else " • text only"
                  )
                  controller.start(portText.toIntOrNull() ?: 8080, true)
                },
                onError = {
                  starting = false
                  backendError =
                    if (it.contains("TF_LITE_AUX", ignoreCase = true)) {
                      "NPU is not available for this model file. It was not compiled with the TF_LITE_AUX metadata required by LiteRT-LM. Choose GPU/CPU or install an NPU-compiled model."
                    } else if (it.contains("Input tensor not found", ignoreCase = true)) {
                      "This Qualcomm-compiled model requires NPU execution. The app selected NPU automatically; reselect the model and try again."
                    } else {
                      "Model load failed: $it"
                    }
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

      backendError?.let { message ->
        ElevatedCard(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        ) {
          Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              "NPU could not be activated",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
              message,
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onErrorContainer,
            )
          }
        }
      }

      ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text("Device performance", style = MaterialTheme.typography.titleMedium)
          val configuredContext =
            inferenceMetrics.contextLength.takeIf { it > 0 }
              ?: contextLengthText.toIntOrNull().orZero(4096)
          val usedTokens = inferenceMetrics.promptTokens
          val reservedTokens =
            inferenceMetrics.reservedOutputTokens.takeIf { it > 0 }
              ?: maximumResponseTokensText.toIntOrNull().orZero(2048)
          val remainingTokens =
            if (inferenceMetrics.contextLength > 0) inferenceMetrics.remainingTokens
            else (configuredContext - usedTokens - reservedTokens).coerceAtLeast(0)
          Text("Context usage", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
          Text(
            "${formatTokenCount(usedTokens)} used  •  ${formatTokenCount(remainingTokens)} remaining",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
          )
          LinearProgressIndicator(
            progress = { ((usedTokens + reservedTokens).toFloat() / configuredContext.coerceAtLeast(1)).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(10.dp),
          )
          Text(
            "${formatTokenCount(configuredContext)} total • ${formatTokenCount(reservedTokens)} reserved for response • estimated",
            style = MaterialTheme.typography.bodyMedium,
          )
          HorizontalDivider()
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
        Text("Server logs", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        OutlinedButton(onClick = { controller.logs.clear() }) {
          Text("Clear", style = MaterialTheme.typography.labelLarge)
        }
      }
      if (logEntries.isEmpty()) {
        Text("No server activity yet.", style = MaterialTheme.typography.bodyLarge)
      } else {
        logEntries.takeLast(30).forEach {
          Text(
            it,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(vertical = 4.dp),
          )
        }
      }
    }
  }
}

private fun Int?.orZero(defaultValue: Int): Int = this?.takeIf { it > 0 } ?: defaultValue

private fun formatTokenCount(value: Int): String =
  when {
    value >= 1024 -> String.format(Locale.US, "%.1fK", value / 1024.0)
    else -> value.toString()
  }
