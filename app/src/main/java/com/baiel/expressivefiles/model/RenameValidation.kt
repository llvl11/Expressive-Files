package com.baiel.expressivefiles.model

/**
 * Why a candidate rename is rejected. Every case is detected client-side,
 * live, as the user types; the repository's typed failures remain as the
 * server-side safety net for races (e.g. a file appearing in the directory
 * between the dialog opening and the confirm tap).
 */
enum class RenameRejection {
    /** Trimmed name is blank. */
    EMPTY,

    /** Contains '/', '\' or a NUL character - exactly what the repository refuses. */
    INVALID_CHARACTERS,

    /** "." or ".." - the two reserved directory names. */
    RESERVED_NAME,

    /** Identical to the current name (case-sensitive): nothing to do. */
    UNCHANGED,

    /** Another entry in the same directory already owns this name. */
    NAME_TAKEN
}

/**
 * Live verdict for the rename dialog's text field.
 *
 * @param rejection null when the name can be confirmed right away.
 * @param trimmedName whitespace-trimmed name to hand to the repository.
 * @param extensionChanged true when a FILE's extension would change
 *   ("photo.jpg" -> "photo.png"). Deliberately not a rejection - Windows and
 *   every major file manager allow it with a warning - but the dialog
 *   surfaces it so the change is never accidental.
 */
data class RenameValidation(
    val rejection: RenameRejection?,
    val trimmedName: String,
    val extensionChanged: Boolean
)

/**
 * Lowercase extension after the last dot ("" when there is none).
 *
 * Deliberately the SAME rule as kotlin.io.File.extension
 * (substringAfterLast('.')) - which is what every type and MIME decision in
 * the app goes through. The old `dot > 0` variant treated a leading dot as
 * "no extension", so a pure dotfile got two answers: getMimeType/determineFileType
 * saw ".zip" as a zip while the rename dialog saw no extension, and the
 * "extension changed" warning fired (or stayed silent) against the type the
 * file list was actually showing.
 *
 * The dialog's basename pre-selection intentionally keeps its own `dot > 0`
 * rule (RenameDialog): selecting nothing for a dotfile would force the user to
 * erase the whole name by hand.
 */
fun fileExtensionOf(name: String): String = name.substringAfterLast('.', "").lowercase()

/**
 * Validates a candidate rename against the current name and a snapshot of
 * the sibling names in the same directory (lowercase, target excluded by the
 * caller). Rules, in priority order:
 *
 *  1. blank after trim            -> EMPTY
 *  2. '/', '\', NUL anywhere      -> INVALID_CHARACTERS
 *  3. "." or ".."                 -> RESERVED_NAME
 *  4. equal to the current name   -> UNCHANGED (case-sensitive, so a
 *                                    case-only rename passes - media
 *                                    filesystems are case-insensitive and the
 *                                    repository handles those via a temp hop)
 *  5. owned by another sibling    -> NAME_TAKEN (case-insensitive, matching
 *                                    the repository's isSameFile/exists check)
 *
 * Trailing/leading whitespace is silently trimmed rather than rejected, the
 * way desktop file managers behave.
 */
fun validateNewName(
    rawName: String,
    currentName: String,
    isDirectory: Boolean,
    siblingNamesLowercase: Set<String>
): RenameValidation {
    val trimmed = rawName.trim()
    val rejection = when {
        trimmed.isEmpty() -> RenameRejection.EMPTY
        trimmed.any { it == '/' || it == '\\' || it == '\u0000' } ->
            RenameRejection.INVALID_CHARACTERS
        trimmed == "." || trimmed == ".." -> RenameRejection.RESERVED_NAME
        trimmed == currentName -> RenameRejection.UNCHANGED
        trimmed.lowercase() in siblingNamesLowercase -> RenameRejection.NAME_TAKEN
        else -> null
    }
    return RenameValidation(
        rejection = rejection,
        trimmedName = trimmed,
        extensionChanged = rejection == null && !isDirectory &&
            fileExtensionOf(trimmed) != fileExtensionOf(currentName)
    )
}
