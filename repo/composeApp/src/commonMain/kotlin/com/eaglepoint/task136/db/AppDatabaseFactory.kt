package com.eaglepoint.task136.db

import com.eaglepoint.task136.shared.db.AppDatabase

interface AppDatabaseFactory {
    fun create(databaseName: String, passphrase: ByteArray): AppDatabase
}
