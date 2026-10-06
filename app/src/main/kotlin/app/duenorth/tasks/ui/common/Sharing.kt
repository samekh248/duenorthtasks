package app.duenorth.tasks.ui.common

import app.duenorth.tasks.data.db.AssignmentSource

/**
 * Whether a list is shared, and by whom (spec 002). Microsoft To Do says only "shared" and "you
 * own it"; it never says who else is in a list, so neither does the app.
 */
enum class ListSharing(
    /** How captions and the list page start: "shared · next: ...". Empty when not shared. */
    val caption: String,
    /** Read by TalkBack after the list's name. */
    val spoken: String
) {
    PRIVATE("", ""),

    /** Shared by the signed-in user, who owns it. */
    OWNED("shared", "shared by you"),

    /** Someone else's list, shared with the signed-in user. They can't rename or delete it. */
    WITH_YOU("shared with you", "shared with you");

    val isShared: Boolean get() = this != PRIVATE

    /** Only the owner can rename or delete a list. */
    val canManage: Boolean get() = this != WITH_YOU

    companion object {
        fun of(isShared: Boolean, isOwner: Boolean): ListSharing = when {
            !isShared -> PRIVATE
            isOwner -> OWNED
            else -> WITH_YOU
        }
    }
}

/** The message under "delete <list>?": a shared list goes for everyone in it. */
fun deleteListMessage(title: String, openCount: Int, sharing: ListSharing, serviceName: String): String =
    if (sharing == ListSharing.OWNED) {
        "This deletes $title and its $openCount open tasks for everyone it's shared with, here and in $serviceName."
    } else {
        "This deletes the list and its $openCount open tasks here and in $serviceName."
    }

/** The message under "clear completed?": how many go, where, and that it can't be undone. */
fun clearCompletedMessage(count: Int, sharing: ListSharing, serviceName: String): String {
    val tasks = if (count == 1) "the 1 completed task" else "the $count completed tasks"
    val where = if (serviceName.isEmpty()) "here" else "here and in $serviceName"
    val who = if (sharing.isShared) " for everyone in this list," else ""
    return "This deletes $tasks$who $where. You can't undo it."
}

/** Dialog title for deleting a list: "delete for everyone?" when it's shared. */
fun deleteListTitle(title: String, sharing: ListSharing): String =
    if (sharing == ListSharing.OWNED) "delete for everyone?" else "delete $title?"

/**
 * "assigned from a google doc" for a task someone assigned to the user in Google Docs or Chat
 * (spec 002 US4); null for everything else. Google doesn't say who assigned it, so neither do we.
 */
fun assignedFrom(source: AssignmentSource): String? = when (source) {
    AssignmentSource.NONE -> null
    AssignmentSource.DOCUMENT -> "assigned from a google doc"
    AssignmentSource.SPACE -> "assigned from a chat space"
    AssignmentSource.OTHER -> "assigned to you"
}
