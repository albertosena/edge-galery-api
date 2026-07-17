package com.google.ai.edge.gallery.context

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class IncrementalSummarizationCoordinatorTest {
  private class Repo(var value: ConversationSummary? = null) : ConversationSummaryRepository {
    override fun get(conversationId: String) = value
    override fun save(conversationId: String, summary: ConversationSummary) { value = summary }
    override fun clear(conversationId: String) { value = null }
  }

  @Test fun incrementalSummaryIncludesPreviousAndCommits() = runBlocking {
    val repo = Repo(ConversationSummary("Previous fact", 2, 1))
    var prompt = ""
    val result = IncrementalSummarizationCoordinator(repo) { prompt = it; "Consolidated" }.summarize("c", listOf("new fact"), 2)
    assertTrue(result is SummarizationResult.Success)
    assertTrue(prompt.contains("Previous fact"))
    assertEquals(3, repo.value!!.summarizedMessageCount)
  }

  @Test fun failurePreservesPreviousSummary() = runBlocking {
    val original = ConversationSummary("Keep me", 2, 1)
    val repo = Repo(original)
    val result = IncrementalSummarizationCoordinator(repo) { error("inference failed") }.summarize("c", listOf("x"))
    assertTrue(result is SummarizationResult.Failure)
    assertEquals(original, repo.value)
  }
}
