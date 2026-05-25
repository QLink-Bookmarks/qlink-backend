@file:Suppress("ktlint:standard:filename")

package com.qlink.link.dto

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.serialization.PatchField
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.time.Instant

@Serializable
data class PatchLinkRequestBody(
    val folderId: Long? = null,
    val memo: String? = null,
    val tags: List<String>? = null,
    val todos: List<PatchLinkTodoRequest>? = null,
)

@Serializable
data class PatchLinkTodoRequest(
    val id: Long? = null,
    val title: String,
    val reminderAt: Instant? = null,
)

data class PatchLinkRequest(
    val folderId: PatchField<Long?> = PatchField.Absent,
    val memo: PatchField<String?> = PatchField.Absent,
    val tags: PatchField<List<String>> = PatchField.Absent,
    val todos: PatchField<List<PatchLinkTodoRequest>> = PatchField.Absent,
) {
    companion object {
        private val json = Json
        private val allowedKeys = setOf("folderId", "memo", "tags", "todos")

        fun fromJsonObject(jsonObject: JsonObject): PatchLinkRequest =
            runCatching {
                validateKeys(jsonObject)

                PatchLinkRequest(
                    folderId = jsonObject.toPatchField<Long?>("folderId"),
                    memo = jsonObject.toPatchField<String?>("memo"),
                    tags = jsonObject.toPatchField<List<String>>("tags"),
                    todos = jsonObject.toPatchField<List<PatchLinkTodoRequest>>("todos"),
                )
            }.getOrElse {
                throw BusinessException(ErrorCode.COMMON_BAD_REQUEST, it)
            }

        private fun validateKeys(jsonObject: JsonObject) {
            if (jsonObject.keys.any { it !in allowedKeys }) {
                throw BusinessException(ErrorCode.COMMON_BAD_REQUEST)
            }
        }

        private inline fun <reified T> JsonObject.toPatchField(key: String): PatchField<T> =
            this[key]?.let {
                PatchField.Present(json.decodeFromJsonElement<T>(it))
            } ?: if (containsKey(key)) {
                PatchField.Present(json.decodeFromJsonElement<T>(getValue(key)))
            } else {
                PatchField.Absent
            }
    }
}
