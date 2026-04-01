package com.eaglepoint.task136.shared.db

import androidx.room.RoomDatabase
import net.sqlcipher.database.SupportFactory

actual object EncryptedRoomConfig {
    actual fun apply(builder: RoomDatabase.Builder<AppDatabase>, passphrase: ByteArray): RoomDatabase.Builder<AppDatabase> {
        val factory = SupportFactory(passphrase)
        return builder.openHelperFactory(factory)
    }
}
