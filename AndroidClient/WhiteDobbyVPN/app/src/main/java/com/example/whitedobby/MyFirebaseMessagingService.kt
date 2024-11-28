package com.example.whitedobby

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    // Вызывается, когда сообщение получено
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // Проверяем, содержит ли сообщение данные
        remoteMessage.data.isNotEmpty().let {
            // Обрабатываем данные сообщения
            val title = remoteMessage.data["title"] ?: "Заголовок по умолчанию"
            val message = remoteMessage.data["message"] ?: "Сообщение по умолчанию"
            sendNotification(title, message)
        }

        // Проверяем, содержит ли сообщение уведомление
        remoteMessage.notification?.let {
            val title = it.title ?: "Заголовок по умолчанию"
            val message = it.body ?: "Сообщение по умолчанию"
            sendNotification(title, message)
        }
    }

    // Вызывается при обновлении токена FCM
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Отправьте токен на ваш сервер, если необходимо
    }

    private fun sendNotification(title: String, message: String) {
        val channelId = "default_channel"
        val notificationId = System.currentTimeMillis().toInt()

        // Создаем канал уведомлений (для Android 8 и выше)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = "Основной канал"
            val channelDescription = "Описание основного канала"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, channelName, importance).apply {
                description = channelDescription
            }
            // Регистрируем канал в системе
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        // Создаем интент для открытия MainActivity при нажатии на уведомление
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_IMMUTABLE else 0
        )

        // Создаем уведомление
        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification) // Замените на ваш значок
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        // Показываем уведомление
        with(NotificationManagerCompat.from(this)) {
            notify(notificationId, builder.build())
        }
    }
}