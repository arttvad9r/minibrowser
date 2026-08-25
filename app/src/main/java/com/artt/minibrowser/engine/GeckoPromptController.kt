package com.artt.minibrowser.engine

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.widget.LinearLayout
import android.text.InputType
import android.widget.EditText
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

/** Small native prompt bridge; it deliberately denies unattended popup/auth actions. */
class GeckoPromptController(
    private val activity: Activity,
    private val pickFiles: ((Int, Array<String>, (Array<Uri>) -> Unit) -> Unit)? = null,
) : GeckoSession.PromptDelegate {
    override fun onAlertPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.AlertPrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> =
        dialog(prompt.title.orEmpty(), prompt.message.orEmpty(), { prompt.dismiss() }, { prompt.dismiss() })

    override fun onTextPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.TextPrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
        activity.runOnUiThread {
            val input = EditText(activity).apply {
                setText(prompt.defaultValue)
                inputType = InputType.TYPE_CLASS_TEXT
            }
            AlertDialog.Builder(activity)
                .setTitle(prompt.title.orEmpty())
                .setMessage(prompt.message.orEmpty())
                .setView(input)
                .setNegativeButton("Отмена") { _, _ -> result.complete(prompt.dismiss()) }
                .setPositiveButton("ОК") { _, _ -> result.complete(prompt.confirm(input.text.toString())) }
                .setOnCancelListener { result.complete(prompt.dismiss()) }
                .show()
        }
        return result
    }

    override fun onButtonPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.ButtonPrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> =
        dialog(prompt.title.orEmpty(), prompt.message.orEmpty(), { prompt.confirm(GeckoSession.PromptDelegate.ButtonPrompt.Type.POSITIVE) }, { prompt.dismiss() })

    override fun onAuthPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.AuthPrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
        activity.runOnUiThread {
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
            AlertDialog.Builder(activity)
                .setTitle(prompt.title.orEmpty())
                .setMessage(prompt.message.orEmpty())
                .setView(fields)
                .setNegativeButton("Отмена") { _, _ -> result.complete(prompt.dismiss()) }
                .setPositiveButton("Войти") { _, _ -> result.complete(prompt.confirm(user.text.toString(), password.text.toString())) }
                .setOnCancelListener { result.complete(prompt.dismiss()) }
                .show()
        }
        return result
    }

    override fun onChoicePrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.ChoicePrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
        val choices = prompt.choices.orEmpty().filterNot { it.disabled || it.separator }
        if (choices.isEmpty()) return GeckoResult.fromValue(prompt.dismiss())
        activity.runOnUiThread {
            val builder = AlertDialog.Builder(activity).setTitle(prompt.title.orEmpty())
            if (prompt.type == GeckoSession.PromptDelegate.ChoicePrompt.Type.MULTIPLE) {
                val selected = choices.map { it.selected }.toBooleanArray()
                builder.setMultiChoiceItems(choices.map { it.label }.toTypedArray(), selected) { _, index, checked ->
                    selected[index] = checked
                }.setNegativeButton("Отмена") { _, _ -> result.complete(prompt.dismiss()) }
                    .setPositiveButton("ОК") { _, _ ->
                        result.complete(prompt.confirm(choices.filterIndexed { index, _ -> selected[index] }.toTypedArray()))
                    }
            } else {
                builder.setItems(choices.map { it.label }.toTypedArray()) { _, index ->
                    result.complete(prompt.confirm(choices[index]))
                }
            }
            builder.setOnCancelListener { result.complete(prompt.dismiss()) }.show()
        }
        return result
    }

    override fun onBeforeUnloadPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.BeforeUnloadPrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> =
        dialog(prompt.title.orEmpty(), "Покинуть страницу?", { prompt.confirm(AllowOrDeny.ALLOW) }, { prompt.confirm(AllowOrDeny.DENY) })

    override fun onRepostConfirmPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.RepostConfirmPrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> =
        dialog(
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
        return dialog("Разрешить загрузку папки?", message, { prompt.confirm(AllowOrDeny.ALLOW) }, { prompt.confirm(AllowOrDeny.DENY) })
    }

    override fun onRedirectPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.RedirectPrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        val host = runCatching { Uri.parse(prompt.targetUri).host }.getOrNull().orEmpty()
        val message = if (host.isBlank()) "Разрешить перенаправление?" else "Разрешить перенаправление на $host?"
        return dialog("Разрешить перенаправление?", message, { prompt.confirm(AllowOrDeny.ALLOW) }, { prompt.confirm(AllowOrDeny.DENY) })
    }

    override fun onSharePrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.SharePrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
        activity.runOnUiThread {
            val text = listOf(prompt.text, prompt.uri).filter { !it.isNullOrBlank() }.joinToString("\n")
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            try {
                if (share.resolveActivity(activity.packageManager) == null) {
                    result.complete(prompt.confirm(GeckoSession.PromptDelegate.SharePrompt.Result.FAILURE))
                } else {
                    activity.startActivity(Intent.createChooser(share, "Поделиться"))
                    result.complete(prompt.confirm(GeckoSession.PromptDelegate.SharePrompt.Result.SUCCESS))
                }
            } catch (_: Exception) {
                result.complete(prompt.confirm(GeckoSession.PromptDelegate.SharePrompt.Result.FAILURE))
            }
        }
        return result
    }

    override fun onDateTimePrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.DateTimePrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
        activity.runOnUiThread { showDateTimePrompt(prompt, result) }
        return result
    }

    override fun onColorPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.ColorPrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
        activity.runOnUiThread {
            val input = EditText(activity).apply {
                setText((prompt.defaultValue ?: "").takeIf { it.matches(Regex("^#[0-9A-Fa-f]{6}$")) } ?: "#000000")
                inputType = InputType.TYPE_CLASS_TEXT
            }
            val dialog = AlertDialog.Builder(activity)
                .setTitle(prompt.title.orEmpty())
                .setView(input)
                .setNegativeButton("Отмена") { _, _ -> result.complete(prompt.dismiss()) }
                .setPositiveButton("ОК", null)
                .setOnCancelListener { result.complete(prompt.dismiss()) }
                .create()
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val value = input.text.toString()
                    if (value.matches(Regex("^#[0-9A-Fa-f]{6}$"))) {
                        result.complete(prompt.confirm(value))
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
        result: GeckoResult<GeckoSession.PromptDelegate.PromptResponse>,
    ) {
        when (prompt.type) {
            GeckoSession.PromptDelegate.DateTimePrompt.Type.TIME -> showTimePicker(prompt, result)
            GeckoSession.PromptDelegate.DateTimePrompt.Type.DATE,
            GeckoSession.PromptDelegate.DateTimePrompt.Type.MONTH,
            GeckoSession.PromptDelegate.DateTimePrompt.Type.WEEK,
            GeckoSession.PromptDelegate.DateTimePrompt.Type.DATETIME_LOCAL -> showDatePicker(prompt, result)
            else -> result.complete(prompt.dismiss())
        }
    }

    private fun showDatePicker(
        prompt: GeckoSession.PromptDelegate.DateTimePrompt,
        result: GeckoResult<GeckoSession.PromptDelegate.PromptResponse>,
    ) {
        val defaultDate = parsePromptDate(prompt.defaultValue) ?: LocalDate.now()
        val dialog = DatePickerDialog(activity, { _, year, month, day ->
            val date = LocalDate.of(year, month + 1, day)
            if (prompt.type == GeckoSession.PromptDelegate.DateTimePrompt.Type.DATETIME_LOCAL) {
                showTimePicker(prompt, result, date)
            } else {
                val value = when (prompt.type) {
                    GeckoSession.PromptDelegate.DateTimePrompt.Type.MONTH -> formatMonthValue(date)
                    GeckoSession.PromptDelegate.DateTimePrompt.Type.WEEK -> formatIsoWeekValue(date)
                    else -> formatDateValue(date)
                }
                result.complete(prompt.confirm(value))
            }
        }, defaultDate.year, defaultDate.monthValue - 1, defaultDate.dayOfMonth)
        parsePromptDate(prompt.minValue)?.let {
            dialog.datePicker.minDate = it.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        parsePromptDate(prompt.maxValue)?.let {
            dialog.datePicker.maxDate = it.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        dialog.setOnCancelListener { result.complete(prompt.dismiss()) }
        dialog.show()
    }

    private fun parsePromptDate(value: String?): LocalDate? = runCatching {
        value?.let { if (it.contains('T')) LocalDateTime.parse(it).toLocalDate() else LocalDate.parse(it) }
    }.getOrNull()

    private fun showTimePicker(
        prompt: GeckoSession.PromptDelegate.DateTimePrompt,
        result: GeckoResult<GeckoSession.PromptDelegate.PromptResponse>,
        date: LocalDate? = null,
    ) {
        val defaultTime = runCatching {
            if (date == null) LocalTime.parse(prompt.defaultValue) else LocalDateTime.parse(prompt.defaultValue).toLocalTime()
        }.getOrDefault(LocalTime.now())
        val dialog = TimePickerDialog(activity, { _, hour, minute ->
            val time = LocalTime.of(hour, minute)
            val value = if (date == null) formatTimeValue(time) else formatDateTimeLocal(date, time)
            result.complete(prompt.confirm(value))
        }, defaultTime.hour, defaultTime.minute, true)
        dialog.setOnCancelListener { result.complete(prompt.dismiss()) }
        dialog.show()
    }

    override fun onPopupPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.PopupPrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> =
        GeckoResult.fromValue(prompt.confirm(AllowOrDeny.DENY))

    override fun onFilePrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.FilePrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
        pickFiles?.invoke(prompt.type, prompt.mimeTypes ?: emptyArray()) { uris ->
            if (uris.isEmpty()) result.complete(prompt.dismiss())
            else result.complete(prompt.confirm(activity, uris))
        } ?: result.complete(prompt.dismiss())
        return result
    }

    private fun dialog(
        title: String,
        message: String,
        positive: () -> GeckoSession.PromptDelegate.PromptResponse,
        negative: () -> GeckoSession.PromptDelegate.PromptResponse,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
        activity.runOnUiThread {
            AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton("Отмена") { _, _ -> result.complete(negative()) }
                .setPositiveButton("ОК") { _, _ -> result.complete(positive()) }
                .setOnCancelListener { result.complete(negative()) }
                .show()
        }
        return result
    }

}
