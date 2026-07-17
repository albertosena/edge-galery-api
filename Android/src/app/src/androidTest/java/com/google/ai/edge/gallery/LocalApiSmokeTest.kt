package com.google.ai.edge.gallery

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalApiSmokeTest {
  @Test
  fun startsServerWithSelectedNpuModel() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    val device = UiDevice.getInstance(instrumentation)
    device.executeShellCommand(
      "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS"
    )

    instrumentation.startActivitySync(
      Intent(context, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
      }
    )
    if (device.wait(Until.hasObject(By.text("Accept & continue")), 10_000)) {
      clickClickableText(device, "Accept & continue")
    }
    assertTrue(device.wait(Until.hasObject(By.text("Local API Server")), 30_000))
    clickClickableText(device, "Local API Server")
    assertTrue(device.wait(Until.hasObject(By.text("Start Server")), 15_000))
    assertTrue(
      "Imported model did not finish loading into the model list",
      device.wait(Until.hasObject(By.text("Select")), 120_000),
    )

    clickClickableText(device, "Select", last = true)
    assertTrue(device.wait(Until.hasObject(By.text("Selected")), 10_000))
    clickClickableText(device, "Start Server")
    assertTrue("Server did not finish loading the NPU model", device.wait(Until.hasObject(By.text("Stop Server")), 60_000))

    val health = get("http://127.0.0.1:8080/health")
    assertTrue("Health endpoint did not publish the loaded model: $health", health.contains("\"model_loaded\":true"))
    val models = get("http://127.0.0.1:8080/v1/models")
    assertTrue("Models endpoint did not expose Gemma: $models", models.contains("gemma3-270m"))
  }

  private fun get(url: String): String {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.connectTimeout = 5_000
    connection.readTimeout = 5_000
    return connection.inputStream.bufferedReader().use { it.readText() }
  }

  private fun clickClickableText(device: UiDevice, text: String, last: Boolean = false) {
    var node = if (last) device.findObjects(By.text(text)).last() else device.findObject(By.text(text))
    val original = node
    while (!node.isClickable) {
      node = node.parent ?: run {
        device.click(original.visibleCenter.x, original.visibleCenter.y)
        return
      }
    }
    node.click()
  }
}
