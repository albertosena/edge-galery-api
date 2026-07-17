package com.google.ai.edge.gallery

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QualcommNpuSmokeTest {
  @Test
  fun initializesSm8650ModelAndGeneratesText() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val model =
      File(
        context.getExternalFilesDir(null),
        "__imports/gemma3-270m-it-q8.qualcomm.sm8650.litertlm",
      )
    assertTrue("Imported SM8650 model was not found at ${model.absolutePath}", model.isFile)

    Engine(
        EngineConfig(
          modelPath = model.absolutePath,
          backend = Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir),
          maxNumTokens = 4096,
        )
      )
      .use { engine ->
        engine.initialize()
        engine.createConversation().use { conversation ->
          val response = conversation.sendMessage("Reply with only: OK")
          assertTrue("The NPU model returned an empty response", response.toString().isNotBlank())
        }
      }
  }
}
