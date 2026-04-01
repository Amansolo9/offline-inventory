package com.eaglepoint.task136.shared.db

import androidx.room.RoomDatabase
import kotlinx.coroutines.Dispatchers

expect object EncryptedRoomConfig {
    fun apply(builder: RoomDatabase.Builder<AppDatabase>, passphrase: ByteArray): RoomDatabase.Builder<AppDatabase>
}

fun secureBuilder(
    builder: RoomDatabase.Builder<AppDatabase>,
    passphrase: ByteArray,
): RoomDatabase.Builder<AppDatabase> {
    return EncryptedRoomConfig
        .apply(builder, passphrase)
        .setQueryCoroutineContext(Dispatchers.IO)
}
