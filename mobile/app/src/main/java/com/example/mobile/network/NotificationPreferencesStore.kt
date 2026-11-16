package com.example.mobile.network

import android.content.Context

data class NotificationPreferences(
    val smsEnabled: Boolean = false,
    val emailEnabled: Boolean = true,
    val pushEnabled: Boolean = true
)

interface NotificationPreferencesStore {
    fun load(userId: Long): NotificationPreferences
    fun save(userId: Long, prefs: NotificationPreferences)
}

class InMemoryNotificationPreferencesStore : NotificationPreferencesStore {
    private val map = mutableMapOf<Long, NotificationPreferences>()
    override fun load(userId: Long) = map[userId] ?: NotificationPreferences()
    override fun save(userId: Long, prefs: NotificationPreferences) {
        map[userId] = prefs
    }
}

class SharedPreferencesNotificationPreferencesStore(context: Context) : NotificationPreferencesStore {
    private val prefs = context.applicationContext.getSharedPreferences(
        "notification_preferences",
        Context.MODE_PRIVATE
    )

    override fun load(userId: Long): NotificationPreferences = NotificationPreferences(
        smsEnabled = prefs.getBoolean("sms_$userId", false),
        emailEnabled = prefs.getBoolean("email_$userId", true),
        pushEnabled = prefs.getBoolean("push_$userId", true)
    )

    override fun save(userId: Long, prefs: NotificationPreferences) {
        this.prefs.edit()
            .putBoolean("sms_$userId", prefs.smsEnabled)
            .putBoolean("email_$userId", prefs.emailEnabled)
            .putBoolean("push_$userId", prefs.pushEnabled)
            .apply()
    }
}
