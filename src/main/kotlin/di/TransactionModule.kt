package com.qlink.di

import com.qlink.common.transaction.ServiceTransactionRunner
import com.qlink.common.transaction.TransactionRunner
import org.koin.dsl.module

fun transactionModule() =
    module {
        single<TransactionRunner> {
            ServiceTransactionRunner(get())
        }
    }
