package com.qlink.folder.domain

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import kotlin.time.Instant

class Folder(
    val id: Long? = null,
    val ownerId: Long,
    val name: String,
    val emoji: String? = null,
    val sharedAt: Instant? = null,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
) {
    fun validateOwner(ownerId: Long) {
        if (this.ownerId != ownerId) {
            throw BusinessException(ErrorCode.FOLDER_DIFFERENT_OWNER)
        }
    }
}
