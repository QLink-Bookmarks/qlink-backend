package com.qlink.folder.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.error.requireFalse
import com.qlink.common.transaction.TransactionRunner
import com.qlink.folder.domain.FolderDeleteOption
import com.qlink.folder.domain.FolderDeletionOptions
import com.qlink.folder.domain.service.FolderDeleteTargetDomainService
import com.qlink.folder.domain.service.FolderDeletionDomainService
import com.qlink.folder.dto.FolderDeletionResult
import com.qlink.folder.repository.FolderRepository
import com.qlink.foldermember.repository.FolderMemberRepository
import com.qlink.link.repository.LinkRepository
import com.qlink.notification.domain.NotificationContext
import com.qlink.notification.repository.NotificationRepository
import com.qlink.notification.worker.TaskScheduler
import com.qlink.todo.repository.TodoRepository
import com.qlink.user.repository.UserRepository
import kotlin.time.Clock

class DeleteFolderService(
    private val tx: TransactionRunner,
    private val folderRepository: FolderRepository,
    private val linkRepository: LinkRepository,
    private val userRepository: UserRepository,
    private val folderMemberRepository: FolderMemberRepository,
    private val todoRepository: TodoRepository,
    private val notificationRepository: NotificationRepository,
    private val taskScheduler: TaskScheduler,
) {
    suspend fun deleteFolder(
        loginId: Long,
        folderId: Long,
        onDelete: String?,
        targetFolderId: Long? = null,
    ) {
        val result =
            tx.required {
                userRepository.emptyById(loginId).requireFalse(ErrorCode.FOLDER_OWNER_NOT_FOUND)

                val discoveredSource =
                    folderRepository.findById(folderId)
                        ?: return@required FolderDeletionResult(cancelNotificationIds = emptyList())
                discoveredSource.validateOwner(loginId)
                val options = FolderDeletionOptions.from(onDelete = onDelete, targetFolderId = targetFolderId)

                // Discover before locking so move always locks both folders in the same order.
                // Only these locked rows supply the facts used for the actual mutation.
                val folders =
                    folderRepository
                        .findAllByIdsForUpdate(
                            listOfNotNull(folderId, options.targetFolderId).distinct().sorted(),
                        ).associateBy { it.id!! }
                val source =
                    folders[folderId]
                        ?: return@required FolderDeletionResult(cancelNotificationIds = emptyList())
                source.validateOwner(loginId)
                if (options.option == FolderDeleteOption.MOVE) {
                    val targetId = options.targetFolderId ?: error("MOVE options must include a target folder")
                    source.validateMoveTarget(targetId)
                    val target = folders[targetId] ?: throw BusinessException(ErrorCode.FOLDER_DELETE_TARGET_NOT_FOUND)
                    val member =
                        folderMemberRepository.findByFolderIdAndUserIdForUpdate(
                            folderId = targetId,
                            userId = loginId,
                        )
                    FolderDeleteTargetDomainService.validateWritable(target = target, member = member, loginId = loginId)
                }

                val links = linkRepository.findAllByFolderIdForUpdate(folderId)
                val plan =
                    FolderDeletionDomainService.plan(
                        source = source,
                        links = links,
                        callerId = loginId,
                        options = options,
                    )
                val todos = todoRepository.findAllByLinkIdsForUpdate(plan.deleteLinkIds)
                val cancelNotificationIds =
                    notificationRepository.deletePendingByContextIds(
                        context = NotificationContext.TODO,
                        ids = todos.map { it.id!! },
                    )
                val now = Clock.System.now()
                linkRepository.assignFolderByIds(linkIds = plan.moveLinkIds, folderId = options.targetFolderId, now = now)
                linkRepository.assignFolderByIds(linkIds = plan.detachLinkIds, folderId = null, now = now)
                linkRepository.deleteAllByIds(plan.deleteLinkIds)
                folderRepository.deleteById(folderId)
                FolderDeletionResult(cancelNotificationIds = cancelNotificationIds)
            }
        result.cancelNotificationIds.forEach(taskScheduler::cancel)
    }
}
