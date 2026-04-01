package com.eaglepoint.task136.di

import com.eaglepoint.task136.shared.di.databaseModule
import com.eaglepoint.task136.shared.di.sharedCoreModule
import com.eaglepoint.task136.shared.db.AppDatabase
import com.eaglepoint.task136.shared.viewmodel.ResourceListViewModel
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

fun initKoinIfNeeded(database: AppDatabase, isDebug: Boolean = false) {
    if (GlobalContext.getOrNull() != null) return

    startKoin {
        modules(
            databaseModule(database),
            sharedCoreModule(isDebug = isDebug),
            module {
                single { ResourceListViewModel(get(), get()) }
            },
        )
    }
}
