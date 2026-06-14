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
            NamePart(korean = "화난", english = "angry"),
            NamePart(korean = "행복한", english = "happy"),
            NamePart(korean = "슬픈", english = "sad"),
            NamePart(korean = "깜짝놀란", english = "startled"),
            NamePart(korean = "걱정하는", english = "worried"),
            NamePart(korean = "자랑스러운", english = "proud"),
            NamePart(korean = "긴장한", english = "nervous"),
            NamePart(korean = "무서워하는", english = "scared"),
            NamePart(korean = "외로운", english = "lonely"),
            NamePart(korean = "괴로운", english = "painful"),
            NamePart(korean = "짜증난", english = "annoyed"),
            NamePart(korean = "불굴의", english = "undaunted"),
            NamePart(korean = "결단력있는", english = "determined"),
            NamePart(korean = "쾌활한", english = "cheerful"),
            NamePart(korean = "즐거운", english = "joyful"),
            NamePart(korean = "기쁜", english = "glad"),
        )
    private val nouns =
        listOf(
            NamePart(korean = "사자", english = "lion"),
            NamePart(korean = "수달", english = "otter"),
            NamePart(korean = "돼지", english = "pig"),
            NamePart(korean = "강아지", english = "puppy"),
            NamePart(korean = "고양이", english = "cat"),
            NamePart(korean = "거북이", english = "turtle"),
            NamePart(korean = "호랑이", english = "tiger"),
            NamePart(korean = "오소리", english = "badger"),
            NamePart(korean = "두더지", english = "mole"),
            NamePart(korean = "송아지", english = "calf"),
            NamePart(korean = "에뮤", english = "emu"),
            NamePart(korean = "딩고", english = "dingo"),
            NamePart(korean = "코알라", english = "koala"),
            NamePart(korean = "페럿", english = "ferret"),
            NamePart(korean = "앵무새", english = "parrot"),
        )

    fun generate(): RandomUserName {
        val uuid = UUID.randomUUID().toString().replace("-", "")
        val adjective = adjectives.random()
        val noun = nouns.random()

        return RandomUserName(
            username = "${adjective.english}${noun.english}${uuid.takeLast(6)}",
            nickname = "${adjective.korean} ${noun.korean}",
        )
    }

    private fun <T> List<T>.random(): T = this[Random.nextInt(size)]

    private data class NamePart(
        val korean: String,
        val english: String,
    )
}
