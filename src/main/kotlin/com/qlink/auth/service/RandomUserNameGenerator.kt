package com.qlink.auth.service

import java.util.UUID
import kotlin.random.Random

data class RandomUserName(
    val username: String,
    val nickname: String,
)

class RandomUserNameGenerator {
    private val adjectives =
        listOf(
            "화난",
            "행복한",
            "슬픈",
            "깜짝놀란",
            "걱정하는",
            "자랑스러운",
            "긴장한",
            "무서워하는",
            "외로운",
            "괴로운",
            "짜증난",
            "불굴의",
            "결단력있는",
            "쾌활한",
            "즐거운",
            "기쁜",
        )
    private val nouns =
        listOf(
            "사자",
            "수달",
            "돼지",
            "강아지",
            "고양이",
            "거북이",
            "호랑이",
            "오소리",
            "두더지",
            "송아지",
            "에뮤",
            "딩고",
            "코알라",
            "페럿",
            "앵무새",
        )

    fun generate(): RandomUserName {
        val uuid = UUID.randomUUID().toString().replace("-", "")
        val adjective = adjectives.random()
        val noun = nouns.random()

        return RandomUserName(
            username = uuid,
            nickname = "$adjective$noun${uuid.takeLast(6)}",
        )
    }

    private fun <T> List<T>.random(): T = this[Random.nextInt(size)]
}
