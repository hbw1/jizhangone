package com.bowe.localledger.data.nlp

import com.bowe.localledger.data.local.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class NaturalLanguageLedgerParserTest {

    private val parser = PlaceholderNaturalLanguageLedgerParser()

    @Test
    fun parsesUserExampleIntoTwoExpenseCandidates() {
        val today = LocalDate.of(2026, 3, 15)
        val result = parser.parse(
            NaturalLanguageParseRequest(
                bookId = 1L,
                rawText = "今天和妈妈一起去吃饭，我请妈妈吃了火锅，火锅花了300元。昨天爸爸买了菜，花了86元。",
                today = today,
                memberNames = listOf("妈妈", "爸爸", "我"),
                expenseCategoryNames = listOf("餐饮", "购物", "交通"),
                incomeCategoryNames = listOf("工资", "奖金", "报销"),
            ),
        )

        assertTrue(result.warnings.isEmpty())
        assertEquals(2, result.candidates.size)

        val first = result.candidates[0]
        assertEquals(TransactionType.EXPENSE, first.type)
        assertEquals(300.0, first.amount ?: 0.0, 0.001)
        assertEquals(today, first.occurredOn)
        assertEquals("我", first.memberName)
        assertEquals("餐饮", first.categoryName)
        assertEquals("我请妈妈吃了火锅", first.note)
        assertNotNull(first.note)

        val second = result.candidates[1]
        assertEquals(TransactionType.EXPENSE, second.type)
        assertEquals(86.0, second.amount ?: 0.0, 0.001)
        assertEquals(today.minusDays(1), second.occurredOn)
        assertEquals("爸爸", second.memberName)
        assertEquals("购物", second.categoryName)
        assertEquals("爸爸买了菜", second.note)
    }

    @Test
    fun splitsOneSentenceIntoMultipleCandidatesWhenThereAreMultipleAmounts() {
        val today = LocalDate.of(2026, 3, 15)
        val result = parser.parse(
            NaturalLanguageParseRequest(
                bookId = 1L,
                rawText = "今天早餐18元，晚上打车30元。",
                today = today,
                memberNames = listOf("我", "妈妈", "爸爸"),
                expenseCategoryNames = listOf("餐饮", "购物", "交通"),
                incomeCategoryNames = listOf("工资", "奖金", "报销"),
            ),
        )

        assertEquals(2, result.candidates.size)

        val breakfast = result.candidates[0]
        assertEquals(18.0, breakfast.amount ?: 0.0, 0.001)
        assertEquals(today, breakfast.occurredOn)
        assertEquals("餐饮", breakfast.categoryName)
        assertNull(breakfast.memberName)

        val taxi = result.candidates[1]
        assertEquals(30.0, taxi.amount ?: 0.0, 0.001)
        assertEquals(today, taxi.occurredOn)
        assertEquals("交通", taxi.categoryName)
        assertEquals("晚上打车", taxi.note)
    }

    @Test
    fun parsesChineseDateAndIncomeCategory() {
        val today = LocalDate.of(2026, 3, 15)
        val result = parser.parse(
            NaturalLanguageParseRequest(
                bookId = 1L,
                rawText = "3月14日爸爸发工资5000元。",
                today = today,
                memberNames = listOf("我", "妈妈", "爸爸"),
                expenseCategoryNames = listOf("餐饮", "购物", "交通"),
                incomeCategoryNames = listOf("工资", "奖金", "报销"),
            ),
        )

        assertEquals(1, result.candidates.size)
        val candidate = result.candidates.first()
        assertEquals(TransactionType.INCOME, candidate.type)
        assertEquals(5000.0, candidate.amount ?: 0.0, 0.001)
        assertEquals(LocalDate.of(2026, 3, 14), candidate.occurredOn)
        assertEquals("爸爸", candidate.memberName)
        assertEquals("工资", candidate.categoryName)
        assertEquals("爸爸发工资", candidate.note)
    }
}
