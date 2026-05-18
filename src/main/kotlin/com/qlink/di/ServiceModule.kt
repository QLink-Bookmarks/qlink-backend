package com.qlink.di

import com.qlink.link.service.CreateLinkService
import com.qlink.link.service.GetLinkDetailService
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
    }
