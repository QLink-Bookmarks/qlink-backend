package com.qlink.di

import com.qlink.notification.worker.TaskScheduler
import com.qlink.push.client.ExpoPushClient
import com.qlink.push.client.FcmPushClient
import com.qlink.push.client.PushNotificationSenderRouter
import org.koin.dsl.module
import org.slf4j.Logger

fun notificationModule(log: Logger) =
    module {
        single {
            TaskScheduler(
                tx = get(),
                notificationRepository = get(),
                todoRepository = get(),
                log = log,
            )
        }

        single {
            FcmPushClient()
        }

        single {
            ExpoPushClient(
                httpClient = get(),
                accessToken = System.getenv("EXPO_ACCESS_TOKEN"),
            )
        }

        single {
            PushNotificationSenderRouter(
                senders =
                    listOf(
                        get<FcmPushClient>(),
                        get<ExpoPushClient>(),
                    ),
            )
        }
    }
