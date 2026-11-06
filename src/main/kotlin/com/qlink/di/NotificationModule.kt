package com.qlink.di

import com.qlink.config.MonitoringConfig
import com.qlink.config.NotificationConfig
import com.qlink.notification.worker.TaskScheduler
import com.qlink.push.client.ExpoPushClient
import com.qlink.push.client.FcmPushClient
import com.qlink.push.client.FirebaseInitializer
import com.qlink.push.client.PushNotificationSenderRouter
import org.koin.dsl.module
import org.slf4j.LoggerFactory

fun notificationModule(config: NotificationConfig) =
    module {
        single {
            TaskScheduler(
                tx = get(),
                notificationRepository = get(),
                todoRepository = get(),
                linkRepository = get(),
                sendNotificationService = get(),
                traceIdLength = get<MonitoringConfig>().httpLogging.traceIdLength,
                log = LoggerFactory.getLogger(TaskScheduler::class.java),
            )
        }

        single {
            FirebaseInitializer(config.fcm.serviceAccountJson)
        }

        single {
            get<FirebaseInitializer>().initializeIfConfigured()
            FcmPushClient(firebaseInitializer = get())
        }

        single {
            ExpoPushClient(
                httpClient = get(),
                sendUrl = config.expo.sendUrl,
                accessToken = config.expo.accessToken,
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
