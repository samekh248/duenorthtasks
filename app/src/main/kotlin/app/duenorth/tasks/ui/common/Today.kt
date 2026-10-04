package app.duenorth.tasks.ui.common

import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

/** Today's date, emitted again just after each midnight so "today" rolls over on its own. */
fun todayFlow(clock: Clock): Flow<LocalDate> = flow {
    while (true) {
        val now = LocalDateTime.now(clock)
        emit(now.toLocalDate())
        val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay()
        delay(Duration.between(now, nextMidnight).toMillis() + 1_000)
    }
}.distinctUntilChanged()
