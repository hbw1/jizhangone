package com.bowe.localledger.data

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class ReportPeriodPreset(val label: String) {
    THIS_MONTH("本月"),
    LAST_MONTH("上月"),
    LAST_30_DAYS("近30天"),
    THIS_YEAR("本年"),
    CUSTOM("自定义"),
}

data class ReportPeriodState(
    val preset: ReportPeriodPreset = ReportPeriodPreset.THIS_MONTH,
    val customStart: LocalDate? = null,
    val customEnd: LocalDate? = null,
) {
    fun resolvedRange(today: LocalDate = LocalDate.now()): ReportDateRange {
        return when (preset) {
            ReportPeriodPreset.THIS_MONTH -> ReportDateRange(
                startDate = today.withDayOfMonth(1),
                endDateInclusive = today,
            )

            ReportPeriodPreset.LAST_MONTH -> {
                val lastMonth = today.minusMonths(1)
                ReportDateRange(
                    startDate = lastMonth.withDayOfMonth(1),
                    endDateInclusive = lastMonth.withDayOfMonth(lastMonth.lengthOfMonth()),
                )
            }

            ReportPeriodPreset.LAST_30_DAYS -> ReportDateRange(
                startDate = today.minusDays(29),
                endDateInclusive = today,
            )

            ReportPeriodPreset.THIS_YEAR -> ReportDateRange(
                startDate = today.withDayOfYear(1),
                endDateInclusive = today,
            )

            ReportPeriodPreset.CUSTOM -> {
                val start = customStart ?: today.withDayOfMonth(1)
                val end = customEnd ?: today
                if (end.isBefore(start)) {
                    ReportDateRange(startDate = end, endDateInclusive = start)
                } else {
                    ReportDateRange(startDate = start, endDateInclusive = end)
                }
            }
        }
    }
}

data class ReportDateRange(
    val startDate: LocalDate,
    val endDateInclusive: LocalDate,
) {
    fun toEpochRange(zoneId: ZoneId = ZoneId.systemDefault()): EpochRange {
        val startInclusive = startDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endExclusive = endDateInclusive.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return EpochRange(startInclusive = startInclusive, endExclusive = endExclusive)
    }

    fun format(formatter: DateTimeFormatter): String {
        return "${startDate.format(formatter)} - ${endDateInclusive.format(formatter)}"
    }
}

data class EpochRange(
    val startInclusive: Long,
    val endExclusive: Long,
)
