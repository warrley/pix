package com.example.mobile.network

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class RecentPixContact(
    val name: String,
    val pixKey: String
)

interface RecentPixContactsStore {
    fun record(accountId: Long, name: String, pixKey: String)
    fun keyForName(accountId: Long, name: String): String?
    fun list(accountId: Long): List<RecentPixContact>
}

class InMemoryRecentPixContactsStore : RecentPixContactsStore {
    private val byAccount = mutableMapOf<Long, MutableList<RecentPixContact>>()

    override fun record(accountId: Long, name: String, pixKey: String) {
        val cleanName = name.trim()
        val cleanKey = pixKey.trim()
        if (cleanName.isEmpty() || cleanKey.isEmpty()) return
        val list = byAccount.getOrPut(accountId) { mutableListOf() }
        list.removeAll { it.pixKey.equals(cleanKey, ignoreCase = true) }
        list.add(0, RecentPixContact(name = cleanName, pixKey = cleanKey))
        if (list.size > 20) list.removeAt(list.lastIndex)
    }

    override fun keyForName(accountId: Long, name: String): String? {
        val clean = name.trim()
        return byAccount[accountId]?.firstOrNull { it.name.equals(clean, ignoreCase = true) }?.pixKey
    }

    override fun list(accountId: Long): List<RecentPixContact> =
        byAccount[accountId]?.toList().orEmpty()
}

class SharedPreferencesRecentPixContactsStore(context: Context) : RecentPixContactsStore {
    private val prefs = context.applicationContext.getSharedPreferences("recent_pix_contacts", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    override fun record(accountId: Long, name: String, pixKey: String) {
        val cleanName = name.trim()
        val cleanKey = pixKey.trim()
        if (cleanName.isEmpty() || cleanKey.isEmpty()) return

        val updated = list(accountId)
            .filterNot { it.pixKey.equals(cleanKey, ignoreCase = true) }
            .toMutableList()
        updated.add(0, RecentPixContact(name = cleanName, pixKey = cleanKey))
        val trimmed = updated.take(20)
        prefs.edit().putString(key(accountId), json.encodeToString(trimmed)).apply()
    }

    override fun keyForName(accountId: Long, name: String): String? {
        val clean = name.trim()
        return list(accountId).firstOrNull { it.name.equals(clean, ignoreCase = true) }?.pixKey
    }

    override fun list(accountId: Long): List<RecentPixContact> {
        val raw = prefs.getString(key(accountId), null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<RecentPixContact>>(raw) }.getOrDefault(emptyList())
    }

    private fun key(accountId: Long) = "account_$accountId"
}
