package com.example.mobile.util

import android.content.Context
import android.provider.ContactsContract

data class DeviceContactKey(
    val typeLabel: String,
    val value: String
)

data class DeviceContact(
    val name: String,
    val keys: List<DeviceContactKey>
)

object DeviceContactsHelper {

    fun load(context: Context): List<DeviceContact> {
        val resolver = context.contentResolver
        val contactsById = linkedMapOf<String, Pair<String, LinkedHashSet<DeviceContactKey>>>()

        resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null,
            null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                val id = cursor.getString(idIdx) ?: continue
                val name = cursor.getString(nameIdx)?.trim().orEmpty()
                val rawPhone = cursor.getString(numIdx).orEmpty()
                val normalized = normalizePhone(rawPhone)
                if (name.isNotEmpty() && normalized.length >= 12) {
                    val entry = contactsById.getOrPut(id) { name to linkedSetOf() }
                    entry.second += DeviceContactKey(typeLabel = "Celular", value = normalized)
                }
            }
        }

        resolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Email.CONTACT_ID,
                ContactsContract.CommonDataKinds.Email.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Email.ADDRESS
            ),
            null,
            null,
            "${ContactsContract.CommonDataKinds.Email.DISPLAY_NAME} ASC"
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.CONTACT_ID)
            val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.DISPLAY_NAME)
            val emailIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
            while (cursor.moveToNext()) {
                val id = cursor.getString(idIdx) ?: continue
                val name = cursor.getString(nameIdx)?.trim().orEmpty()
                val email = cursor.getString(emailIdx)?.trim()?.lowercase().orEmpty()
                if (name.isNotEmpty() && email.contains("@")) {
                    val entry = contactsById.getOrPut(id) { name to linkedSetOf() }
                    entry.second += DeviceContactKey(typeLabel = "E-mail", value = email)
                }
            }
        }

        return contactsById.values
            .map { (name, keys) -> DeviceContact(name = name, keys = keys.toList()) }
            .filter { it.keys.isNotEmpty() }
            .sortedBy { it.name.lowercase() }
    }
}
