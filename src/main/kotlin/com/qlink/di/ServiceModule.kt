package com.qlink.di

import com.qlink.link.service.CreateLinkService
import com.qlink.link.service.GetLinkDetailService
import com.qlink.link.service.UpdateLinkService
import org.koin.dsl.module

fun serviceModule() =
    module {
        single {
            CreateLinkService(
                tx = get(),
                linkRepository = get(),
                userRepository = get(),
                folderRepository = get(),
            )
        }

        single {
            GetLinkDetailService(
                tx = get(),
                linkRepository = get(),
                folderRepository = get(),
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
    }
