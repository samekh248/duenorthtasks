package app.duenorth.tasks.ui.templates

import app.duenorth.tasks.data.repo.TemplateRepository

/** Words for due offsets and counts on the template pages (specs/004-templates). */
internal object TemplateText {
    /** A list template task's offset: "3 days before start", "on the start day", "2 days after start". */
    fun fromStart(days: Int): String = when {
        days == 0 -> "on the start day"
        days < 0 -> "${days(-days)} before start"
        else -> "${days(days)} after start"
    }

    /** A task template's offset from the day it is used: "due today", "due in 3 days". */
    fun fromToday(days: Int): String = when (days) {
        0 -> "due today"
        1 -> "due tomorrow"
        else -> "due in ${days(days)}"
    }

    fun offset(days: Int?, inList: Boolean): String? = days?.let { if (inList) fromStart(it) else fromToday(it) }

    /** "3 steps", "1 step", or null for none. */
    fun steps(count: Int): String? = when (count) {
        0 -> null
        1 -> "1 step"
        else -> "$count steps"
    }

    fun tasks(count: Int): String = if (count == 1) "1 task" else "$count tasks"

    /** The rows' grey caption: due offset, then details and steps, joined by " · ". */
    fun caption(offset: String?, hasDetails: Boolean, stepCount: Int, important: Boolean): String? =
        listOfNotNull(offset, "details".takeIf { hasDetails }, steps(stepCount), "important".takeIf { important })
            .joinToString(" · ")
            .ifEmpty { null }

    /**
     * The choices in the due picker: none, then the start day and every day within a month either
     * way for list templates, or today and the next two months for task templates.
     */
    fun choices(inList: Boolean): List<Int?> =
        listOf<Int?>(null) + if (inList) (-PICKER_DAYS..PICKER_DAYS).toList() else (0..PICKER_DAYS * 2).toList()

    fun choiceLabel(days: Int?, inList: Boolean): String = offset(days, inList) ?: "no due date"

    private fun days(n: Int) = if (n == 1) "1 day" else "$n days"

    private const val PICKER_DAYS = 31

    init {
        check(PICKER_DAYS * 2 <= TemplateRepository.MAX_OFFSET)
    }
}
