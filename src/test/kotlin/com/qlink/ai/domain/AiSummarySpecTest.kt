package com.qlink.ai.domain

import com.qlink.ai.client.AiSummarySpec
import com.qlink.ai.client.parseSummaryResponse
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class AiSummarySpecTest :
    BehaviorSpec({
        Given("AI 요약 공통 스펙") {
            When("OpenAI용 JSON schema를 생성하면") {
                val schema = AiSummarySpec.openAiJsonSchema
                val todoItem = schema.todoItemSchema()
                val properties = schema.properties()
                val reminderAt = todoItem.properties().getValue("reminderAt").jsonObject

                Then("strict structured output에 필요한 제약을 포함한다") {
                    schema.getValue("additionalProperties").jsonPrimitive.boolean shouldBe false
                    todoItem.getValue("additionalProperties").jsonPrimitive.boolean shouldBe false
                    properties
                        .getValue("tags")
                        .jsonObject
                        .getValue("items")
                        .jsonObject
                        .getValue("type")
                        .jsonPrimitive.content shouldBe
                        "string"
                    properties
                        .getValue("folderId")
                        .jsonObject
                        .getValue("type")
                        .jsonArray shouldBe
                        JsonArray(listOf(JsonPrimitive("number"), JsonPrimitive("null")))
                    reminderAt.getValue("type").jsonArray shouldBe
                        JsonArray(listOf(JsonPrimitive("string"), JsonPrimitive("null")))
                    schema.required() shouldContainAll listOf("id", "title", "summary", "folderId", "tags", "todos")
                    todoItem.required() shouldContainAll listOf("title", "reminderAt")
                }
            }

            When("Gemini용 JSON schema를 생성하면") {
                val schema = AiSummarySpec.geminiResponseSchema
                val todoItem = schema.todoItemSchema()
                val properties = schema.properties()
                val reminderAt = todoItem.properties().getValue("reminderAt").jsonObject

                Then("vendor 비호환 제약을 제외한다") {
                    schema.containsKey("additionalProperties") shouldBe false
                    todoItem.containsKey("additionalProperties") shouldBe false
                    properties
                        .getValue("tags")
                        .jsonObject
                        .getValue("items")
                        .jsonObject
                        .getValue("type")
                        .jsonPrimitive.content shouldBe
                        "string"
                    properties
                        .getValue("folderId")
                        .jsonObject
                        .getValue("type")
                        .jsonPrimitive.content shouldBe
                        "number"
                    reminderAt.getValue("type").jsonPrimitive.content shouldBe "string"
                    schema.required() shouldContainAll listOf("id", "title", "summary", "folderId", "tags", "todos")
                }
            }

            When("시스템 지시문을 만들면") {
                val instruction = AiSummarySpec.systemInstruction

                Then("액션 제목을 50자 이내로 쓰라는 지시를 포함한다") {
                    instruction shouldContain "Keep each todo title within 50 characters, including spaces."
                }
            }

            When("OpenAI용 액션 제목 스키마를 만들면") {
                val title =
                    AiSummarySpec.openAiJsonSchema
                        .todoItemSchema()
                        .properties()
                        .getValue("title")
                        .jsonObject

                Then("설명에 50자 제한을 담고 strict 비호환 maxLength는 넣지 않는다") {
                    title.getValue("description").jsonPrimitive.content shouldContain "within 50 characters"
                    title.containsKey("maxLength") shouldBe false
                }
            }

            When("Gemini용 액션 제목 스키마를 만들면") {
                val title =
                    AiSummarySpec.geminiResponseSchema
                        .todoItemSchema()
                        .properties()
                        .getValue("title")
                        .jsonObject

                Then("설명에 50자 제한을 담는다") {
                    title.getValue("description").jsonPrimitive.content shouldContain "within 50 characters"
                }
            }

            When("fenced JSON 응답을 파싱하면") {
                val response =
                    parseSummaryResponse(
                        rawResponse =
                            """
                            ```json
                            {"linkId":1,"folderId":3,"title":"네이버","summary":"요약","tags":["포털"],"todos":[]}
                            ```
                            """.trimIndent(),
                        usedTokens = 831,
                    )

                Then("linkId와 토큰을 공통 응답으로 정규화한다") {
                    response.linkId shouldBe 1L
                    response.folderId shouldBe 3L
                    response.title shouldBe "네이버"
                    response.summary shouldBe "요약"
                    response.tags shouldBe listOf("포털")
                    response.usedTokens shouldBe 831
                    response.todos shouldBe emptyList()
                }
            }

            When("plain JSON 응답을 파싱하면") {
                val response =
                    parseSummaryResponse(
                        rawResponse = """{"id":2,"folderId":null,"title":"제목","summary":"요약","tags":[],"todos":[]}""",
                        usedTokens = 10,
                    )

                Then("id를 linkId로 정규화한다") {
                    response.linkId shouldBe 2L
                    response.usedTokens shouldBe 10
                }
            }
        }
    })

private fun JsonObject.properties(): JsonObject = getValue("properties").jsonObject

private fun JsonObject.required(): List<String> = getValue("required").jsonArray.map { it.jsonPrimitive.content }

private fun JsonObject.todoItemSchema(): JsonObject =
    properties()
        .getValue("todos")
        .jsonObject
        .getValue("items")
        .jsonObject
