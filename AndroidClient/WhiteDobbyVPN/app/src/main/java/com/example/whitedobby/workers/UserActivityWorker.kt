package com.example.whitedobby

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ServerTimestamp
import java.util.*
import java.util.concurrent.TimeUnit

class UserActivityWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {

    override fun doWork(): Result {
        Log.d("UserActivityWorker", "DO WORK for userId = ${inputData.getString("USER_ID")}")
        // Получаем userId из inputData
        val userId = inputData.getString("USER_ID") ?: return Result.failure()

        val firestore = FirebaseFirestore.getInstance()
        val docRef = firestore.collection("users").document(userId)

        return try {
            // 1) Получаем документ
            val snapshot = Tasks.await(docRef.get())

            if (snapshot.exists()) {
                // Предполагаем, что "lastLogin" хранит Timestamp (или null)
                val lastLogin = snapshot.getTimestamp("lastLogin")
                val now = Date()

                val status = if (lastLogin != null) {
                    val diffMillis = now.time - lastLogin.toDate().time
                    val diffHours = TimeUnit.MILLISECONDS.toHours(diffMillis)

                    when {
                        diffHours < 24 -> "recentlyActive"
                        diffHours < 24 * 30 -> "longAgo"
                        else -> "veryLongAgo"
                    }
                } else {
                    // Если нет lastLogin, считаем "veryLongAgo" или что-то другое
                    "noData"
                }

                // 2) Обновляем поле "activityStatus" в документе
                val updateTask = docRef.update("activityStatus", status)
                Tasks.await(updateTask) // Блокирующий вызов в doWork() допустим

                Log.d("UserActivityWorker", "User $userId status: $status")
                return Result.success()
            } else {
                // Документ не существует — ничего не делаем, но вернём успех
                Log.w("UserActivityWorker", "Document $userId does not exist")
                return Result.success()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }
}