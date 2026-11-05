@file:Suppress("ktlint:standard:filename")

package com.qlink.folder.dto

data class FolderDeletionPlan(
    val deleteLinkIds: List<Long>,
    val moveLinkIds: List<Long>,
    val detachLinkIds: List<Long>,
)

data class FolderDeletionResult(
    val cancelNotificationIds: List<Long>,
)
