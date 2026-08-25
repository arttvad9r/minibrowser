package com.artt.minibrowser.engine

import android.app.Activity
import android.app.AlertDialog
import android.net.Uri
import android.widget.LinearLayout
import android.text.InputType
import android.widget.EditText
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession

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
