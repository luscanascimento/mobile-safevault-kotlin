package com.safevault.app.util

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import androidx.core.content.getSystemService

/**
 * Copies a secret to the clipboard and schedules it to be wiped shortly after,
 * limiting the window in which another app could read it.
 *
 * On Android 13+ the clip is also flagged as sensitive so the OS hides the
 * value from the clipboard-preview toast.
 */
object SecureClipboard {

    /** How long (ms) a secret is allowed to remain on the clipboard. */
    const val CLEAR_DELAY_MS = 30_000L

    private val mainHandler = Handler(Looper.getMainLooper())

    fun copySensitive(context: Context, label: String, secret: String) {
        val clipboard = context.getSystemService<ClipboardManager>() ?: return
        val clip = ClipData.newPlainText(label, secret).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                description.extras = PersistableBundle().apply {
                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                }
            }
        }
        clipboard.setPrimaryClip(clip)

        mainHandler.postDelayed({ clearIfUnchanged(clipboard, secret) }, CLEAR_DELAY_MS)
    }

    /**
     * Clears the clipboard only if it still holds the secret we set, so we do
     * not wipe something the user copied afterwards.
     */
    private fun clearIfUnchanged(clipboard: ClipboardManager, secret: String) {
        val current = clipboard.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.text
            ?.toString()

        if (current == secret) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                clipboard.clearPrimaryClip()
            } else {
                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
    }
}
