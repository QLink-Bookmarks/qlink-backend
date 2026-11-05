package com.qlink.di

import com.qlink.linkcontent.worker.LinkContentSignal
import org.koin.dsl.module

fun linkContentModule() =
    module {
        single {
            LinkContentSignal()
        }
    }
