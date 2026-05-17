package com.qlink.di

import com.qlink.folder.repository.DbFolderRepository
import com.qlink.folder.repository.FolderRepository
import com.qlink.link.repository.DbLinkRepository
import com.qlink.link.repository.LinkRepository
import com.qlink.user.repository.DbUserRepository
import com.qlink.user.repository.UserRepository
import org.koin.dsl.module

fun repositoryModule() = module {
    single<LinkRepository> {
        DbLinkRepository()
    }

    single<FolderRepository> {
        DbFolderRepository()
    }

    single<UserRepository> {
        DbUserRepository()
    }
}