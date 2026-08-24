package com.safevault.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * On-disk representation of a note.
 *
 * IMPORTANT: [encryptedContent] and [iv] hold the AES-256-GCM ciphertext and
 * its nonce respectively — no plaintext content is ever stored. The title and
 * tags are kept in plaintext to enable fast, index-friendly search; treat them
 * as non-secret metadata (users are guided not to put secrets in the title).
 */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "encrypted_content", typeAffinity = ColumnInfo.BLOB)
    val encryptedContent: ByteArray,

    @ColumnInfo(name = "iv", typeAffinity = ColumnInfo.BLOB)
    val iv: ByteArray,

    @ColumnInfo(name = "tags")
    val tags: List<String>,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NoteEntity) return false
        return id == other.id &&
            title == other.title &&
            encryptedContent.contentEquals(other.encryptedContent) &&
            iv.contentEquals(other.iv) &&
            tags == other.tags &&
            createdAt == other.createdAt &&
            updatedAt == other.updatedAt
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + encryptedContent.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + tags.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + updatedAt.hashCode()
        return result
    }
}
