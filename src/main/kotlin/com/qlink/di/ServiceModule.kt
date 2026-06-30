package com.qlink.di

import com.qlink.ai.service.GetAiProviderModelsService
import com.qlink.ai.service.PutAiUserProviderService
import com.qlink.ai.service.UpdateLinkAiSummaryService
import com.qlink.auth.service.AuthTokenService
import com.qlink.auth.service.ConnectAuthProviderService
import com.qlink.auth.service.RandomUserNameGenerator
import com.qlink.auth.service.RefreshAuthTokenService
import com.qlink.auth.service.SignInService
import com.qlink.auth.service.SignOutService
import com.qlink.device.service.DeleteDeviceService
import com.qlink.device.service.PutDeviceService
import com.qlink.folder.service.AcceptFolderInvitationService
import com.qlink.folder.service.CreateFolderInvitationService
import com.qlink.folder.service.CreateFolderService
import com.qlink.folder.service.DeleteFolderMemberService
import com.qlink.folder.service.DeleteFolderService
import com.qlink.folder.service.FolderAccessValidator
import com.qlink.folder.service.GetFolderMembersService
import com.qlink.folder.service.GetFoldersService
import com.qlink.folder.service.UpdateFolderService
import com.qlink.image.service.UploadImageService
import com.qlink.link.service.CopyLinkService
import com.qlink.link.service.CreateLinkService
import com.qlink.link.service.DeleteLinkService
import com.qlink.link.service.GetLinkDetailService
import com.qlink.link.service.GetLinksService
import com.qlink.link.service.PatchLinkService
import com.qlink.link.service.SetLinkFavoriteService
import com.qlink.link.service.UpdateLinkService
import com.qlink.notification.service.GetNotificationsService
import com.qlink.notification.service.GetUnreadNotificationCountService
import com.qlink.notification.service.ReadNotificationService
import com.qlink.notification.service.ScheduleTodoNotificationService
import com.qlink.notification.service.SendNotificationService
import com.qlink.todo.service.CompleteTodoService
import com.qlink.todo.service.CreateTodoService
import com.qlink.todo.service.DeleteTodoService
import com.qlink.todo.service.GetTodosService
import com.qlink.todo.service.UpdateTodoService
import com.qlink.user.service.DeleteAccountService
import com.qlink.user.service.GetMyProfileService
import com.qlink.user.service.GetMySettingsService
import com.qlink.user.service.UpdateMyAgreementsService
import com.qlink.user.service.UpdateMyProfileService
import com.qlink.user.service.UpdateMySettingsService
import org.koin.dsl.module

fun serviceModule() =
    module {
        single {
            AuthTokenService(
                securityConfig = get(),
            )
        }

        single {
            RandomUserNameGenerator()
        }

        single {
            SignInService(
                tx = get(),
                userRepository = get(),
                authProviderRepository = get(),
                refreshTokenRepository = get(),
                authResourceClientRouter = get(),
                authTokenService = get(),
                randomUserNameGenerator = get(),
            )
        }

        single {
            RefreshAuthTokenService(
                tx = get(),
                userRepository = get(),
                refreshTokenRepository = get(),
                authTokenService = get(),
            )
        }

        single {
            ConnectAuthProviderService(
                tx = get(),
                userRepository = get(),
                authProviderRepository = get(),
                authResourceClientRouter = get(),
            )
        }

        single {
            SignOutService(
                tx = get(),
                userRepository = get(),
                refreshTokenRepository = get(),
            )
        }

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
                authProviderRepository = get(),
            )
        }

        single {
            UpdateMyProfileService(
                tx = get(),
                userRepository = get(),
            )
        }

        single {
            DeleteAccountService(
                tx = get(),
                userRepository = get(),
                folderRepository = get(),
                folderMemberRepository = get(),
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
            UpdateMyAgreementsService(
                tx = get(),
                userRepository = get(),
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
            DeleteDeviceService(
                tx = get(),
                userRepository = get(),
                deviceTokenRepository = get(),
            )
        }

        single {
            FolderAccessValidator(
                folderRepository = get(),
                folderMemberRepository = get(),
            )
        }

        single {
            CreateFolderService(
                tx = get(),
                folderRepository = get(),
                folderMemberRepository = get(),
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
                folderMemberRepository = get(),
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
            DeleteFolderMemberService(
                tx = get(),
                folderRepository = get(),
                folderMemberRepository = get(),
                userRepository = get(),
            )
        }

        single {
            GetFolderMembersService(
                tx = get(),
                folderRepository = get(),
                folderMemberRepository = get(),
                userRepository = get(),
            )
        }

        single {
            CreateFolderInvitationService(
                tx = get(),
                folderRepository = get(),
                userRepository = get(),
                securityConfig = get(),
            )
        }

        single {
            AcceptFolderInvitationService(
                tx = get(),
                folderRepository = get(),
                folderMemberRepository = get(),
                userRepository = get(),
                securityConfig = get(),
            )
        }

        single {
            CreateLinkService(
                tx = get(),
                linkRepository = get(),
                todoRepository = get(),
                userRepository = get(),
                folderAccessValidator = get(),
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
                folderAccessValidator = get(),
            )
        }

        single {
            PatchLinkService(
                tx = get(),
                linkRepository = get(),
                todoRepository = get(),
                userRepository = get(),
                folderAccessValidator = get(),
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
            SetLinkFavoriteService(
                tx = get(),
                linkRepository = get(),
                userRepository = get(),
            )
        }

        single {
            CopyLinkService(
                tx = get(),
                userRepository = get(),
                folderRepository = get(),
                folderMemberRepository = get(),
                linkRepository = get(),
            )
        }

        single {
            GetNotificationsService(
                tx = get(),
                notificationRepository = get(),
                userRepository = get(),
            )
        }

        single {
            GetUnreadNotificationCountService(
                tx = get(),
                notificationRepository = get(),
                userRepository = get(),
            )
        }

        single {
            ReadNotificationService(
                tx = get(),
                notificationRepository = get(),
                userRepository = get(),
            )
        }

        single {
            UpdateLinkAiSummaryService(
                tx = get(),
                userRepository = get(),
                folderRepository = get(),
                folderAccessValidator = get(),
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

        single {
            SendNotificationService(
                tx = get(),
                notificationRepository = get(),
                userRepository = get(),
                deviceTokenRepository = get(),
                senderRouter = get(),
                todoRepository = get(),
            )
        }

        single {
            UploadImageService(
                tx = get(),
                userRepository = get(),
                imageStorage = get(),
            )
        }
    }
