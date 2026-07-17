package com.google.ai.edge.gallery.context

import org.junit.Assert.*
import org.junit.Test

class ConversationContextTest {
  private val exact = TokenCounter { TokenCount(it.split(' ').filter(String::isNotBlank).size, false) }

  @Test fun reservesOutputBeforePrompt() {
    val result = ContextBudgetCalculator(exact).calculate("system", List(10) { "one two" }, "now", ModelGenerationSettings(contextLength = 2048, maximumResponseTokens = 1024))
    assertEquals(1024, result.availablePromptTokens)
    assertEquals(1024, result.reservedOutputTokens)
  }

  @Test fun triggersAtConfiguredThreshold() {
    val counter = TokenCounter { TokenCount(750, false) }
    val result = ContextBudgetCalculator(counter).calculate("s", emptyList(), "u", ModelGenerationSettings(contextLength = 2048, maximumResponseTokens = 512, summarizationThresholdPercent = 65))
    assertTrue(result.shouldSummarize)
  }

  @Test fun preservesCurrentAndRecentMessages() {
    val settings = ModelGenerationSettings(contextLength = 2048, maximumResponseTokens = 512, recentMessagesToPreserve = 4)
    val result = ConversationContextBuilder(ContextBudgetCalculator(exact)).build("system", List(10) { "message $it" }, "current", null, settings)
    assertEquals("current", result.currentMessage)
    assertEquals(listOf("message 6", "message 7", "message 8", "message 9"), result.recentMessages)
  }

  @Test fun rejectsModelLimitAndInvalidValues() {
    assertThrows(IllegalArgumentException::class.java) { ModelGenerationSettings(contextLength = 32768, modelMaximumContext = 16384).validated() }
  }

  @Test fun fallbackIsMarkedEstimated() {
    assertTrue(ConservativeTokenCounter().count("hello").estimated)
  }

  @Test fun smallConversationNeedsNoSummary() {
    val result = ContextBudgetCalculator(exact).calculate("system", emptyList(), "hello", ModelGenerationSettings(contextLength = 4096, maximumResponseTokens = 512))
    assertFalse(result.shouldSummarize)
    assertTrue(result.fits)
  }
}
