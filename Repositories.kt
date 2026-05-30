package com.phoneassistant.data.repository

import android.content.Context
import android.content.Intent
import android.provider.CallLog
import android.provider.ContactsContract
import com.phoneassistant.BuildConfig
import com.phoneassistant.data.api.ApiClient
import com.phoneassistant.data.api.toCallerInfo
import com.phoneassistant.data.db.AppDatabase
import com.phoneassistant.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ── Contacts ──────────────────────────────────────────────────────────────────
class ContactsRepository(private val ctx: Context) {

    suspend fun getAll(): List<Contact> = withContext(Dispatchers.IO) {
        val map = mutableMapOf<Long, Contact>()
        val cursor = ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.TYPE,
                ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
                ContactsContract.CommonDataKinds.Phone.STARRED
            ),
            null, null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY + " ASC"
        ) ?: return@withContext emptyList()

        cursor.use { c ->
            val idCol    = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameCol  = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
            val numCol   = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val typeCol  = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE)
            val photoCol = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
            val starCol  = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.STARRED)

            while (c.moveToNext()) {
                val id     = c.getLong(idCol)
                val name   = c.getString(nameCol) ?: "Inconnu"
                val num    = c.getString(numCol) ?: continue
                val type   = ContactsContract.CommonDataKinds.Phone
                    .getTypeLabel(ctx.resources, c.getInt(typeCol), "Autre").toString()
                val photo  = c.getString(photoCol)
                val star   = c.getInt(starCol) == 1

                val existing = map[id]
                map[id] = existing?.copy(phoneNumbers = existing.phoneNumbers + PhoneNumber(num, type))
                    ?: Contact(id, name, listOf(PhoneNumber(num, type)), photo, star)
            }
        }
        map.values.toList()
    }

    suspend fun search(query: String): List<Contact> {
        val q = query.lowercase()
        return getAll().filter { c ->
            c.name.lowercase().contains(q) ||
            c.phoneNumbers.any { it.number.contains(q) }
        }
    }

    suspend fun findByNumber(number: String): Contact? {
        val clean = number.replace(Regex("[^0-9+]"), "")
        return getAll().firstOrNull { c ->
            c.phoneNumbers.any { p ->
                p.number.replace(Regex("[^0-9+]"), "").endsWith(clean.takeLast(9)) ||
                clean.endsWith(p.number.replace(Regex("[^0-9+]"), "").takeLast(9))
            }
        }
    }
}

// ── Historique d'appels ───────────────────────────────────────────────────────
class CallLogRepository(private val ctx: Context) {

    suspend fun getAll(limit: Int = 150): List<CallLogEntry> = withContext(Dispatchers.IO) {
        val entries = mutableListOf<CallLogEntry>()
        val cursor = ctx.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls._ID,
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.TYPE,
                CallLog.Calls.DURATION,
                CallLog.Calls.DATE,
                CallLog.Calls.CACHED_PHOTO_URI
            ),
            null, null,
            "${CallLog.Calls.DATE} DESC LIMIT $limit"
        ) ?: return@withContext emptyList()

        cursor.use { c ->
            val idC    = c.getColumnIndexOrThrow(CallLog.Calls._ID)
            val numC   = c.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
            val nameC  = c.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
            val typeC  = c.getColumnIndexOrThrow(CallLog.Calls.TYPE)
            val durC   = c.getColumnIndexOrThrow(CallLog.Calls.DURATION)
            val dateC  = c.getColumnIndexOrThrow(CallLog.Calls.DATE)
            val photoC = c.getColumnIndexOrThrow(CallLog.Calls.CACHED_PHOTO_URI)

            while (c.moveToNext()) {
                val callType = when (c.getInt(typeC)) {
                    CallLog.Calls.INCOMING_TYPE -> CallType.INCOMING
                    CallLog.Calls.OUTGOING_TYPE -> CallType.OUTGOING
                    CallLog.Calls.MISSED_TYPE   -> CallType.MISSED
                    CallLog.Calls.REJECTED_TYPE -> CallType.REJECTED
                    CallLog.Calls.BLOCKED_TYPE  -> CallType.BLOCKED
                    else                        -> CallType.INCOMING
                }
                entries.add(CallLogEntry(
                    id = c.getLong(idC),
                    number = c.getString(numC) ?: "",
                    name = c.getString(nameC)?.takeIf { it.isNotBlank() },
                    resolvedInfo = null,
                    type = callType,
                    duration = c.getLong(durC),
                    timestamp = c.getLong(dateC),
                    photoUri = c.getString(photoC)
                ))
            }
        }
        entries
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        ctx.contentResolver.delete(CallLog.Calls.CONTENT_URI, "${CallLog.Calls._ID}=?", arrayOf(id.toString()))
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        ctx.contentResolver.delete(CallLog.Calls.CONTENT_URI, null, null)
    }
}

// ── Caller ID ─────────────────────────────────────────────────────────────────
class CallerIdRepository(private val ctx: Context) {

    private val cache = AppDatabase.get(ctx).callerIdCacheDao()

    suspend fun identify(number: String): CallerInfo? = withContext(Dispatchers.IO) {
        val normalized = number.replace(Regex("[^0-9+]"), "")

        // Cache local (30 jours)
        try {
            val cached = cache.get(normalized)
            if (cached != null) {
                val age = System.currentTimeMillis() - cached.cachedAt
                if (age < 30L * 86_400_000) {
                    return@withContext CallerInfo(number, cached.carrier, cached.lineType, cached.location, cached.isSpam)
                }
            }
        } catch (_: Exception) {}

        val apiKey = BuildConfig.NUMLOOKUP_API_KEY
        if (apiKey == "VOTRE_CLE_API_ICI" || apiKey.isBlank()) return@withContext null

        return@withContext try {
            val resp = ApiClient.api.validate(apiKey, normalized)
            val info = resp.toCallerInfo(number)
            cache.insert(CallerIdCache(normalized, info.carrier, info.lineType, info.location, info.isSpam))
            info
        } catch (_: Exception) { null }
    }
}

// ── Numéros bloqués ───────────────────────────────────────────────────────────
class BlockedRepo(private val ctx: Context) {
    private val dao = AppDatabase.get(ctx).blockedNumberDao()

    suspend fun isBlocked(number: String): Boolean {
        val n = number.replace(Regex("[^0-9+]"), "")
        return dao.findByNumber(n) != null
    }
    suspend fun getAll() = dao.getAll()
    suspend fun block(number: String, reason: String = "") {
        dao.insert(BlockedNumber(number = number, normalizedNumber = number.replace(Regex("[^0-9+]"), ""), reason = reason))
    }
    suspend fun unblock(number: String) = dao.deleteByNumber(number.replace(Regex("[^0-9+]"), ""))
}
