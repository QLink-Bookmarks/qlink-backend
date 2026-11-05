package com.qlink.link.domain

import com.qlink.support.fixture.RandomFixture
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldStartWith

private const val ELLIPSIS = "…"
private const val KEYWORD = "본문검색"

private fun filler(length: Int): String = RandomFixture.randomFixedSentence(length)

private fun digits(length: Int): String = (1..length).joinToString("") { RandomFixture.randomInt(0, 9).toString() }

private fun Snippet.visibleText(): String = text.removePrefix(ELLIPSIS).removeSuffix(ELLIPSIS)

private fun Snippet.highlightedTexts(): List<String> = highlights.map { text.substring(it.start, it.end) }

class SnippetTest :
    BehaviorSpec({
        Given("100자 이상 본문에서 서버가 자른 창") {
            val before = filler(Snippet.CONTEXT_BEFORE)
            val source = before + KEYWORD + filler(Snippet.SOURCE_MAX_CODE_POINTS - Snippet.CONTEXT_BEFORE - KEYWORD.length)
            val bodyLength = RandomFixture.randomInt(200, 100_000)

            When("앞뒤가 잘렸으면") {
                val snippet = Snippet.of(source = source, headCut = true, tailCut = true, keyword = KEYWORD, bodyLength = bodyLength)

                Then("양끝에 말줄임이 붙고 보이는 글자가 100 이하다") {
                    snippet.text shouldStartWith ELLIPSIS
                    snippet.text shouldEndWith ELLIPSIS
                    snippet.visibleText().length shouldBeLessThanOrEqual 100
                    snippet.text.length shouldBeLessThanOrEqual Snippet.MAX_TEXT_LENGTH
                }
            }

            When("스니펫을 만들면") {
                val snippet = Snippet.of(source = source, headCut = true, tailCut = true, keyword = KEYWORD, bodyLength = bodyLength)

                Then("일치어 앞 문맥이 15자 이하다(가운데 정렬하지 않음)") {
                    snippet.visibleText().substringBefore(KEYWORD) shouldBe before
                    snippet.visibleText().substringBefore(KEYWORD).length shouldBeLessThanOrEqual Snippet.CONTEXT_BEFORE
                }
            }
        }

        Given("surrogate 쌍이 많아 말줄임을 붙이면 100 UTF-16 유닛을 넘는 창") {
            val emoji = "😀"
            val before = filler(Snippet.CONTEXT_BEFORE - 1)
            val source = before + KEYWORD + emoji.repeat(Snippet.SOURCE_MAX_CODE_POINTS - before.length - KEYWORD.length)

            When("99번째 유닛이 쌍의 끝이면") {
                val snippet = Snippet.of(source = source, headCut = true, tailCut = true, keyword = KEYWORD, bodyLength = 1_000)

                Then("99유닛에서 surrogate 보존으로 잘리고 말줄임으로 끝나 길이가 100이다") {
                    snippet.text.length shouldBe Snippet.MAX_TEXT_LENGTH
                    snippet.text shouldEndWith ELLIPSIS
                    snippet.text
                        .dropLast(1)
                        .last()
                        .isHighSurrogate() shouldBe false
                    snippet.text shouldStartWith ELLIPSIS + before + KEYWORD + emoji
                    snippet.highlightedTexts() shouldContainExactly listOf(KEYWORD)
                }
            }

            When("99번째 유닛이 쌍의 가운데면") {
                val shifted = filler(1) + source
                val snippet = Snippet.of(source = shifted, headCut = true, tailCut = true, keyword = KEYWORD, bodyLength = 1_000)

                Then("쌍을 깨지 않고 98유닛에서 잘려 길이가 99이다") {
                    snippet.text.length shouldBe Snippet.MAX_TEXT_LENGTH - 1
                    snippet.text shouldEndWith ELLIPSIS
                    snippet.text
                        .dropLast(1)
                        .last()
                        .isLowSurrogate() shouldBe true
                }
            }
        }

        Given("50자 본문의 가운데에 있는 4자 검색어") {
            val body = filler(20) + KEYWORD + filler(26)
            val source = body.substring(20 - Snippet.CONTEXT_BEFORE)

            When("스니펫을 만들면") {
                val snippet = Snippet.of(source = source, headCut = true, tailCut = false, keyword = KEYWORD, bodyLength = body.length)

                Then("보이는 글자가 15자 이하이고 일치어가 가운데에 있다") {
                    snippet.visibleText().length shouldBeLessThanOrEqual 15
                    snippet.text shouldBe ELLIPSIS + body.substring(15, 30) + ELLIPSIS
                    snippet.highlightedTexts() shouldContainExactly listOf(KEYWORD)
                }
            }
        }

        Given("10자 본문 가운데의 5자 검색어") {
            val keyword = "본문검색어"
            val body = filler(3) + keyword + filler(2)

            When("스니펫을 만들면") {
                val snippet = Snippet.of(source = body, headCut = false, tailCut = false, keyword = keyword, bodyLength = body.length)

                Then("보이는 글자가 일치어 5자뿐이고 양쪽에 말줄임이 붙는다") {
                    snippet.text shouldBe ELLIPSIS + keyword + ELLIPSIS
                }
            }
        }

        Given("검색어가 여러 번, 대소문자를 달리해 들어 있는 창") {
            val keyword = "abc"
            val first = digits(5)
            val second = digits(7)
            val source = "${first}ABC$second" + "abc" + "가나" + "aBc" + digits(10)

            When("스니펫을 만들면") {
                val snippet = Snippet.of(source = source, headCut = false, tailCut = true, keyword = keyword, bodyLength = 500)

                Then("최종 문자열 기준으로 모든 위치의 범위가 겹치지 않게 나온다") {
                    val start = first.length
                    val secondStart = start + 3 + second.length
                    snippet.highlights shouldContainExactly
                        listOf(
                            Snippet.Highlight(start = start, end = start + 3),
                            Snippet.Highlight(start = secondStart, end = secondStart + 3),
                            Snippet.Highlight(start = secondStart + 5, end = secondStart + 8),
                        )
                    snippet.highlights.zipWithNext().forEach { (left, right) -> (left.end <= right.start) shouldBe true }
                    snippet.highlights.forEach { (it.start < it.end && it.end <= snippet.text.length) shouldBe true }
                }
            }
        }

        Given("소문자로 바꾸면 길이가 달라지는 글자가 든 창") {
            val source = "İ" + filler(10) + KEYWORD + filler(10)

            When("스니펫을 만들면") {
                val snippet = Snippet.of(source = source, headCut = false, tailCut = false, keyword = KEYWORD, bodyLength = 500)

                Then("범위가 비어 있다") {
                    snippet.highlights.shouldBeEmpty()
                }
            }
        }

        Given("최종 문자열에 들어가지 않는 긴 검색어") {
            val keyword = "검".repeat(120)
            val source = filler(Snippet.CONTEXT_BEFORE) + keyword.take(Snippet.SOURCE_MAX_CODE_POINTS - Snippet.CONTEXT_BEFORE)

            When("스니펫을 만들면") {
                val snippet = Snippet.of(source = source, headCut = true, tailCut = true, keyword = keyword, bodyLength = 500)

                Then("범위가 비어 있다") {
                    snippet.highlights.shouldBeEmpty()
                }
            }
        }

        Given("_·%가 든 검색어") {
            val keyword = "50%_off"
            val decoy = "50xxoff"
            val before = filler(5)
            val source = before + keyword + decoy + filler(10)

            When("스니펫을 만들면") {
                val snippet = Snippet.of(source = source, headCut = false, tailCut = false, keyword = keyword, bodyLength = 500)

                Then("글자 그대로 찾는다") {
                    snippet.highlights shouldContainExactly
                        listOf(Snippet.Highlight(start = before.length, end = before.length + keyword.length))
                }
            }
        }
    })
