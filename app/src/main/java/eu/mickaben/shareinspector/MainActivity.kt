package eu.mickaben.shareinspector

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var reportView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        renderIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        renderIntent(intent)
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        val copy = Button(this).apply {
            text = "Copy report"
            setOnClickListener {
                val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cb.setPrimaryClip(ClipData.newPlainText("Share Inspector report", reportView.text))
            }
        }

        reportView = TextView(this).apply {
            setTextIsSelectable(true)
            textSize = 14f
        }

        val scroll = ScrollView(this).apply { addView(reportView) }
        root.addView(copy)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))
        setContentView(root)
    }

    private fun renderIntent(i: Intent?) {
        if (i == null) {
            reportView.text = "No intent received."
            return
        }

        val out = StringBuilder()
        out.appendLine("ACTION")
        out.appendLine(i.action)
        out.appendLine()
        out.appendLine("TYPE")
        out.appendLine(i.type)
        out.appendLine()
        out.appendLine("DATA")
        out.appendLine(i.dataString)
        out.appendLine()
        out.appendLine("FLAGS")
        out.appendLine("0x${i.flags.toString(16)}")
        out.appendLine()

        out.appendLine("CATEGORIES")
        val categories = i.categories
        if (categories.isNullOrEmpty()) out.appendLine("(none)") else categories.sorted().forEach { out.appendLine(it) }
        out.appendLine()

        out.appendLine("EXTRAS")
        val extras = i.extras
        if (extras == null || extras.isEmpty) {
            out.appendLine("(none)")
        } else {
            extras.keySet().sorted().forEach { key ->
                val value = extras.get(key)
                out.appendLine(key)
                out.appendLine("  type=${value?.javaClass?.name ?: "null"}")
                out.appendLine("  value=${formatValue(value)}")
            }
        }
        out.appendLine()

        out.appendLine("CLIPDATA")
        val clip = i.clipData
        if (clip == null) {
            out.appendLine("(none)")
        } else {
            out.appendLine("description=${clip.description}")
            out.appendLine("itemCount=${clip.itemCount}")
            for (index in 0 until clip.itemCount) {
                val item = clip.getItemAt(index)
                out.appendLine("item[$index]")
                out.appendLine("  text=${item.text}")
                out.appendLine("  htmlText=${item.htmlText}")
                out.appendLine("  uri=${item.uri}")
                out.appendLine("  intent=${item.intent}")
                item.uri?.let { uri ->
                    out.appendLine("  uri.scheme=${uri.scheme}")
                    out.appendLine("  uri.authority=${uri.authority}")
                    runCatching { contentResolver.getType(uri) }
                        .onSuccess { out.appendLine("  uri.mimeType=$it") }
                        .onFailure { out.appendLine("  uri.mimeType=<error: ${it.javaClass.simpleName}>") }
                }
            }
        }

        reportView.text = out.toString()
    }

    private fun formatValue(value: Any?): String = when (value) {
        null -> "null"
        is Array<*> -> value.joinToString(prefix = "[", postfix = "]") { formatValue(it) }
        is IntArray -> value.joinToString(prefix = "[", postfix = "]")
        is LongArray -> value.joinToString(prefix = "[", postfix = "]")
        is BooleanArray -> value.joinToString(prefix = "[", postfix = "]")
        is Bundle -> value.keySet().sorted().joinToString(prefix = "Bundle{", postfix = "}") { k -> "$k=${formatValue(value.get(k))}" }
        is Uri -> value.toString()
        is Parcelable -> value.toString()
        else -> value.toString()
    }
}
