package com.jarvis.assistant.persona

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Jarvis's voice: how it greets and what short acknowledgements it uses. Pure logic. */
object Persona {
    enum class Daypart(val word: String) { MORNING("morning"), AFTERNOON("afternoon"), EVENING("evening") }

    fun daypart(hour: Int): Daypart = when (hour) {
        in 4..11 -> Daypart.MORNING
        in 12..16 -> Daypart.AFTERNOON
        else -> Daypart.EVENING
    }

    /** First wake of the day, when nothing has been asked yet. */
    fun greeting(hour: Int, language: String = "en"): String =
        if (language == "ru") when (daypart(hour)) {
            Daypart.MORNING -> "Доброе утро. Чем займёмся сегодня?"
            Daypart.AFTERNOON -> "Добрый день. Что нужно сделать?"
            Daypart.EVENING -> "Добрый вечер. Чем могу помочь?"
        } else "Hello sir, good ${daypart(hour).word}. What's the task for today?"

    /** First wake of the day, when a command came with it: the greeting is folded into the reply. */
    fun greetingPrefix(hour: Int, language: String = "en"): String =
        if (language == "ru") when (daypart(hour)) {
            Daypart.MORNING -> "Доброе утро."
            Daypart.AFTERNOON -> "Добрый день."
            Daypart.EVENING -> "Добрый вечер."
        } else "Good ${daypart(hour).word}, sir."

    /** All three greetings, so the voice can pre-render them and speak them with no delay. */
    val ALL_GREETINGS: List<String> = Daypart.entries.map { "Hello sir, good ${it.word}. What's the task for today?" }

    /** Short acknowledgements for slow requests. Never used for instant commands. */
    val ACKNOWLEDGEMENTS = listOf("One moment.", "Let me check.", "Working on it.", "Give me a second.")
    val RUSSIAN_ACKNOWLEDGEMENTS = listOf("Секунду.", "Сейчас проверю.", "Уже занимаюсь.", "Дай мне немного времени.")

    /** Picks an acknowledgement, never the same one twice in a row. */
    class AckPicker(private val random: java.util.Random = java.util.Random()) {
        private var last = -1

        fun next(language: String = "en"): String {
            val choices = if (language == "ru") RUSSIAN_ACKNOWLEDGEMENTS else ACKNOWLEDGEMENTS
            var i = random.nextInt(choices.size)
            if (i == last) i = (i + 1) % choices.size
            last = i
            return choices[i]
        }
    }
}

interface GreetingStore {
    /** The local date (yyyy-MM-dd) of the last greeting, or null if never. Must survive app restarts. */
    var lastDate: String?
}

/**
 * Greets once per local calendar day. The date is stored as text, so a change of time zone or clock simply means a
 * different date: a new day gets a greeting, the same date never gets two.
 */
class DailyGreeting(
    private val store: GreetingStore,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val now: () -> Instant = { Instant.now() },
) {
    fun today(): String = LocalDate.now(Clock.fixed(now(), zone())).toString()

    fun hour(): Int = now().atZone(zone()).hour

    fun isDue(): Boolean = store.lastDate != today()

    fun markGreeted() {
        store.lastDate = today()
    }

    /** The greeting to speak now, or null when today's greeting was already given. Marks it as given. */
    fun takeGreeting(language: String = "en"): String? = if (isDue()) Persona.greeting(hour(), language).also { markGreeted() } else null

    /** Same, for a reply that already carries the user's command. */
    fun takePrefix(language: String = "en"): String? = if (isDue()) Persona.greetingPrefix(hour(), language).also { markGreeted() } else null
}
