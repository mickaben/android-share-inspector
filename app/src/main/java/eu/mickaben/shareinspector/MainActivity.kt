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
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity() {
    private lateinit var reportView: TextView
    private lateinit var resolveButton: Button
    private var baseReport: String = ""

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

        resolveButton = Button(this).apply {
            text = "Resolve shared URL"
            setOnClickListener { resolveSharedUrl() }
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
        root.addView(resolveButton)
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
            baseReport = "No intent received."
            reportView.text = baseReport
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

        baseReport = out.toString()
        reportView.text = baseReport
    }

    private fun resolveSharedUrl() {
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)?.trim()
        val startUrl = text?.split(Regex("\\s+"))?.firstOrNull { it.startsWith("http://") || it.startsWith("https://") }
        if (startUrl == null) {
            reportView.text = "$baseReport\n\nRESOLUTION\nNo HTTP(S) URL found in EXTRA_TEXT."
            return
        }

        resolveButton.isEnabled = false
        resolveButton.text = "Resolving..."
        reportView.text = "$baseReport\n\nRESOLUTION\nResolving $startUrl ..."

        Thread {
            val result = resolveRedirectChain(startUrl)
            runOnUiThread {
                reportView.text = "$baseReport\n\nRESOLUTION\n$result"
                resolveButton.isEnabled = true
                resolveButton.text = "Resolve shared URL"
            }
        }.start()
    }

    private fun resolveRedirectChain(startUrl: String): String {
        val out = StringBuilder()
        var current = startUrl
        val seen = linkedSetOf<String>()

        return try {
            for (hop in 0 until 12) {
                if (!seen.add(current)) {
                    out.appendLine("loop detected: $current")
                    break
                }

                val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 10000
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) ShareInspector/0.2")
                    setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                }

                val code = conn.responseCode
                val location = conn.getHeaderField("Location")
                out.appendLine("hop[$hop]")
                out.appendLine("  status=$code")
                out.appendLine("  url=$current")
                out.appendLine("  location=${location ?: "(none)"}")
                conn.disconnect()

                if (code in 300..399 && !location.isNullOrBlank()) {
                    current = URL(URL(current), location).toExternalForm()
                } else {
                    out.appendLine("FINAL_URL=$current")
                    out.appendLine("FINAL_STATUS=$code")
                    break
                }
            }
            out.toString()
        } catch (e: Exception) {
            out.appendLine("ERROR=${e.javaClass.name}: ${e.message}")
            out.toString()
        }
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
