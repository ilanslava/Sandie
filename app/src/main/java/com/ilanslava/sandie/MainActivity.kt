package com.ilanslava.sandie

import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private var lightMode = false
    private var query = ""
    private lateinit var root: LinearLayout
    private lateinit var appGrid: GridLayout
    private lateinit var resultLabel: TextView
    private lateinit var search: EditText
    private lateinit var clock: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val apps = mutableListOf<ApplicationInfo>()
    private val clockTick = object : Runnable {
        override fun run() {
            if (::clock.isInitialized) {
                clock.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            }
            handler.postDelayed(this, 30_000)
        }
    }

    private val bg get() = if (lightMode) Color.rgb(242, 245, 250) else Color.rgb(9, 13, 24)
    private val panel get() = if (lightMode) Color.WHITE else Color.rgb(20, 27, 44)
    private val fg get() = if (lightMode) Color.rgb(20, 28, 45) else Color.rgb(244, 247, 255)
    private val muted get() = if (lightMode) Color.rgb(100, 112, 132) else Color.rgb(147, 160, 185)
    private val accent = Color.rgb(93, 225, 210)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        loadApps()
        buildHome()
        handler.post(clockTick)
    }

    override fun onDestroy() {
        handler.removeCallbacks(clockTick)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        if (::appGrid.isInitialized) renderApps()
    }

    private fun loadApps() {
        val pm = packageManager
        apps.clear()
        apps.addAll(pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .sortedBy { pm.getApplicationLabel(it).toString().lowercase(Locale.getDefault()) })
    }

    private fun buildHome() {
        window.statusBarColor = bg
        window.navigationBarColor = bg
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(22), dp(22), dp(8))
            setBackgroundColor(bg)
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        brand.addView(label("ZERO / PERSONAL OS", 12, accent, true))
        clock = label("00:00", 52, fg, true)
        clock.setPadding(0, dp(3), 0, 0)
        brand.addView(clock)
        brand.addView(label(SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date()), 14, muted, false))
        top.addView(brand)
        val themeButton = label("◐", 26, fg, true).apply {
            gravity = Gravity.CENTER
            background = shape(panel, 22)
            layoutParams = LinearLayout.LayoutParams(dp(54), dp(54))
            setOnClickListener { lightMode = !lightMode; buildHome() }
        }
        top.addView(themeButton)
        root.addView(top)

        val searchWrap = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = shape(panel, 18)
            setPadding(dp(15), 0, dp(15), 0)
            layoutParams = LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(22) }
        }
        searchWrap.addView(label("⌕", 25, accent, true))
        search = EditText(this).apply {
            hint = "Search apps"
            setHintTextColor(muted)
            setTextColor(fg)
            textSize = 15f
            singleLine = true
            background = null
            setPadding(dp(10), 0, 0, 0)
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            addTextChangedListener(SimpleWatcher { value -> query = value; renderApps() })
        }
        searchWrap.addView(search, LinearLayout.LayoutParams(0, -1, 1f))
        root.addView(searchWrap)

        val quickHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(23); bottomMargin = dp(10) }
        }
        quickHeader.addView(label("QUICK ACCESS", 11, muted, true), LinearLayout.LayoutParams(0, -2, 1f))
        quickHeader.addView(label("Tap an icon to open", 11, muted, false))
        root.addView(quickHeader)

        val quick = GridLayout(this).apply {
            columnCount = 4
            rowCount = 1
            alignmentMode = GridLayout.ALIGN_BOUNDS
        }
        val quickApps = preferredApps().take(4)
        quickApps.forEach { quick.addView(appTile(it, true), GridLayout.LayoutParams().apply {
            width = 0; height = dp(86); columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            setMargins(dp(3), 0, dp(3), 0)
        }) }
        root.addView(quick, LinearLayout.LayoutParams(-1, dp(90)))

        val appHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20); bottomMargin = dp(10) }
        }
        appHeader.addView(label("YOUR APPS", 11, muted, true), LinearLayout.LayoutParams(0, -2, 1f))
        resultLabel = label("${apps.size} apps", 11, accent, true)
        appHeader.addView(resultLabel)
        root.addView(appHeader)

        val scroll = ScrollView(this).apply { isFillViewport = false; clipToPadding = false }
        appGrid = GridLayout(this).apply { columnCount = 4; alignmentMode = GridLayout.ALIGN_BOUNDS }
        scroll.addView(appGrid)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val footer = label("◆   YOUR SPACE. YOUR RULES.", 10, muted, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(5))
        }
        root.addView(footer)
        setContentView(root)
        renderApps()
    }

    private fun preferredApps(): List<ApplicationInfo> {
        val pm = packageManager
        val preferred = listOf("com.android.settings", "com.google.android.apps.chrome", "com.android.chrome",
            "com.google.android.youtube", "com.whatsapp", "com.google.android.dialer", "com.android.camera2")
        val ordered = preferred.mapNotNull { pkg -> apps.firstOrNull { it.packageName == pkg } }
        return (ordered + apps.filterNot { it in ordered }).distinctBy { it.packageName }
    }

    private fun renderApps() {
        if (!::appGrid.isInitialized) return
        appGrid.removeAllViews()
        val pm = packageManager
        val shown = preferredApps().filter {
            query.isBlank() || pm.getApplicationLabel(it).toString().contains(query, true)
        }
        resultLabel.text = "${shown.size} apps"
        shown.forEach { info ->
            appGrid.addView(appTile(info, false), GridLayout.LayoutParams().apply {
                width = 0; height = dp(105)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(2), dp(3), dp(2), dp(5))
            })
        }
        if (shown.isEmpty()) {
            val empty = label("No apps found", 14, muted, false)
            appGrid.addView(empty)
        }
    }

    private fun appTile(info: ApplicationInfo, quick: Boolean): View {
        val pm = packageManager
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = if (quick) shape(panel, 18) else shape(Color.TRANSPARENT, 16)
            setPadding(dp(4), dp(7), dp(4), dp(5))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                val launch = pm.getLaunchIntentForPackage(info.packageName)
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try { startActivity(launch) } catch (_: Exception) { }
                }
            }
            setOnLongClickListener {
                val details = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                details.data = android.net.Uri.parse("package:" + info.packageName)
                startActivity(details)
                true
            }
        }
        val icon = ImageView(this).apply {
            setImageDrawable(info.loadIcon(pm))
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        tile.addView(icon, LinearLayout.LayoutParams(dp(quickSize(quick)), dp(quickSize(quick))))
        val name = label(pm.getApplicationLabel(info).toString(), 11, fg, false).apply {
            gravity = Gravity.CENTER
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dp(5), 0, 0)
        }
        tile.addView(name, LinearLayout.LayoutParams(-1, -2))
        return tile
    }

    private fun quickSize(quick: Boolean) = if (quick) 34 else 39

    private fun label(text: String, size: Int, color: Int, bold: Boolean): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size.toFloat()
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun shape(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private class SimpleWatcher(val changed: (String) -> Unit) : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { changed(s?.toString().orEmpty()) }
        override fun afterTextChanged(s: android.text.Editable?) {}
    }
}
