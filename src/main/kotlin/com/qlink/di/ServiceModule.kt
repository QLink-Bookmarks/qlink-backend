package com.qlink.di

import com.qlink.ai.service.GetAiProviderModelsService
import com.qlink.ai.service.PutAiUserProviderService
import com.qlink.ai.service.UpdateLinkAiSummaryService
import com.qlink.device.service.PutDeviceService
import com.qlink.folder.service.CreateFolderService
import com.qlink.folder.service.DeleteFolderService
import com.qlink.folder.service.GetFoldersService
import com.qlink.folder.service.UpdateFolderService
import com.qlink.link.service.CreateLinkService
import com.qlink.link.service.DeleteLinkService
import com.qlink.link.service.GetLinkDetailService
import com.qlink.link.service.GetLinksService
import com.qlink.link.service.PatchLinkService
import com.qlink.link.service.UpdateLinkService
import com.qlink.notification.service.ScheduleTodoNotificationService
import com.qlink.todo.service.CompleteTodoService
import com.qlink.todo.service.CreateTodoService
import com.qlink.todo.service.DeleteTodoService
import com.qlink.todo.service.GetTodosService
import com.qlink.todo.service.UpdateTodoService
import com.qlink.user.service.GetMyProfileService
import com.qlink.user.service.GetMySettingsService
import com.qlink.user.service.UpdateMySettingsService
import org.koin.dsl.module

fun serviceModule() =
    module {
        single {
            GetMyProfileService(
                tx = get(),
                userRepository = get(),
            )
        }

        single {
            GetMySettingsService(
                tx = get(),
                userRepository = get(),
                aiProviderRepository = get(),
                availableModelRepository = get(),
            )
        }

        single {
            UpdateMySettingsService(
                tx = get(),
                userRepository = get(),
                aiProviderRepository = get(),
                availableModelRepository = get(),
            )
        }

        single {
            GetAiProviderModelsService(
                tx = get(),
                userRepository = get(),
                userProviderRepository = get(),
                aiProviderRepository = get(),
                availableModelRepository = get(),
            )
        }

        single {
            PutAiUserProviderService(
                tx = get(),
                userRepository = get(),
                aiProviderRepository = get(),
                userProviderRepository = get(),
                aiClientRouter = get(),
                apiKeyCipher = get(),
            )
        }

        single {
            PutDeviceService(
                tx = get(),
                userRepository = get(),
                deviceTokenRepository = get(),
            )
        }

        single {
            CreateFolderService(
                tx = get(),
                folderRepository = get(),
                userRepository = get(),
            )
        }

        single {
            GetFoldersService(
                tx = get(),
                folderRepository = get(),
                userRepository = get(),
            )
        }

        single {
            UpdateFolderService(
                tx = get(),
                folderRepository = get(),
                userRepository = get(),
            )
        }

        single {
            DeleteFolderService(
                tx = get(),
                folderRepository = get(),
                linkRepository = get(),
                userRepository = get(),
            )
        }

        single {
            CreateLinkService(
                tx = get(),
                linkRepository = get(),
                todoRepository = get(),
                userRepository = get(),
                folderRepository = get(),
                scheduleTodoNotificationService = get(),
            )
        }

        single {
            GetLinkDetailService(
                tx = get(),
                linkRepository = get(),
                todoRepository = get(),
            )
        }

        single {
            GetLinksService(
                tx = get(),
                linkRepository = get(),
                todoRepository = get(),
                userRepository = get(),
            )
        }

        single {
            UpdateLinkService(
                tx = get(),
                linkRepository = get(),
                userRepository = get(),
                folderRepository = get(),
            )
        }

        single {
            PatchLinkService(
                tx = get(),
                linkRepository = get(),
                todoRepository = get(),
                userRepository = get(),
                folderRepository = get(),
                scheduleTodoNotificationService = get(),
            )
        }

        single {
            DeleteLinkService(
                tx = get(),
                linkRepository = get(),
                userRepository = get(),
            )
        }

        single {
            UpdateLinkAiSummaryService(
                tx = get(),
                userRepository = get(),
                folderRepository = get(),
                linkRepository = get(),
                userProviderRepository = get(),
                availableModelRepository = get(),
                aiJobRepository = get(),
                dispatcher = get(),
            )
        }

        single {
            GetTodosService(
                tx = get(),
                todoRepository = get(),
                userRepository = get(),
            )
        }

        single {
            CreateTodoService(
                tx = get(),
                todoRepository = get(),
                linkRepository = get(),
                userRepository = get(),
                scheduleTodoNotificationService = get(),
            )
        }

        single {
            UpdateTodoService(
                tx = get(),
                todoRepository = get(),
                linkRepository = get(),
                userRepository = get(),
                scheduleTodoNotificationService = get(),
            )
        }

        single {
            CompleteTodoService(
                tx = get(),
                todoRepository = get(),
                userRepository = get(),
                scheduleTodoNotificationService = get(),
            )
        }

        single {
            DeleteTodoService(
                tx = get(),
                todoRepository = get(),
                userRepository = get(),
                scheduleTodoNotificationService = get(),
            )
        }

        single {
            ScheduleTodoNotificationService(
                tx = get(),
                notificationRepository = get(),
                taskScheduler = get(),
            )
        }
    }
