package com.google.ai.edge.gallery.context

import android.content.Context
import com.google.ai.edge.gallery.data.ConfigKeys
import com.google.ai.edge.gallery.data.Model
import kotlin.math.ceil
import java.util.concurrent.atomic.AtomicBoolean

data class ModelGenerationSettings(
  val contextLength: Int = 4096,
  val maximumResponseTokens: Int = 2048,
  val automaticallySummarize: Boolean = true,
  val summarizationThresholdPercent: Int = 75,
  val recentMessagesToPreserve: Int = 8,
  val modelMaximumContext: Int = 32768,
) {
  fun validated(): ModelGenerationSettings {
    require(contextLength in 2048..minOf(32768, modelMaximumContext))
    require(maximumResponseTokens in 1 until contextLength)
    require(summarizationThresholdPercent in 65..85)
    require(recentMessagesToPreserve in 4..12)
    return this
  }
}

data class TokenCount(val tokens: Int, val estimated: Boolean)

fun interface TokenCounter {
  fun count(text: String): TokenCount
}

/** Conservative fallback because LiteRT-LM 0.14.0 does not expose its tokenizer here. */
class ConservativeTokenCounter : TokenCounter {
  override fun count(text: String): TokenCount =
    TokenCount(tokens = if (text.isEmpty()) 0 else ceil(text.length / 3.0).toInt() + 4, estimated = true)
}

data class ContextBudget(
  val contextLength: Int,
  val promptTokens: Int,
  val reservedOutputTokens: Int,
  val availablePromptTokens: Int,
  val projectedTokens: Int,
  val usagePercent: Int,
  val shouldSummarize: Boolean,
  val fits: Boolean,
  val estimated: Boolean,
)

class ContextBudgetCalculator(private val tokenCounter: TokenCounter) {
  fun calculate(systemPrompt: String, history: List<String>, currentMessage: String, settings: ModelGenerationSettings): ContextBudget {
    settings.validated()
    val counts = (listOf(systemPrompt) + history + currentMessage).map(tokenCounter::count)
    val prompt = counts.sumOf { it.tokens }
    val projected = prompt + settings.maximumResponseTokens
    val available = settings.contextLength - settings.maximumResponseTokens
    val percent = ((projected.toDouble() / settings.contextLength) * 100).toInt().coerceAtLeast(0)
    return ContextBudget(settings.contextLength, prompt, settings.maximumResponseTokens, available, projected, percent,
      settings.automaticallySummarize && percent >= settings.summarizationThresholdPercent,
      prompt <= available, counts.any { it.estimated })
  }
}

data class ConversationSummary(val content: String, val summarizedMessageCount: Int, val updatedAtMs: Long)

interface ConversationSummaryRepository {
  fun get(conversationId: String): ConversationSummary?
  fun save(conversationId: String, summary: ConversationSummary)
  fun clear(conversationId: String)
}

fun interface ConversationSummarizer {
  suspend fun summarize(instruction: String): String
}

sealed interface SummarizationResult {
  data class Success(val summary: ConversationSummary) : SummarizationResult
  data object AlreadyRunning : SummarizationResult
  data class Failure(val cause: Throwable) : SummarizationResult
}

/** Generates first, then commits: failures and cancellation leave the previous summary intact. */
class IncrementalSummarizationCoordinator(
  private val repository: ConversationSummaryRepository,
  private val summarizer: ConversationSummarizer,
) {
  private val isSummarizing = AtomicBoolean(false)

  suspend fun summarize(conversationId: String, oldMessages: List<String>, nowMs: Long = System.currentTimeMillis()): SummarizationResult {
    if (!isSummarizing.compareAndSet(false, true)) return SummarizationResult.AlreadyRunning
    return try {
      val previous = repository.get(conversationId)
      val prompt = buildString {
        appendLine("Summarize the older conversation history for continued use by another assistant response. Preserve decisions, requirements, technical identifiers, relevant code details, errors, attempted solutions and pending tasks. Do not add new information.")
        appendLine("Use sections: Current goal; User preferences; Decisions; Technical context; Completed work; Errors and attempted solutions; Pending tasks.")
        previous?.let { appendLine("Previous consolidated summary:\n${it.content}") }
        appendLine("New older messages:\n${oldMessages.joinToString("\n")}")
      }
      val content = summarizer.summarize(prompt).trim()
      require(content.isNotEmpty()) { "The summarizer returned an empty summary" }
      val summary = ConversationSummary(content, (previous?.summarizedMessageCount ?: 0) + oldMessages.size, nowMs)
      repository.save(conversationId, summary)
      SummarizationResult.Success(summary)
    } catch (error: Throwable) {
      SummarizationResult.Failure(error)
    } finally {
      isSummarizing.set(false)
    }
  }
}

data class ContextBuildResult(
  val summary: ConversationSummary?,
  val recentMessages: List<String>,
  val currentMessage: String,
  val budget: ContextBudget,
)

class ConversationContextBuilder(private val calculator: ContextBudgetCalculator) {
  fun build(systemPrompt: String, history: List<String>, currentMessage: String, summary: ConversationSummary?, settings: ModelGenerationSettings): ContextBuildResult {
    var recent = history.takeLast(settings.recentMessagesToPreserve)
    var budget = calculator.calculate(systemPrompt, listOfNotNull(summary?.content) + recent, currentMessage, settings)
    while (!budget.fits && recent.isNotEmpty()) {
      recent = recent.drop(1)
      budget = calculator.calculate(systemPrompt, listOfNotNull(summary?.content) + recent, currentMessage, settings)
    }
    return ContextBuildResult(summary, recent, currentMessage, budget)
  }
}

/** Per-model preferences; apply is a single SharedPreferences transaction. */
object ModelGenerationSettingsStore {
  private const val PREFS = "model_generation_settings_v1"
  private fun key(model: Model, suffix: String) = "${model.normalizedName}.$suffix"

  fun load(context: Context, model: Model) {
    val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val values = model.configValues.toMutableMap()
    values[ConfigKeys.CONTEXT_LENGTH.label] = p.getInt(key(model, "context"), model.getIntConfigValue(ConfigKeys.CONTEXT_LENGTH, 4096))
    values[ConfigKeys.MAX_OUTPUT_TOKENS.label] = p.getInt(key(model, "output"), model.getIntConfigValue(ConfigKeys.MAX_OUTPUT_TOKENS, 2048))
    values[ConfigKeys.AUTO_SUMMARIZE.label] = p.getBoolean(key(model, "summarize"), model.getBooleanConfigValue(ConfigKeys.AUTO_SUMMARIZE, true))
    values[ConfigKeys.SUMMARIZATION_THRESHOLD.label] = p.getInt(key(model, "threshold"), model.getIntConfigValue(ConfigKeys.SUMMARIZATION_THRESHOLD, 75))
    values[ConfigKeys.RECENT_MESSAGES_TO_PRESERVE.label] = p.getInt(key(model, "recent"), model.getIntConfigValue(ConfigKeys.RECENT_MESSAGES_TO_PRESERVE, 8))
    model.configValues = values
  }

  fun save(context: Context, model: Model) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
      .putInt(key(model, "context"), model.getIntConfigValue(ConfigKeys.CONTEXT_LENGTH, 4096))
      .putInt(key(model, "output"), model.getIntConfigValue(ConfigKeys.MAX_OUTPUT_TOKENS, 2048))
      .putBoolean(key(model, "summarize"), model.getBooleanConfigValue(ConfigKeys.AUTO_SUMMARIZE, true))
      .putInt(key(model, "threshold"), model.getIntConfigValue(ConfigKeys.SUMMARIZATION_THRESHOLD, 75))
      .putInt(key(model, "recent"), model.getIntConfigValue(ConfigKeys.RECENT_MESSAGES_TO_PRESERVE, 8))
      .apply()
  }
}
