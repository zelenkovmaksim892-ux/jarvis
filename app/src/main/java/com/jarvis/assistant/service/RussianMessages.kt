package com.jarvis.assistant.service

/**
 * Russian wording for short, deterministic phone-action results.
 * Long and open-ended replies come from the language-aware agent prompt.
 */
internal object RussianMessages {
    private val exact = mapOf(
        "Android didn't allow that." to "Android не разрешил выполнить это действие.",
        "Sorry, that didn't work." to "Не получилось выполнить действие.",
        "I couldn't scroll." to "Не удалось прокрутить экран.",
        "Scrolled down." to "Прокрутил экран вниз.",
        "Scrolled up." to "Прокрутил экран вверх.",
        "The dialer is open with that number. Tap call to place it." to "Номер открыт в приложении телефона. Нажми кнопку вызова, чтобы позвонить.",
        "Typed it." to "Текст введён.",
        "Taking a screenshot." to "Делаю снимок экрана.",
        "I couldn't take a screenshot." to "Не удалось сделать снимок экрана.",
        "Locking the screen." to "Блокирую экран.",
        "I couldn't lock the screen." to "Не удалось заблокировать экран.",
        "Going back." to "Возвращаюсь назад.",
        "Notifications." to "Открываю уведомления.",
        "Quick settings." to "Открываю быстрые настройки.",
        "Done." to "Готово.",
        "Please turn on phone control in the Jarvis app first." to "Сначала включи управление телефоном в настройках Jarvis.",
        "Muted." to "Звук выключен.",
        "Flashlight on." to "Фонарик включён.",
        "Flashlight off." to "Фонарик выключен.",
        "Nothing is playing right now." to "Сейчас ничего не воспроизводится.",
        "Paused." to "Воспроизведение приостановлено.",
        "Playing." to "Воспроизведение запущено.",
        "Next track." to "Следующий трек.",
        "Previous track." to "Предыдущий трек.",
        "I couldn't start the search." to "Не удалось запустить поиск.",
        "I couldn't open settings." to "Не удалось открыть настройки.",
        "Okay. English with offline recognition." to "Хорошо. Переключаю распознавание и ответы на английский язык.",
        "Okay. I'll listen for Hindi and Hinglish with Google speech recognition, and answer in English." to "Хорошо. Буду распознавать хинди через службу Google и отвечать на английском.",
        "Something went wrong." to "Произошла ошибка. Команду выполнить не удалось.",
        "Action denied." to "Действие запрещено.",
        "I couldn't save that." to "Не удалось сохранить.",
    )

    fun translate(message: String, success: Boolean): String {
        exact[message]?.let { return it }
        Regex("""^Opening (.+) settings\.$""").matchEntire(message)?.let {
            return "Открываю настройки: ${it.groupValues[1]}."
        }
        Regex("""^Opened (.+)\.$""").matchEntire(message)?.let {
            return "Открыл приложение ${it.groupValues[1]}."
        }
        Regex("""^Opening (.+)\.$""").matchEntire(message)?.let {
            return "Открываю ${it.groupValues[1]}."
        }
        Regex("""^I couldn't open (.+)\.$""").matchEntire(message)?.let {
            return "Не удалось открыть ${it.groupValues[1]}."
        }
        Regex("""^Searching Google for (.+)\.$""").matchEntire(message)?.let {
            return "Ищу в Google: ${it.groupValues[1]}."
        }
        Regex("""^Calling (.+)\.$""").matchEntire(message)?.let {
            return "Звоню: ${it.groupValues[1]}."
        }
        Regex("""^Dialing (.+)\.$""").matchEntire(message)?.let {
            return "Набираю номер ${it.groupValues[1]}."
        }
        Regex("""^Timer set for (.+)\.$""").matchEntire(message)?.let {
            return "Таймер установлен на ${it.groupValues[1]}."
        }
        Regex("""^Alarm set for (.+)\.$""").matchEntire(message)?.let {
            return "Будильник установлен на ${it.groupValues[1]}."
        }
        Regex("""^Battery is at (\d+) percent( and charging)?\.$""").matchEntire(message)?.let {
            return "Заряд батареи ${it.groupValues[1]} процентов${if (it.groupValues[2].isNotEmpty()) ", телефон заряжается" else ""}."
        }
        Regex("""^Volume is (\d+) percent\.$""").matchEntire(message)?.let {
            return "Громкость установлена на ${it.groupValues[1]} процентов."
        }
        return if (message.any { it in 'А'..'я' || it == 'Ё' || it == 'ё' }) message
        else if (success) "Готово." else "Не удалось выполнить команду."
    }
}
