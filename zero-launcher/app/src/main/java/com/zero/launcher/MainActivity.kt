package com.zero.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private val bg = Color.rgb(10, 12, 11)
    private val surface = Color.rgb(20, 24, 21)
    private val lime = Color.rgb(202, 255, 105)
    private val white = Color.rgb(244, 246, 240)
    private val muted = Color.rgb(142, 151, 139)
    private val prefs by lazy { getSharedPreferences("zero_preferences", Context.MODE_PRIVATE) }
    private lateinit var search: EditText
    private lateinit var appsGrid: LinearLayout
    private lateinit var favoritesGrid: LinearLayout
    private lateinit var clock: TextView
    private lateinit var dateLabel: TextView
    private lateinit var countLabel: TextView
    private lateinit var emptyLabel: TextView
    private var apps: List<ResolveInfo> = emptyList()
    private var query = ""
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            if (::clock.isInitialized) {
                val now = Date()
                clock.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
                dateLabel.text = SimpleDateFormat("EEEE · d MMMM", Locale.getDefault()).format(now).uppercase(Locale.getDefault())
            }
            handler.postDelayed(this, 30_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        buildUi()
        loadApps()
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        if (::appsGrid.isInitialized) loadApps()
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        val scroll = ScrollView(this).apply { isFillViewport = true; overScrollMode = View.OVER_SCROLL_NEVER }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(22), dp(22), dp(30))
        }
        scroll.addView(page)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val mark = TextView(this).apply {
            text = "Z"; textSize = 20f; typeface = Typeface.create("sans-serif-black", 0)
            gravity = Gravity.CENTER; setTextColor(bg); background = rounded(lime, 15)
        }
        bar.addView(mark, LinearLayout.LayoutParams(dp(44), dp(44)))
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
        brand.addView(label("ZERO", 16, white, true))
        brand.addView(label("YOUR SPACE. YOUR RULES.", 9, muted, true).apply { setPadding(0, dp(3), 0, 0); letterSpacing = .08f })
        bar.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        val settings = button("⚙", 20)
        settings.setOnClickListener { showSettings() }
        bar.addView(settings, LinearLayout.LayoutParams(dp(44), dp(44)))
        page.addView(bar)

        val hero = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(35), 0, dp(23)) }
        clock = label("--:--", 64, white, false).apply {
            typeface = Typeface.create("sans-serif-light", 0)
            includeFontPadding = false
        }
        dateLabel = label("", 11, muted, true).apply { setPadding(dp(3), dp(6), 0, 0); letterSpacing = .06f }
        hero.addView(clock)
        hero.addView(dateLabel)
        page.addView(hero)

        val searchBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = rounded(surface, 17); setPadding(dp(14), 0, dp(12), 0)
        }
        searchBox.addView(label("⌕", 25, lime, false))
        search = EditText(this).apply {
            hint = "Find an app..."; setHintTextColor(muted); setTextColor(white); textSize = 14f
            singleLine = true; background = null; setPadding(dp(10), 0, dp(4), 0)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { query = s?.toString().orEmpty(); renderApps() }
                override fun afterTextChanged(s: Editable?) {}
            })
            setOnEditorActionListener { _, _, _ ->
                (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)?.hideSoftInputFromWindow(windowToken, 0)
                clearFocus(); true
            }
        }
        searchBox.addView(search, LinearLayout.LayoutParams(0, dp(54), 1f))
        val clear = button("×", 22)
        clear.setOnClickListener { search.setText(""); search.requestFocus() }
        searchBox.addView(clear, LinearLayout.LayoutParams(dp(30), dp(44)))
        page.addView(searchBox)

        page.addView(section("YOUR QUICK ACCESS", "LONG-PRESS TO PIN").apply { setPadding(0, dp(28), 0, dp(12)) })
        favoritesGrid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(favoritesGrid)

        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(27), 0, dp(13))
        }
        heading.addView(label("ALL APPLICATIONS", 10, muted, true), LinearLayout.LayoutParams(0, -2, 1f))
        countLabel = label("0 APPS", 9, lime, true)
        heading.addView(countLabel)
        page.addView(heading)
        appsGrid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(appsGrid)
        emptyLabel = label("No apps found.", 13, muted, false).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(25), 0, dp(25)); visibility = View.GONE
        }
        page.addView(emptyLabel)
        page.addView(label("ZERO  /  BUILT FOR FOCUS", 9, muted, true).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(28), 0, 0); letterSpacing = .12f
        })
    }

    private fun loadApps() {
        val intent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        apps = packageManager.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase(Locale.getDefault()) }
        renderApps()
    }

    private fun renderApps() {
        if (!::appsGrid.isInitialized) return
        appsGrid.removeAllViews()
        favoritesGrid.removeAllViews()
        val favorites = prefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()
        val matching = apps.filter {
            val name = it.loadLabel(packageManager).toString()
            (name.contains(query, true) || it.activityInfo.packageName.contains(query, true))
        }
        val pinned = matching.filter { favorites.contains(it.activityInfo.packageName) }
        addRows(favoritesGrid, pinned, true)
        val showFavorites = query.isEmpty()
        favoritesGrid.visibility = if (showFavorites && pinned.isNotEmpty()) View.VISIBLE else View.GONE
        val remaining = matching.filterNot { favorites.contains(it.activityInfo.packageName) }
        countLabel.text = "${matching.size} ${if (matching.size == 1) "APP" else "APPS"}"
        emptyLabel.visibility = if (matching.isEmpty()) View.VISIBLE else View.GONE
        addRows(appsGrid, remaining, false)
    }

    private fun addRows(container: LinearLayout, items: List<ResolveInfo>, compact: Boolean) {
        var i = 0
        while (i < items.size) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP }
            for (column in 0..1) {
                if (i < items.size) {
                    val info = items[i++]
                    val params = LinearLayout.LayoutParams(0, dp(if (compact) 92 else 104), 1f)
                    if (column == 0) params.rightMargin = dp(7) else params.leftMargin = dp(7)
                    params.bottomMargin = dp(12)
                    row.addView(appCard(info, compact), params)
                } else row.addView(View(this), LinearLayout.LayoutParams(0, dp(104), 1f))
            }
            container.addView(row)
        }
    }

    private fun appCard(info: ResolveInfo, compact: Boolean): View {
        val pkg = info.activityInfo.packageName
        val name = info.loadLabel(packageManager).toString()
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(13), dp(10), dp(11), dp(10)); background = rounded(surface, 17)
            isClickable = true; isFocusable = true
            setOnClickListener {
                try {
                    val launch = packageManager.getLaunchIntentForPackage(pkg)
                    if (launch != null) startActivity(launch) else Toast.makeText(this@MainActivity, "App cannot be opened", Toast.LENGTH_SHORT).show()
                } catch (_: Exception) { Toast.makeText(this@MainActivity, "Couldn't open app", Toast.LENGTH_SHORT).show() }
            }
            setOnLongClickListener {
                val set = (prefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()).toMutableSet()
                val nowPinned = if (set.contains(pkg)) { set.remove(pkg); false } else { set.add(pkg); true }
                prefs.edit().putStringSet("favorites", set).apply()
                Toast.makeText(this@MainActivity, if (nowPinned) "$name pinned to Quick Access" else "$name unpinned", Toast.LENGTH_SHORT).show()
                renderApps(); true
            }
            val top = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val icon = ImageView(this@MainActivity).apply {
                setImageDrawable(info.loadIcon(packageManager)); scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = name
            }
            top.addView(icon, LinearLayout.LayoutParams(dp(34), dp(34)))
            val spacer = View(this@MainActivity)
            top.addView(spacer, LinearLayout.LayoutParams(0, 1, 1f))
            val favs = prefs.getStringSet("favorites", emptySet()) ?: emptySet()
            top.addView(label(if (favs.contains(pkg)) "✦" else "↗", 14, if (favs.contains(pkg)) lime else muted, true))
            addView(top)
            addView(label(name, if (compact) 11 else 12, white, true).apply {
                maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(0, dp(8), 0, 0)
            })
        }
    }

    private fun showSettings() {
        val options = arrayOf("Show all apps", "Android system settings", "Manage ZERO Launcher")
        AlertDialog.Builder(this).setTitle("ZERO CONTROL").setItems(options) { _, which ->
            when (which) {
                0 -> { search.setText(""); renderApps() }
                1 -> startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
                2 -> startActivity(Intent(Intent.ACTION_VIEW).setData(android.net.Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }.setNegativeButton("CLOSE", null).show()
    }

    private fun section(left: String, right: String) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        addView(label(left, 10, muted, true), LinearLayout.LayoutParams(0, -2, 1f))
        addView(label(right, 8, muted, false).apply { letterSpacing = .05f })
    }

    private fun label(text: String, size: Int, color: Int, bold: Boolean) = TextView(this).apply {
        this.text = text; textSize = size.toFloat(); setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private fun button(text: String, size: Int) = TextView(this).apply {
        this.text = text; textSize = size.toFloat(); setTextColor(white); gravity = Gravity.CENTER
        background = rounded(surface, 15); isClickable = true; isFocusable = true
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
