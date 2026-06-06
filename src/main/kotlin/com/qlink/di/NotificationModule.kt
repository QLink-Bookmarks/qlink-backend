package com.qlink.di

import com.qlink.config.NotificationConfig
import com.qlink.notification.worker.TaskScheduler
import com.qlink.push.client.ExpoPushClient
import com.qlink.push.client.FcmPushClient
import com.qlink.push.client.FirebaseInitializer
import com.qlink.push.client.PushNotificationSenderRouter
import org.koin.dsl.module
import org.slf4j.Logger

fun notificationModule(
    config: NotificationConfig,
    log: Logger,
) = module {
    single {
        TaskScheduler(
            tx = get(),
            notificationRepository = get(),
            todoRepository = get(),
            sendNotificationService = get(),
            log = log,
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
