package com.eaglepoint.task136.shared.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface LearningDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCourse(course: CourseEntity)

    @Query("SELECT * FROM courses ORDER BY title ASC")
    suspend fun getAllCourses(): List<CourseEntity>

    @Query("SELECT * FROM courses WHERE id = :id")
    suspend fun getCourseById(id: String): CourseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEnrollment(enrollment: EnrollmentEntity)

    @Query("SELECT * FROM enrollments WHERE userId = :userId")
    suspend fun getEnrollmentsByUser(userId: String): List<EnrollmentEntity>

    @Query("SELECT * FROM enrollments WHERE userId = :userId AND courseId = :courseId LIMIT 1")
    suspend fun getEnrollment(userId: String, courseId: String): EnrollmentEntity?

    @Update
    suspend fun updateEnrollment(enrollment: EnrollmentEntity)
}
