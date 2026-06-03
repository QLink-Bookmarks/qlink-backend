package com.qlink.di

import com.qlink.notification.worker.TaskScheduler
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
    }
