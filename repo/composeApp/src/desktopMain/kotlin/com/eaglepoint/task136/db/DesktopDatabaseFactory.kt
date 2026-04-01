package com.eaglepoint.task136.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.eaglepoint.task136.shared.db.AppDatabase
import com.eaglepoint.task136.shared.db.secureBuilder
import java.io.File

class DesktopDatabaseFactory : AppDatabaseFactory {
    override fun create(databaseName: String, passphrase: ByteArray): AppDatabase {
        val dbPath = File(System.getProperty("user.home"), databaseName).absolutePath
        val builder = Room.databaseBuilder<AppDatabase>(name = dbPath)
            .setDriver(BundledSQLiteDriver())

        return secureBuilder(builder, passphrase).build()
    }
}
