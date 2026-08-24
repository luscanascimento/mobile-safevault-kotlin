package com.safevault.app.ui.navigation

/** Type-safe-ish route definitions for the Navigation-Compose graph. */
object Routes {
    const val LOCK = "lock"
    const val VAULT = "vault"
    const val SETTINGS = "settings"

    const val EDITOR = "editor"
    const val EDITOR_ARG_ID = "noteId"

    /** Route pattern for the editor; -1 means "new note". */
    const val EDITOR_PATTERN = "$EDITOR?$EDITOR_ARG_ID={$EDITOR_ARG_ID}"

    fun editor(noteId: Long? = null): String =
        if (noteId == null) EDITOR else "$EDITOR?$EDITOR_ARG_ID=$noteId"
}
