package com.qlink.support.fixture

import net.datafaker.Faker
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Locale
import java.util.concurrent.TimeUnit

object RandomFixture {
    private val faker = Faker(Locale.KOREA)

    fun randomId(): Long = randomLong(1L, Long.MAX_VALUE)

    fun randomSentenceWithMax(maxLength: Int): String =
        faker
            .lorem()
            .maxLengthSentence(maxLength)

    fun randomSentence(
        minLength: Int,
        maxLength: Int,
    ): String =
        faker
            .lorem()
            .characters(minLength, maxLength)

    fun randomFixedSentence(length: Int): String =
        faker
            .lorem()
            .characters(length)

    fun randomInt(
        min: Int,
        max: Int,
    ): Int =
        faker
            .random()
            .nextInt(min, max)

    fun randomPositive(): Int =
        faker
            .number()
            .positive()

    fun randomNegative(): Int =
        faker
            .number()
            .negative()

    fun randomLong(
        min: Long,
        max: Long,
    ): Long =
        faker
            .random()
            .nextLong(min, max)

    fun randomColor(): String =
        faker
            .color()
            .hex()
            .substring(1)

    fun futureDate(futureDaysScope: Int): LocalDate {
        val tomorrow = OffsetDateTime.now(ZoneOffset.UTC).plusDays(1)
        val until = OffsetDateTime.now(ZoneOffset.UTC).plusDays(futureDaysScope.toLong())

        return faker
            .timeAndDate()
            .between(tomorrow.toInstant(), until.toInstant())
            .toOffsetDateTime()
            .toLocalDate()
    }

    fun pastDate(pastDaysScope: Int): LocalDate {
        val yesterday = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1)
        val until = OffsetDateTime.now(ZoneOffset.UTC).minusDays(pastDaysScope.toLong())

        return faker
            .timeAndDate()
            .between(until.toInstant(), yesterday.toInstant())
            .toOffsetDateTime()
            .toLocalDate()
    }

    fun randomDateTime(): OffsetDateTime =
        faker
            .timeAndDate()
            .birthday()
            .atStartOfDay()
            .atOffset(ZoneOffset.UTC)

    fun pastDateTime(
        pastScope: Int,
        timeUnit: TimeUnit,
    ): OffsetDateTime =
        faker
            .timeAndDate()
            .past(pastScope.toLong(), timeUnit)
            .toOffsetDateTime()

    fun futureDateTime(
        futureScope: Int,
        timeUnit: TimeUnit,
    ): OffsetDateTime =
        faker
            .timeAndDate()
            .future(futureScope.toLong(), timeUnit)
            .toOffsetDateTime()

    fun randomDateTimeBetween(
        from: OffsetDateTime,
        to: OffsetDateTime,
    ): OffsetDateTime =
        faker
            .timeAndDate()
            .between(from.toInstant(), to.toInstant())
            .toOffsetDateTime()

    fun randomDateTimeInScopeFromNow(dayScope: Int): OffsetDateTime {
        val now = OffsetDateTime.now(ZoneOffset.UTC)

        return faker
            .timeAndDate()
            .between(
                now.minusDays(dayScope.toLong()).toInstant(),
                now.plusDays(dayScope.toLong()).toInstant(),
            ).toOffsetDateTime()
    }

    fun randomTime(): LocalTime {
        val randomNanoTime =
            faker
                .time()
                .between(LocalTime.MIN, LocalTime.MAX)

        return LocalTime.ofNanoOfDay(randomNanoTime)
    }

    fun futureTime(): LocalTime {
        val futureNano =
            faker
                .time()
                .between(LocalTime.now(), LocalTime.MAX)

        return LocalTime.ofNanoOfDay(futureNano)
    }

    fun pastTime(): LocalTime {
        val pastNano =
            faker
                .time()
                .between(LocalTime.MIN, LocalTime.now())

        return LocalTime.ofNanoOfDay(pastNano)
    }

    fun randomAm(): LocalTime {
        val amNano =
            faker
                .time()
                .between(LocalTime.MIN, LocalTime.NOON)

        return LocalTime.ofNanoOfDay(amNano)
    }

    fun randomPm(): LocalTime {
        val pmNano =
            faker
                .time()
                .between(LocalTime.NOON, LocalTime.MAX)

        return LocalTime.ofNanoOfDay(pmNano)
    }

    fun futureTimeFrom(from: LocalTime): LocalTime {
        val futureNano =
            faker
                .time()
                .between(from, LocalTime.MAX)

        return LocalTime.ofNanoOfDay(futureNano)
    }

    fun pastTimeFrom(from: LocalTime): LocalTime {
        val pastNano =
            faker
                .time()
                .between(LocalTime.MIN, from)

        return LocalTime.ofNanoOfDay(pastNano)
    }

    fun randomUrl(): String = faker.internet().url()

    fun randomEmoji(): String = faker.emoji().smiley()

    fun randomSentenceList(
        maxSize: Int? = null,
        maxSentenceLength: String? = null,
    ): List<String> {
        val size = maxSize ?: faker.number().numberBetween(0, 100)
        val maxLength = maxSentenceLength?.length ?: faker.number().numberBetween(0, 100)

        return List(size) { randomSentenceWithMax(maxLength) }
    }

    private fun java.time.Instant.toOffsetDateTime(): OffsetDateTime = atOffset(ZoneOffset.UTC)
}
