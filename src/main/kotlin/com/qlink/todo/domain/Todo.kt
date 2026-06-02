package com.qlink.todo.domain

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireFalse
import com.qlink.common.error.requireNotOver
import com.qlink.common.error.requireTrue
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

private const val MAX_TITLE_LENGTH = 50
private const val DEFAULT_REPEAT_TIMEZONE = "UTC"
private val REPEAT_TIME_REGEX = Regex("""\d{2}:\d{2}""")

class Todo(
    val id: Long? = null,
    val linkId: Long,
    val ownerId: Long,
    val title: String,
    val reminderAt: Instant? = null,
    val repeatUntil: Instant? = null,
    val repeatDays: List<RepeatDay>? = null,
    val repeatTime: LocalTime? = null,
    repeatTimezone: String? = null,
    val completedAt: Instant? = null,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
) {
    val repeatTimezone: String? =
        if (repeatUntil != null && repeatDays != null && repeatTime != null) {
            repeatTimezone ?: DEFAULT_REPEAT_TIMEZONE
        } else {
            repeatTimezone
        }

    init {
        validateTitle(title)
        validateRepeat()
    }

    val isCompleted: Boolean
        get() = completedAt != null

    fun validateOwner(ownerId: Long) {
        if (this.ownerId != ownerId) {
            throw BusinessException(ErrorCode.TODO_DIFFERENT_OWNER)
        }
    }

    fun isDifferentLink(linkId: Long): Boolean = this.linkId != linkId

    fun update(
        linkId: Long,
        title: String,
        reminderAt: Instant?,
        repeatUntil: Instant?,
        repeatDays: List<RepeatDay>?,
        repeatTime: LocalTime?,
        repeatTimezone: String?,
    ): Todo =
        Todo(
            id = this.id,
            linkId = linkId,
            ownerId = this.ownerId,
            title = title,
            reminderAt = reminderAt,
            repeatUntil = repeatUntil,
            repeatDays = repeatDays,
            repeatTime = repeatTime,
            repeatTimezone = repeatTimezone,
            completedAt = this.completedAt,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
        )

    fun setNextReminder(now: Instant): Todo {
        if (!hasRepeat) {
            return this
        }

        if (repeatUntil!! < now) {
            return copyWith(reminderAt = null)
        }

        val zoneId = ZoneId.of(repeatTimezone!!)
        val localNow = now.toJavaInstant().atZone(zoneId)
        val nextReminder =
            repeatDays!!
                .map { repeatDay ->
                    val daysUntil = (repeatDay.dayOfWeek.value - localNow.dayOfWeek.value + 7) % 7
                    val candidateDate = localNow.toLocalDate().plusDays(daysUntil.toLong())
                    val candidate = candidateDate.atTime(repeatTime!!).atZone(zoneId)

                    if (candidate.toInstant().isAfter(now.toJavaInstant())) {
                        candidate
                    } else {
                        candidate.plusWeeks(1)
                    }
                }.minBy { it.toInstant() }
                .toInstant()
                .toKotlinInstant()

        return copyWith(
            reminderAt = nextReminder.takeIf { it <= repeatUntil },
        )
    }

    fun complete(completedAt: Instant): Todo =
        if (isCompleted) {
            this
        } else {
            Todo(
                id = id,
                linkId = linkId,
                ownerId = ownerId,
                title = title,
                reminderAt = reminderAt,
                repeatUntil = repeatUntil,
                repeatDays = repeatDays,
                repeatTime = repeatTime,
                repeatTimezone = repeatTimezone,
                completedAt = completedAt,
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
        }

    fun incomplete(): Todo =
        if (!isCompleted) {
            this
        } else {
            Todo(
                id = id,
                linkId = linkId,
                ownerId = ownerId,
                title = title,
                reminderAt = reminderAt,
                repeatUntil = repeatUntil,
                repeatDays = repeatDays,
                repeatTime = repeatTime,
                repeatTimezone = repeatTimezone,
                completedAt = null,
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
        }

    val hasRepeat: Boolean
        get() = repeatUntil != null && repeatDays != null && repeatTime != null

    private fun validateTitle(title: String) {
        title.isNotBlank().requireTrue(ErrorCode.TODO_TITLE_BLANK)
        title.requireNotOver(MAX_TITLE_LENGTH, ErrorCode.TODO_TITLE_OVER_MAX)
    }

    private fun validateRepeat() {
        val repeatFieldCount = listOf(repeatUntil, repeatDays, repeatTime).count { it != null }
        (repeatFieldCount == 0 || repeatFieldCount == 3).requireTrue(ErrorCode.TODO_REPEAT_FIELDS_INCOMPLETE)

        repeatDays?.isEmpty()?.requireFalse(ErrorCode.TODO_REPEAT_DAYS_EMPTY)
        repeatTimezone?.let { validateTimezone(it) }
    }

    private fun copyWith(reminderAt: Instant?): Todo =
        Todo(
            id = id,
            linkId = linkId,
            ownerId = ownerId,
            title = title,
            reminderAt = reminderAt,
            repeatUntil = repeatUntil,
            repeatDays = repeatDays,
            repeatTime = repeatTime,
            repeatTimezone = repeatTimezone,
            completedAt = completedAt,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    companion object {
        fun parseRepeatTime(repeatTime: String?): LocalTime? {
            if (repeatTime == null) {
                return null
            }

            if (!REPEAT_TIME_REGEX.matches(repeatTime)) {
                throw BusinessException(ErrorCode.TODO_REPEAT_TIME_INVALID)
            }

            return try {
                LocalTime.parse(repeatTime)
            } catch (_: DateTimeParseException) {
                throw BusinessException(ErrorCode.TODO_REPEAT_TIME_INVALID)
            }
        }

        fun normalizeRepeatTimezone(
            repeatUntil: Instant?,
            repeatDays: List<RepeatDay>?,
            repeatTime: LocalTime?,
            repeatTimezone: String?,
        ): String? {
            val hasRepeat = repeatUntil != null && repeatDays != null && repeatTime != null
            if (!hasRepeat) {
                return repeatTimezone
            }

            val normalizedTimezone = repeatTimezone ?: DEFAULT_REPEAT_TIMEZONE
            validateTimezone(normalizedTimezone)
            return normalizedTimezone
        }

        private fun validateTimezone(repeatTimezone: String) {
            try {
                ZoneId.of(repeatTimezone)
            } catch (_: RuntimeException) {
                throw BusinessException(ErrorCode.TODO_REPEAT_TIMEZONE_INVALID)
            }
        }
    }
}
