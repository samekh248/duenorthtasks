package app.duenorth.tasks.data.order

import app.duenorth.tasks.data.db.TaskEntity
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

/**
 * Sortable order keys for tasks (specs/003-reordering, plan "Task order key").
 *
 * A key is a string of digits read as a decimal fraction, so "05" < "055" < "06" and plain string
 * order is the right order. Google's `position` values are fixed-width digit strings, so keys made
 * here sort correctly among them. [between] always finds room between two different keys without
 * touching any other row, and never ends a key in 0, so there is always room again later.
 */
object OrderKeys {
    /**
     * A key strictly after [after] and strictly before [before]; null [after] means the start,
     * null [before] the end. Returns null when there is no room, which only happens when the two
     * are equal as fractions ("05" and "050") or [before] is all zeros.
     */
    fun between(after: String?, before: String?): String? {
        val a = after.orEmpty()
        require(a.all { it.isDigit() } && (before == null || before.all { it.isDigit() })) { "Keys are digits" }
        if (before != null && a >= before) return null
        val key = StringBuilder()
        var i = 0
        var upper = before
        while (true) {
            val da = if (i < a.length) a[i] - '0' else 0
            val db = when {
                upper == null -> 10
                i < upper.length -> upper[i] - '0'
                else -> 0
            }
            if (db - da > 1) {
                key.append('0' + (da + db) / 2)
                return key.toString()
            }
            key.append('0' + da)
            // From here the key is already below [before]: only [after] still bounds it.
            if (db - da == 1) upper = null
            i++
            if (upper != null && i >= a.length && i >= upper.length) return null
        }
    }

    /**
     * [count] keys in ascending order, all before [first] (null: no upper bound). For giving
     * tasks that have none a key while keeping them above the keyed ones, in their current order.
     */
    fun keysBefore(first: String?, count: Int): List<String> {
        if (count == 0) return emptyList()
        // Evenly spaced, so a few hundred keys stay a few digits long.
        val end = if (first == null) BigDecimal.ONE else BigDecimal("0.$first")
        require(end.signum() > 0) { "No room before $first" }
        val scale = (first?.length ?: 0) + count.toString().length + 2
        return (1..count).map { i ->
            val value = end.multiply(BigDecimal(i)).divide(BigDecimal(count + 1), scale, RoundingMode.DOWN)
            value.stripTrailingZeros().toPlainString().removePrefix("0.")
        }
    }

    /** Newest first: a later [at] gives a smaller key. Used to seed services that keep no order. */
    fun timeKey(at: Instant): String = (TIME_BASE - at.toEpochMilli().coerceIn(0, TIME_BASE)).toString().padStart(13, '0')

    /** "My order": tasks with no key first (newest edit first), then by key. */
    val taskComparator: Comparator<TaskEntity> =
        compareBy<TaskEntity, String?>(nullsFirst()) { it.position }.thenByDescending { it.localUpdatedAt }

    private const val TIME_BASE = 9_999_999_999_999L
}
