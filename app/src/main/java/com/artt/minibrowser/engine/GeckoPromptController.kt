package com.artt.minibrowser.engine

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.LinearLayout
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import android.widget.TimePicker
import com.artt.minibrowser.BuildConfig
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Small native prompt bridge; it deliberately denies unattended popup/auth actions. */
class GeckoPromptController(
    private val activity: Activity,
    private val pickFiles: ((Int, Array<String>, (Array<Uri>) -> Unit) -> Unit)? = null,
) : GeckoSession.PromptDelegate {
    private class PromptGuard<T>(
        private val prompt: GeckoSession.PromptDelegate.BasePrompt,
        private val result: GeckoResult<T>,
    ) {
        private var completed = false

        fun complete(action: () -> T) {
            if (completed || prompt.isComplete) return
            try {
                result.complete(action())
                completed = true
            } catch (_: RuntimeException) {
                completed = true
            }
        }
    }

    private fun bindPromptDialog(
        prompt: GeckoSession.PromptDelegate.BasePrompt,
        dialog: Dialog,
    ) {
        prompt.setDelegate(object : GeckoSession.PromptDelegate.PromptInstanceDelegate {
            override fun onPromptDismiss(prompt: GeckoSession.PromptDelegate.BasePrompt) {
                activity.runOnUiThread {
                    if (dialog.isShowing) dialog.dismiss()
                }
            }
        })
    }

    private fun isPromptOpen(prompt: GeckoSession.PromptDelegate.BasePrompt): Boolean = !prompt.isComplete

    override fun onAlertPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.AlertPrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> =
        dialog(prompt, prompt.title.orEmpty(), prompt.message.orEmpty(), { prompt.dismiss() }, { prompt.dismiss() })

    override fun onTextPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.TextPrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
        val guard = PromptGuard(prompt, result)
        activity.runOnUiThread {
            if (!isPromptOpen(prompt)) return@runOnUiThread
            val input = EditText(activity).apply {
                setText(prompt.defaultValue)
                inputType = InputType.TYPE_CLASS_TEXT
            }
            val dialog = AlertDialog.Builder(activity)
                .setTitle(prompt.title.orEmpty())
                .setMessage(prompt.message.orEmpty())
                .setView(input)
                .setNegativeButton("Отмена") { _, _ -> guard.complete { prompt.dismiss() } }
                .setPositiveButton("ОК") { _, _ -> guard.complete { prompt.confirm(input.text.toString()) } }
                .setOnCancelListener { guard.complete { prompt.dismiss() } }
                .create()
            bindPromptDialog(prompt, dialog)
            dialog.show()
        }
        return result
    }

    override fun onButtonPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.ButtonPrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> =
        dialog(prompt, prompt.title.orEmpty(), prompt.message.orEmpty(), { prompt.confirm(GeckoSession.PromptDelegate.ButtonPrompt.Type.POSITIVE) }, { prompt.dismiss() })

    override fun onAuthPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.AuthPrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
        val guard = PromptGuard(prompt, result)
        activity.runOnUiThread {
            if (!isPromptOpen(prompt)) return@runOnUiThread
            val user = EditText(activity).apply {
                hint = "Имя пользователя"
                setText(prompt.authOptions.username)
            }
            val password = EditText(activity).apply {
                hint = "Пароль"
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                setText(prompt.authOptions.password)
            }
            val fields = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(user)
                addView(password)
            }
            val dialog = AlertDialog.Builder(activity)
                .setTitle(prompt.title.orEmpty())
                .setMessage(prompt.message.orEmpty())
                .setView(fields)
                .setNegativeButton("Отмена") { _, _ -> guard.complete { prompt.dismiss() } }
                .setPositiveButton("Войти") { _, _ -> guard.complete { prompt.confirm(user.text.toString(), password.text.toString()) } }
                .setOnCancelListener { guard.complete { prompt.dismiss() } }
                .create()
            bindPromptDialog(prompt, dialog)
            dialog.show()
        }
        return result
    }

    override fun onChoicePrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.ChoicePrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
        val guard = PromptGuard(prompt, result)
        val choices = prompt.choices.orEmpty().filterNot { it.disabled || it.separator }
        if (choices.isEmpty()) return GeckoResult.fromValue(prompt.dismiss())
        activity.runOnUiThread {
            if (!isPromptOpen(prompt)) return@runOnUiThread
            val builder = AlertDialog.Builder(activity).setTitle(prompt.title.orEmpty())
            if (prompt.type == GeckoSession.PromptDelegate.ChoicePrompt.Type.MULTIPLE) {
                val selected = choices.map { it.selected }.toBooleanArray()
                builder.setMultiChoiceItems(choices.map { it.label }.toTypedArray(), selected) { _, index, checked ->
                    selected[index] = checked
                }.setNegativeButton("Отмена") { _, _ -> guard.complete { prompt.dismiss() } }
                    .setPositiveButton("ОК") { _, _ ->
                        guard.complete { prompt.confirm(choices.filterIndexed { index, _ -> selected[index] }.toTypedArray()) }
                    }
            } else {
                builder.setItems(choices.map { it.label }.toTypedArray()) { _, index ->
                    guard.complete { prompt.confirm(choices[index]) }
                }
            }
            val dialog = builder.setOnCancelListener { guard.complete { prompt.dismiss() } }.create()
            bindPromptDialog(prompt, dialog)
            dialog.show()
        }
        return result
    }

    override fun onBeforeUnloadPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.BeforeUnloadPrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> =
        dialog(prompt, prompt.title.orEmpty(), "Покинуть страницу?", { prompt.confirm(AllowOrDeny.ALLOW) }, { prompt.confirm(AllowOrDeny.DENY) })

    override fun onRepostConfirmPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.RepostConfirmPrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> =
        dialog(
            prompt,
            "Отправить данные повторно?",
            "Для обновления страницы нужно повторно отправить данные формы.",
            { prompt.confirm(AllowOrDeny.ALLOW) },
            { prompt.confirm(AllowOrDeny.DENY) },
        )

    override fun onFolderUploadPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.FolderUploadPrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        val folder = prompt.directoryName?.takeIf { it.isNotBlank() }
        val message = if (folder == null) {
            "Сайт получит файлы из выбранной папки."
        } else {
            "Сайт получит файлы из папки «$folder»."
        }
        return dialog(prompt, "Разрешить загрузку папки?", message, { prompt.confirm(AllowOrDeny.ALLOW) }, { prompt.confirm(AllowOrDeny.DENY) })
    }

    override fun onRedirectPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.RedirectPrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        val host = runCatching { Uri.parse(prompt.targetUri).host }.getOrNull().orEmpty()
        val message = if (host.isBlank()) "Разрешить перенаправление?" else "Разрешить перенаправление на $host?"
        return dialog(prompt, "Разрешить перенаправление?", message, { prompt.confirm(AllowOrDeny.ALLOW) }, { prompt.confirm(AllowOrDeny.DENY) })
    }

    override fun onSharePrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.SharePrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
        val guard = PromptGuard(prompt, result)
        activity.runOnUiThread {
            if (!isPromptOpen(prompt)) return@runOnUiThread
            val text = listOf(prompt.text, prompt.uri).filter { !it.isNullOrBlank() }.joinToString("\n")
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            try {
                if (share.resolveActivity(activity.packageManager) == null) {
                    guard.complete { prompt.confirm(GeckoSession.PromptDelegate.SharePrompt.Result.FAILURE) }
                } else {
                    activity.startActivity(Intent.createChooser(share, "Поделиться"))
                    guard.complete { prompt.dismiss() }
                }
            } catch (_: Exception) {
                guard.complete { prompt.confirm(GeckoSession.PromptDelegate.SharePrompt.Result.FAILURE) }
            }
        }
        return result
    }

    override fun onDateTimePrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.DateTimePrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
        val guard = PromptGuard(prompt, result)
        activity.runOnUiThread {
            if (isPromptOpen(prompt)) showDateTimePrompt(prompt, guard)
        }
        return result
    }

    override fun onColorPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.ColorPrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
        val guard = PromptGuard(prompt, result)
        activity.runOnUiThread {
            if (!isPromptOpen(prompt)) return@runOnUiThread
            val input = EditText(activity).apply {
                setText((prompt.defaultValue ?: "").takeIf { it.matches(Regex("^#[0-9A-Fa-f]{6}$")) } ?: "#000000")
                inputType = InputType.TYPE_CLASS_TEXT
            }
            val dialog = AlertDialog.Builder(activity)
                .setTitle(prompt.title.orEmpty())
                .setView(input)
                .setNegativeButton("Отмена") { _, _ -> guard.complete { prompt.dismiss() } }
                .setPositiveButton("ОК", null)
                .setOnCancelListener { guard.complete { prompt.dismiss() } }
                .create()
            bindPromptDialog(prompt, dialog)
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val value = input.text.toString()
                    if (value.matches(Regex("^#[0-9A-Fa-f]{6}$"))) {
                        guard.complete { prompt.confirm(value) }
                        dialog.dismiss()
                    } else {
                        input.error = "Введите цвет в формате #RRGGBB"
                    }
                }
            }
            dialog.show()
        }
        return result
    }

    private fun showDateTimePrompt(
        prompt: GeckoSession.PromptDelegate.DateTimePrompt,
        guard: PromptGuard<GeckoSession.PromptDelegate.PromptResponse>,
    ) {
        when (prompt.type) {
            GeckoSession.PromptDelegate.DateTimePrompt.Type.TIME -> showTimePicker(prompt, guard)
            GeckoSession.PromptDelegate.DateTimePrompt.Type.DATE,
            GeckoSession.PromptDelegate.DateTimePrompt.Type.MONTH,
            GeckoSession.PromptDelegate.DateTimePrompt.Type.WEEK,
            GeckoSession.PromptDelegate.DateTimePrompt.Type.DATETIME_LOCAL -> showDatePicker(prompt, guard)
            else -> guard.complete { prompt.dismiss() }
        }
    }

    private fun showDatePicker(
        prompt: GeckoSession.PromptDelegate.DateTimePrompt,
        guard: PromptGuard<GeckoSession.PromptDelegate.PromptResponse>,
    ) {
        val defaultDate = parsePromptDate(prompt.type, prompt.defaultValue) ?: LocalDate.now()
        val dialog = DatePickerDialog(activity, { _, year, month, day ->
            val date = LocalDate.of(year, month + 1, day)
            if (!dateWithinDateRange(prompt, date)) {
                Toast.makeText(activity, "Дата вне допустимого диапазона", Toast.LENGTH_SHORT).show()
                return@DatePickerDialog
            }
            if (prompt.type == GeckoSession.PromptDelegate.DateTimePrompt.Type.DATETIME_LOCAL) {
                showTimePicker(prompt, guard, date)
            } else {
                val value = when (prompt.type) {
                    GeckoSession.PromptDelegate.DateTimePrompt.Type.MONTH -> formatMonthValue(date)
                    GeckoSession.PromptDelegate.DateTimePrompt.Type.WEEK -> formatIsoWeekValue(date)
                    else -> formatDateValue(date)
                }
                guard.complete { prompt.confirm(value) }
            }
        }, defaultDate.year, defaultDate.monthValue - 1, defaultDate.dayOfMonth)
        promptDateBoundary(prompt, prompt.minValue, upper = false)?.let {
            dialog.datePicker.minDate = it.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        promptDateBoundary(prompt, prompt.maxValue, upper = true)?.let {
            dialog.datePicker.maxDate = it.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        dialog.setOnCancelListener { guard.complete { prompt.dismiss() } }
        bindPromptDialog(prompt, dialog)
        dialog.show()
    }

    private fun parsePromptDate(type: Int, value: String?): LocalDate? = when (type) {
        GeckoSession.PromptDelegate.DateTimePrompt.Type.DATE -> parseDateValue(value)
        GeckoSession.PromptDelegate.DateTimePrompt.Type.MONTH -> parseMonthValue(value)
        GeckoSession.PromptDelegate.DateTimePrompt.Type.WEEK -> parseIsoWeekValue(value)
        GeckoSession.PromptDelegate.DateTimePrompt.Type.DATETIME_LOCAL -> parseDateTimeLocalValue(value)?.toLocalDate()
        else -> null
    }

    private fun promptDateBoundary(prompt: GeckoSession.PromptDelegate.DateTimePrompt, value: String?, upper: Boolean): LocalDate? {
        val date = parsePromptDate(prompt.type, value) ?: return null
        return when {
            prompt.type == GeckoSession.PromptDelegate.DateTimePrompt.Type.MONTH && upper -> date.withDayOfMonth(date.lengthOfMonth())
            prompt.type == GeckoSession.PromptDelegate.DateTimePrompt.Type.WEEK && upper -> date.plusDays(6)
            else -> date
        }
    }

    private fun dateWithinDateRange(prompt: GeckoSession.PromptDelegate.DateTimePrompt, date: LocalDate): Boolean {
        val min = promptDateBoundary(prompt, prompt.minValue, upper = false)
        val max = promptDateBoundary(prompt, prompt.maxValue, upper = true)
        return (min == null || !date.isBefore(min)) && (max == null || !date.isAfter(max))
    }

    private fun showTimePicker(
        prompt: GeckoSession.PromptDelegate.DateTimePrompt,
        guard: PromptGuard<GeckoSession.PromptDelegate.PromptResponse>,
        date: LocalDate? = null,
    ) {
        val defaultTime = if (date == null) {
            parseTimeValue(prompt.defaultValue) ?: LocalTime.now()
        } else {
            parseDateTimeLocalValue(prompt.defaultValue)?.toLocalTime() ?: LocalTime.now()
        }
        val picker = TimePicker(activity).apply {
            setIs24HourView(true)
            hour = defaultTime.hour
            minute = defaultTime.minute
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle(prompt.title.orEmpty())
            .setView(picker)
            .setNegativeButton("Отмена") { _, _ -> guard.complete { prompt.dismiss() } }
            .setPositiveButton("ОК", null)
            .setOnCancelListener { guard.complete { prompt.dismiss() } }
            .create()
        bindPromptDialog(prompt, dialog)
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val time = LocalTime.of(picker.hour, picker.minute)
                val outOfRange = if (date == null) {
                    val min = parseTimeValue(prompt.minValue)
                    val max = parseTimeValue(prompt.maxValue)
                    (min != null && time < min) || (max != null && time > max)
                } else {
                    val selected = date.atTime(time)
                    val min = parseDateTimeLocalValue(prompt.minValue)
                    val max = parseDateTimeLocalValue(prompt.maxValue)
                    (min != null && selected < min) || (max != null && selected > max)
                }
                if (outOfRange) {
                    Toast.makeText(activity, "Время вне допустимого диапазона", Toast.LENGTH_SHORT).show()
                } else {
                    val value = if (date == null) formatTimeValue(time) else formatDateTimeLocal(date, time)
                    guard.complete { prompt.confirm(value) }
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    override fun onPopupPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.PopupPrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        val targetUri = prompt.targetUri
        if (BuildConfig.DEBUG) Log.d("MinibrowserNavigation", "popup prompt uri=$targetUri")
        if (!isAllowedPopupTarget(targetUri)) {
            return GeckoResult.fromValue(prompt.confirm(AllowOrDeny.DENY))
        }
        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
        val guard = PromptGuard(prompt, result)
        activity.runOnUiThread {
            if (!isPromptOpen(prompt)) return@runOnUiThread
            val host = runCatching { Uri.parse(targetUri).host }.getOrNull().orEmpty()
            val message = if (host.isBlank()) {
                "Сайт пытается открыть новую страницу."
            } else {
                "Сайт пытается открыть страницу на $host."
            }
            val dialog = AlertDialog.Builder(activity)
                .setTitle("Открыть новое окно?")
                .setMessage(message)
                .setNegativeButton("Отмена") { _, _ -> guard.complete { prompt.confirm(AllowOrDeny.DENY) } }
                .setPositiveButton("Открыть") { _, _ -> guard.complete { prompt.confirm(AllowOrDeny.ALLOW) } }
                .setOnCancelListener { guard.complete { prompt.confirm(AllowOrDeny.DENY) } }
                .create()
            bindPromptDialog(prompt, dialog)
            dialog.show()
        }
        return result
    }

    override fun onFilePrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.FilePrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
        val guard = PromptGuard(prompt, result)
        pickFiles?.invoke(prompt.type, prompt.mimeTypes ?: emptyArray()) { uris ->
            if (uris.isEmpty()) guard.complete { prompt.dismiss() }
            else guard.complete { prompt.confirm(activity, uris) }
        } ?: guard.complete { prompt.dismiss() }
        return result
    }

    private fun dialog(
        prompt: GeckoSession.PromptDelegate.BasePrompt,
        title: String,
        message: String,
        positive: () -> GeckoSession.PromptDelegate.PromptResponse,
        negative: () -> GeckoSession.PromptDelegate.PromptResponse,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
        val guard = PromptGuard(prompt, result)
        activity.runOnUiThread {
            if (!isPromptOpen(prompt)) return@runOnUiThread
            val dialog = AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton("Отмена") { _, _ -> guard.complete(negative) }
                .setPositiveButton("ОК") { _, _ -> guard.complete(positive) }
                .setOnCancelListener { guard.complete(negative) }
                .create()
            bindPromptDialog(prompt, dialog)
            dialog.show()
        }
        return result
    }

}
