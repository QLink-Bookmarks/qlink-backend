package com.qlink.di

import com.qlink.bookmarkimport.worker.ImportThumbnailSignal
import org.koin.dsl.module

fun bookmarkImportModule() =
    module {
        single {
            ImportThumbnailSignal()
        }
    }
