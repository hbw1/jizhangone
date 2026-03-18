package com.bowe.localledger.data.nlp

import com.bowe.localledger.data.local.entity.TransactionType
import java.time.LocalDate
import java.util.Locale

data class NaturalLanguageParseRequest(
    val bookId: Long,
    val rawText: String,
    val today: LocalDate = LocalDate.now(),
    val memberNames: List<String> = emptyList(),
    val expenseCategoryNames: List<String> = emptyList(),
    val incomeCategoryNames: List<String> = emptyList(),
)

data class NaturalLanguageParseResult(
    val rawText: String,
    val diaryText: String,
    val candidates: List<ParsedTransactionCandidate>,
    val warnings: List<String> = emptyList(),
    val parseMode: ParseMode = ParseMode.LOCAL,
    val providerLabel: String? = null,
)

enum class ParseMode {
    LOCAL,
    CLOUD,
}

data class ParsedTransactionCandidate(
    val type: TransactionType,
    val amount: Double?,
    val occurredOn: LocalDate?,
    val memberName: String?,
    val categoryName: String?,
    val note: String,
    val confidence: Float,
    val sourceSnippet: String,
)

interface NaturalLanguageLedgerParser {
    fun parse(request: NaturalLanguageParseRequest): NaturalLanguageParseResult
}

class PlaceholderNaturalLanguageLedgerParser : NaturalLanguageLedgerParser {
    override fun parse(request: NaturalLanguageParseRequest): NaturalLanguageParseResult {
        val normalized = request.rawText.trim()
        if (normalized.isBlank()) {
            return NaturalLanguageParseResult(
                rawText = request.rawText,
                diaryText = "",
                candidates = emptyList(),
                warnings = listOf("请输入要解析的内容。"),
                parseMode = ParseMode.LOCAL,
            )
        }

        val snippets = extractSnippetContexts(normalized)

        val candidates = snippets.mapNotNull { snippet ->
            val amount = extractAmount(snippet.focusText) ?: extractAmount(snippet.contextText)
            if (amount == null) return@mapNotNull null

            val type = resolveType(snippet.focusText, snippet.contextText)
            val occurredOn = resolveDate(snippet.contextText, request.today)
            val memberName = resolveMember(snippet.contextText, request.memberNames)
            val categoryName = resolveCategory(
                focusText = snippet.focusText,
                contextText = snippet.contextText,
                type = type,
                expenseCategoryNames = request.expenseCategoryNames,
                incomeCategoryNames = request.incomeCategoryNames,
            )

            ParsedTransactionCandidate(
                type = type,
                amount = amount,
                occurredOn = occurredOn,
                memberName = memberName,
                categoryName = categoryName,
                note = buildNote(snippet.contextText, snippet.focusText),
                confidence = estimateConfidence(
                    amount = amount,
                    occurredOn = occurredOn,
                    memberName = memberName,
                    categoryName = categoryName,
                ),
                sourceSnippet = snippet.focusText,
            )
        }

        return NaturalLanguageParseResult(
            rawText = request.rawText,
            diaryText = normalized,
            candidates = candidates,
            warnings = if (candidates.isEmpty()) {
                listOf("当前没有识别出可入账的金额片段。")
            } else {
                emptyList()
            },
            parseMode = ParseMode.LOCAL,
        )
    }

    private fun extractSnippetContexts(text: String): List<SnippetContext> {
        val sentences = strongSplitRegex
            .split(text)
            .map { it.trim() }
            .filter { it.isNotBlank() }

        return sentences.flatMap { sentence ->
            val clauses = clauseSplitRegex
                .split(sentence)
                .map { it.trim() }
                .filter { it.isNotBlank() }
            val amountClauses = clauses.filter { hasAmount(it) }

            when {
                amountClauses.isEmpty() -> emptyList()
                amountClauses.size == 1 -> listOf(SnippetContext(contextText = sentence, focusText = amountClauses.first()))
                else -> amountClauses.map { clause ->
                    SnippetContext(contextText = sentence, focusText = clause)
                }
            }
        }
    }

    private fun resolveDate(snippet: String, today: LocalDate): LocalDate? {
        return when {
            todayKeywords.any { it in snippet } -> today
            yesterdayKeywords.any { it in snippet } -> today.minusDays(1)
            dayBeforeYesterdayKeywords.any { it in snippet } -> today.minusDays(2)
            else -> resolveAbsoluteDate(snippet, today)
        }
    }

    private fun resolveMember(snippet: String, members: List<String>): String? {
        val defaults = listOf("我", "妈妈", "爸爸")
        val candidates = (members + defaults).distinct()
            .filter { it.isNotBlank() }

        val scored = candidates.mapNotNull { member ->
            val score = scoreMemberMention(snippet, member)
            if (score <= 0) null else Triple(member, score, snippet.indexOf(member).takeIf { it >= 0 } ?: Int.MAX_VALUE)
        }

        return scored
            .sortedWith(compareByDescending<Triple<String, Int, Int>> { it.second }.thenBy { it.third })
            .firstOrNull()
            ?.first
    }

    private fun resolveCategory(
        focusText: String,
        contextText: String,
        type: TransactionType,
        expenseCategoryNames: List<String>,
        incomeCategoryNames: List<String>,
    ): String? {
        val categories = if (type == TransactionType.INCOME) incomeCategoryNames else expenseCategoryNames

        matchProvidedCategory(focusText, categories)?.let { return it }
        matchProvidedCategory(contextText, categories)?.let { return it }

        val rules = if (type == TransactionType.INCOME) incomeRules else expenseRules
        val loweredFocusText = focusText.lowercase(Locale.ROOT)
        val loweredContextText = contextText.lowercase(Locale.ROOT)
        val matchedRule = rules.firstOrNull { rule ->
            rule.keywords.any { keyword -> loweredFocusText.contains(keyword) }
        } ?: rules.firstOrNull { rule ->
            rule.keywords.any { keyword -> loweredContextText.contains(keyword) }
        }

        if (matchedRule != null) {
            return preferAvailableCategory(categories, matchedRule.categoryHints, matchedRule.fallbackCategory)
        }

        return null
    }

    private fun resolveType(focusText: String, contextText: String): TransactionType {
        return if (incomeKeywords.any { focusText.contains(it) || contextText.contains(it) }) {
            TransactionType.INCOME
        } else {
            TransactionType.EXPENSE
        }
    }

    private fun estimateConfidence(
        amount: Double?,
        occurredOn: LocalDate?,
        memberName: String?,
        categoryName: String?,
    ): Float {
        var score = 0.35f
        if (amount != null) score += 0.25f
        if (occurredOn != null) score += 0.15f
        if (memberName != null) score += 0.1f
        if (categoryName != null) score += 0.15f
        return score.coerceAtMost(0.95f)
    }

    private fun resolveAbsoluteDate(snippet: String, today: LocalDate): LocalDate? {
        fullDateRegex.find(snippet)?.let { match ->
            val year = match.groupValues[1].toIntOrNull() ?: return@let
            val month = match.groupValues[2].toIntOrNull() ?: return@let
            val day = match.groupValues[3].toIntOrNull() ?: return@let
            return runCatching { LocalDate.of(year, month, day) }.getOrNull()
        }

        chineseDateRegex.find(snippet)?.let { match ->
            val month = match.groupValues[1].toIntOrNull() ?: return@let
            val day = match.groupValues[2].toIntOrNull() ?: return@let
            return runCatching { LocalDate.of(today.year, month, day) }.getOrNull()
        }

        return null
    }

    private fun buildNote(contextText: String, focusText: String): String {
        val clauses = clauseSplitRegex
            .split(contextText)
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val focusIndex = clauses.indexOfFirst { it == focusText }.takeIf { it >= 0 }
        val fromContext = focusIndex?.let { index ->
            clauses
                .subList(0, index + 1)
                .asReversed()
                .firstOrNull { clause ->
                    !hasAmount(clause) && actionKeywords.any { keyword -> clause.contains(keyword) }
                }
        }

        val primary = fromContext ?: focusText
        return cleanNote(primary).ifBlank {
            cleanNote(focusText).ifBlank { focusText }
        }
    }

    private fun hasAmount(text: String): Boolean {
        return currencyAmountRegex.containsMatchIn(text) || contextualAmountRegex.containsMatchIn(text)
    }

    private fun extractAmount(text: String): Double? {
        return currencyAmountRegex.find(text)?.groupValues?.get(1)?.toDoubleOrNull()
            ?: contextualAmountRegex.find(text)?.groupValues?.get(2)?.toDoubleOrNull()
    }

    private fun scoreMemberMention(snippet: String, member: String): Int {
        val escapedMember = Regex.escape(member)
        val explicitPatterns = listOf(
            Regex("$escapedMember[^，。；\\s]{0,8}(请|付了|支付了|支付|花了|买了|买|交了|交|打车|充了|转给|还了|报销了|收了|收到|到账|领了|发了|发工资)"),
            Regex("(由|让)$escapedMember[^，。；\\s]{0,8}(支付|付款|付了|买了|报销)"),
        )
        if (explicitPatterns.any { it.containsMatchIn(snippet) }) return 3
        if (snippet.contains(member)) return 1
        return 0
    }

    private fun matchProvidedCategory(text: String, categories: List<String>): String? {
        val loweredText = text.lowercase(Locale.ROOT)
        return categories
            .sortedByDescending { it.length }
            .firstOrNull { category -> loweredText.contains(category.lowercase(Locale.ROOT)) }
    }

    private fun preferAvailableCategory(
        categories: List<String>,
        hints: List<String>,
        fallbackCategory: String,
    ): String? {
        val rankedHints = hints + fallbackCategory
        return rankedHints.firstNotNullOfOrNull { hint ->
            val normalizedHint = hint.lowercase(Locale.ROOT)
            categories.firstOrNull { category ->
                val normalizedCategory = category.lowercase(Locale.ROOT)
                normalizedCategory.contains(normalizedHint) || normalizedHint.contains(normalizedCategory)
            }
        } ?: fallbackCategory.takeIf { categories.isEmpty() || categories.any { it == fallbackCategory } }
    }

    private fun cleanNote(text: String): String {
        return text
            .replace(fullDateRegex, " ")
            .replace(chineseDateRegex, " ")
            .replace(relativeDateRegex, " ")
            .replace(currencyAmountRegex, " ")
            .replace(contextualAmountRegex, { match -> match.groupValues[1] })
            .replace(noiseRegex, " ")
            .replace(redundantVerbRegex, " ")
            .replace(spaceRegex, " ")
            .trim()
            .trim('，', '。', '；', ',', ';', ' ')
    }

    private data class SnippetContext(
        val contextText: String,
        val focusText: String,
    )

    private data class CategoryRule(
        val keywords: List<String>,
        val categoryHints: List<String>,
        val fallbackCategory: String,
    )

    companion object {
        private val currencyAmountRegex = Regex("(\\d+(?:\\.\\d{1,2})?)\\s*(元|块|rmb|人民币)")
        private val contextualAmountRegex = Regex("(花了|花费|付了|支付|买了|买|用了|收入|收到|收了|到账|报销|赚了|共)\\s*(\\d+(?:\\.\\d{1,2})?)")
        private val strongSplitRegex = Regex("[。！？!?\n]+")
        private val clauseSplitRegex = Regex("[，；;,]+")
        private val fullDateRegex = Regex("(20\\d{2})[-/.](\\d{1,2})[-/.](\\d{1,2})")
        private val chineseDateRegex = Regex("(\\d{1,2})月(\\d{1,2})日")
        private val relativeDateRegex = Regex("(今天|今晚|今日|刚刚|昨天|昨晚|昨日|前天)")
        private val noiseRegex = Regex("\\s+")
        private val spaceRegex = Regex("\\s+")
        private val redundantVerbRegex = Regex("(花了|付了|支付了|支付|消费了|用了|共|总共)\\s*$")
        private val incomeKeywords = listOf("收入", "工资", "奖金", "报销", "收了", "到账")
        private val actionKeywords = listOf("请", "买", "吃", "打车", "发工资", "发薪", "工资", "奖金", "报销", "收", "到账", "充", "交")
        private val todayKeywords = listOf("今天", "今晚", "今日", "刚刚")
        private val yesterdayKeywords = listOf("昨天", "昨晚", "昨日")
        private val dayBeforeYesterdayKeywords = listOf("前天")
        private val expenseRules = listOf(
            CategoryRule(
                keywords = listOf("火锅", "吃饭", "午饭", "晚饭", "早餐", "宵夜", "奶茶", "咖啡", "餐厅", "外卖"),
                categoryHints = listOf("餐饮", "吃喝"),
                fallbackCategory = "餐饮",
            ),
            CategoryRule(
                keywords = listOf("买菜", "买了菜", "菜市场", "蔬菜", "水果", "食材", "超市"),
                categoryHints = listOf("买菜", "生鲜", "购物", "居家"),
                fallbackCategory = "购物",
            ),
            CategoryRule(
                keywords = listOf("打车", "出租车", "地铁", "公交", "高铁", "停车", "加油"),
                categoryHints = listOf("交通", "出行"),
                fallbackCategory = "交通",
            ),
            CategoryRule(
                keywords = listOf("房租", "水电", "电费", "网费", "物业"),
                categoryHints = listOf("居家", "房租"),
                fallbackCategory = "居家",
            ),
        )
        private val incomeRules = listOf(
            CategoryRule(
                keywords = listOf("工资", "薪水", "发工资", "发薪"),
                categoryHints = listOf("工资"),
                fallbackCategory = "工资",
            ),
            CategoryRule(
                keywords = listOf("奖金", "绩效", "年终"),
                categoryHints = listOf("奖金"),
                fallbackCategory = "奖金",
            ),
            CategoryRule(
                keywords = listOf("报销"),
                categoryHints = listOf("报销"),
                fallbackCategory = "报销",
            ),
        )
    }
}
