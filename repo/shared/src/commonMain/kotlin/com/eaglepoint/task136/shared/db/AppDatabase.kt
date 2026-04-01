package com.eaglepoint.task136.shared.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        UserEntity::class,
        ResourceEntity::class,
        CartItemEntity::class,
        OrderEntity::class,
        OrderLineItemEntity::class,
        DeviceBindingEntity::class,
        RuleHitMetricEntity::class,
        DailyLedgerEntity::class,
        DiscrepancyTicketEntity::class,
        MeetingEntity::class,
        MeetingAttendeeEntity::class,
        CourseEntity::class,
        EnrollmentEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun resourceDao(): ResourceDao
    abstract fun cartDao(): CartDao
    abstract fun orderDao(): OrderDao
    abstract fun orderLineItemDao(): OrderLineItemDao
    abstract fun deviceBindingDao(): DeviceBindingDao
    abstract fun governanceDao(): GovernanceDao
    abstract fun meetingDao(): MeetingDao
    abstract fun learningDao(): LearningDao
}
