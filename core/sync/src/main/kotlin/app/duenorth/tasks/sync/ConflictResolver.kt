package app.duenorth.tasks.sync

import java.time.Instant

/**
 * The conflict rule (research R9, FR-023): the remote copy wins unless the local entity has a
 * pending change newer than the remote `updated` stamp. Ties go to the remote, the source of truth.
 */
object ConflictResolver {
    enum class Decision {
        /** Nothing pending here: take the remote copy. */
        APPLY_REMOTE,

        /** Pending here and the remote has not changed since we last saw it: keep ours and push it. */
        KEEP_LOCAL,

        /** Both changed and the remote is newer: take the remote, log ours as the loser. */
        REMOTE_WINS,

        /** Both changed and ours is newer: keep ours and push it, log the remote as the loser. */
        LOCAL_WINS
    }

    fun decide(
        hasPendingLocalChange: Boolean,
        localUpdatedAt: Instant,
        lastSeenRemoteUpdatedAt: Instant?,
        remoteUpdatedAt: Instant
    ): Decision = when {
        !hasPendingLocalChange -> Decision.APPLY_REMOTE
        lastSeenRemoteUpdatedAt != null && remoteUpdatedAt == lastSeenRemoteUpdatedAt -> Decision.KEEP_LOCAL
        localUpdatedAt.isAfter(remoteUpdatedAt) -> Decision.LOCAL_WINS
        else -> Decision.REMOTE_WINS
    }
}
