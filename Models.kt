package com.phoneassistant.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

// ─── Contact (depuis le répertoire Android) ─────────────────────────────────
data class Contact(
    val id: Long,
    val name: String,
    val phoneNumbers: List<PhoneNumber>,
    val photoUri: String? = null,
    val isFavorite: Boolean = false
)

data class PhoneNumber(
    val number: String,
    val type: String
)

// ─── Appel de l'historique ───────────────────────────────────────────────────
data class CallLogEntry(
    val id: Long,
    val number: String,
    val name: String?,
    val resolvedInfo: String?,   // Info Caller ID si numéro inconnu
    val type: CallType,
    val duration: Long,          // secondes
    val timestamp: Long,
    val photoUri: String? = null
)

enum class CallType { INCOMING, OUTGOING, MISSED, REJECTED, BLOCKED }

// ─── Résultat API Caller ID ─────────────────────────────────────────────────
data class CallerInfo(
    val number: String,
    val carrier: String?,
    val lineType: String?,
    val location: String?,
    val isSpam: Boolean = false
)

// ─── Room Entities ────────────────────────────────────────────────────────────

@Entity(tableName = "blocked_numbers")
data class BlockedNumber(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: String,
    val normalizedNumber: String,
    val reason: String = "",
    val blockedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "contact_notes")
data class ContactNote(
    @PrimaryKey val contactId: Long,
    val note: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "favorites")
data class FavoriteContact(
    @PrimaryKey val contactId: Long,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "caller_id_cache")
data class CallerIdCache(
    @PrimaryKey val normalizedNumber: String,
    val carrier: String?,
    val lineType: String?,
    val location: String?,
    val isSpam: Boolean,
    val cachedAt: Long = System.currentTimeMillis()
)
