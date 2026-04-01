package com.eaglepoint.task136.db

import android.content.Context
import androidx.room.Room
import com.eaglepoint.task136.shared.db.AppDatabase
import com.eaglepoint.task136.shared.db.secureBuilder
import net.sqlcipher.database.SQLiteDatabase

class AndroidDatabaseFactory(
    private val context: Context,
) : AppDatabaseFactory {
    override fun create(databaseName: String, passphrase: ByteArray): AppDatabase {
        SQLiteDatabase.loadLibs(context)
        val builder = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
        return secureBuilder(builder, passphrase).build()
    }
}
