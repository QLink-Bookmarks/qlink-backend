package com.qlink.ai.client

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

object AiSummarySpec {
    const val RESPONSE_SCHEMA_NAME = "qlink_bookmark_summary_result"

    val systemInstruction: String =
        """
        - Always respond in Korean.
        - Always respond in the given JSON format.
        - If any safety issue may happen, don't block and just mention in the summary.
        - Use UTC timezone for reminderAt.
        - If there is no reasonable reminder datetime, set reminderAt to null.
        - If there is no todo, return an empty todos array.
        - Choose folderId from the given folders only. Use null for 미분류.
        - Return tags as short keywords extracted from the URL contents.
        """.trimIndent()

    val openAiJsonSchema: JsonObject =
        summarySchema(
            includeAdditionalProperties = true,
            nullableReminderAt = true,
            nullableFolderId = true,
        )

    val geminiResponseSchema: JsonObject =
        summarySchema(
            includeAdditionalProperties = false,
            nullableReminderAt = false,
            nullableFolderId = false,
        )

    private fun summarySchema(
        includeAdditionalProperties: Boolean,
        nullableReminderAt: Boolean,
        nullableFolderId: Boolean,
    ): JsonObject =
        buildJsonObject {
            put("type", "object")
            putAdditionalProperties(includeAdditionalProperties)
            putJsonObject("properties") {
                putJsonObject("id") {
                    put("type", "number")
                    put("description", "link id given in the prompt")
                }
                putJsonObject("title") {
                    put("type", "string")
                    put("description", "title of the url contents")
                }
                putJsonObject("summary") {
                    put("type", "string")
                    put(
                        key = "description",
                        value = "one-liner-summary of the url contents for the user who wants to see important information quickly",
                    )
                }
                putJsonObject("folderId") {
                    if (nullableFolderId) {
                        put(
                            "type",
                            buildJsonArray {
                                add(JsonPrimitive("number"))
                                add(JsonPrimitive("null"))
                            },
                        )
                    } else {
                        put("type", "number")
                    }
                    put("description", "folder id selected from the folders given in the prompt. Use null for 미분류.")
                }
                putJsonObject("tags") {
                    put("type", "array")
                    put("description", "short keywords extracted from the URL contents")
                    putJsonObject("items") {
                        put("type", "string")
                    }
                }
                putJsonObject("todos") {
                    put("type", "array")
                    put("description", "reasonable tasks which the user should do if the URL content includes any task or deadline")
                    putJsonObject("items") {
                        put("type", "object")
                        putAdditionalProperties(includeAdditionalProperties)
                        putJsonObject("properties") {
                            putJsonObject("title") {
                                put("type", "string")
                                put("description", "title of the todo")
                            }
                            putJsonObject("reminderAt") {
                                if (nullableReminderAt) {
                                    put(
                                        "type",
                                        buildJsonArray {
                                            add(JsonPrimitive("string"))
                                            add(JsonPrimitive("null"))
                                        },
                                    )
                                } else {
                                    put("type", "string")
                                }
                                put("format", "date-time")
                                put(
                                    "description",
                                    "todo reminder datetime in UTC timezone. Use null if no reasonable reminder datetime exists.",
                                )
                            }
                        }
                        putRequired("title", "reminderAt")
                    }
                }
            }
            putRequired("id", "title", "summary", "folderId", "tags", "todos")
        }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putAdditionalProperties(enabled: Boolean) {
        if (enabled) {
            put("additionalProperties", false)
        }
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putRequired(vararg values: String) {
        putJsonArray("required") {
            values.forEach { add(JsonPrimitive(it)) }
        }
    }
}
