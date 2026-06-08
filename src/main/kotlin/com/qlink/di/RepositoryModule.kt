package com.qlink.di

import com.qlink.ai.repository.AiJobRepository
import com.qlink.ai.repository.AiProviderRepository
import com.qlink.ai.repository.AvailableModelRepository
import com.qlink.ai.repository.DailyUsageRepository
import com.qlink.ai.repository.DbAiJobRepository
import com.qlink.ai.repository.DbAiProviderRepository
import com.qlink.ai.repository.DbAvailableModelRepository
import com.qlink.ai.repository.DbDailyUsageRepository
import com.qlink.ai.repository.DbUserProviderRepository
import com.qlink.ai.repository.UserProviderRepository
import com.qlink.auth.repository.AuthProviderRepository
import com.qlink.auth.repository.DbAuthProviderRepository
import com.qlink.auth.repository.DbRefreshTokenRepository
import com.qlink.auth.repository.RefreshTokenRepository
import com.qlink.device.repository.DbDeviceTokenRepository
import com.qlink.device.repository.DeviceTokenRepository
import com.qlink.folder.repository.DbFolderRepository
import com.qlink.folder.repository.FolderRepository
import com.qlink.foldermember.repository.DbFolderMemberRepository
import com.qlink.foldermember.repository.FolderMemberRepository
import com.qlink.link.repository.DbLinkRepository
import com.qlink.link.repository.LinkRepository
import com.qlink.notification.repository.DbNotificationRepository
import com.qlink.notification.repository.NotificationRepository
import com.qlink.todo.repository.DbTodoRepository
import com.qlink.todo.repository.TodoRepository
import com.qlink.user.repository.DbUserRepository
import com.qlink.user.repository.UserRepository
import org.koin.dsl.module

fun repositoryModule() =
    module {
        single<LinkRepository> {
            DbLinkRepository()
        }

        single<FolderRepository> {
            DbFolderRepository()
        }

        single<FolderMemberRepository> {
            DbFolderMemberRepository()
        }

        single<UserRepository> {
            DbUserRepository()
        }

        single<TodoRepository> {
            DbTodoRepository()
        }

        single<NotificationRepository> {
            DbNotificationRepository()
        }

        single<DeviceTokenRepository> {
            DbDeviceTokenRepository()
        }

        single<AiProviderRepository> {
            DbAiProviderRepository()
        }

        single<AvailableModelRepository> {
            DbAvailableModelRepository()
        }

        single<UserProviderRepository> {
            DbUserProviderRepository()
        }

        single<AiJobRepository> {
            DbAiJobRepository()
        }

        single<DailyUsageRepository> {
            DbDailyUsageRepository()
        }

        single<AuthProviderRepository> {
            DbAuthProviderRepository()
        }

        single<RefreshTokenRepository> {
            DbRefreshTokenRepository()
        }
    }
