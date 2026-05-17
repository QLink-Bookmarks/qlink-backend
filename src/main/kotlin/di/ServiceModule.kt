package com.qlink.di

import com.qlink.link.service.CreateLinkService
import org.koin.dsl.module

fun serviceModule() = module {
    single { CreateLinkService(get(), get(), get(), get()) }
}