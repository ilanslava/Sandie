@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.futurelauncher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent { LauncherApp() }
    }
}

data class AppInfo(val label: String, val pkg: String, val icon: ImageBitmap)

enum class IconShape(val label: String) { Circle("Círculo"), Squircle("Quadrado"), Hex("Hexágono") }

val HexShape = GenericShape { size, _ ->
    val r = size.minDimension / 2f
    val cx = size.width / 2f
    val cy = size.height / 2f
    for (i in 0 until 6) {
        val a = Math.toRadians((60 * i - 90).toDouble())
        val x = cx + r * cos(a).toFloat()
        val y = cy + r * sin(a).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

fun IconShape.toShape(): Shape = when (this) {
    IconShape.Circle -> CircleShape
    IconShape.Squircle -> RoundedCornerShape(30)
    IconShape.Hex -> HexShape
}

val Palette = listOf(
    Color(0xFF00E5FF), Color(0xFFB388FF), Color(0xFFFF4081),
    Color(0xFF69F0AE), Color(0xFFFFD740)
)

fun loadApps(ctx: Context): List<AppInfo> {
    val pm = ctx.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .filter { it.activityInfo.packageName != ctx.packageName }
        .map { AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.loadIcon(pm).toBitmap(144, 144).asImageBitmap()) }
        .distinctBy { it.pkg }
        .sortedBy { it.label.lowercase() }
}

fun Modifier.glass(shape: Shape, accent: Color): Modifier = this
    .clip(shape)
    .background(Color.White.copy(alpha = 0.07f))
    .border(1.dp, Brush.linearGradient(listOf(accent.copy(alpha = 0.6f), Color.White.copy(alpha = 0.05f))), shape)

@Composable
fun LauncherApp() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE) }
    var shape by remember { mutableStateOf(IconShape.valueOf(prefs.getString("shape", "Squircle")!!)) }
    var accentIdx by remember { mutableStateOf(prefs.getInt("accent", 0)) }
    var iconSize by remember { mutableStateOf(prefs.getFloat("size", 56f)) }
    var dock by remember { mutableStateOf(prefs.getString("dock", "")!!.split(",").filter { it.isNotBlank() }) }
    var selected by remember { mutableStateOf<AppInfo?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    val accent = Palette[accentIdx]

    val apps by produceState(emptyList<AppInfo>()) { value = withContext(Dispatchers.IO) { loadApps(ctx) } }
    val dockApps = dock.mapNotNull { p -> apps.find { it.pkg == p } }.ifEmpty { apps.take(4) }

    val pager = rememberPagerState(pageCount = { 2 })
    BackHandler(pager.currentPage == 1) { scope.launch { pager.animateScrollToPage(0) } }

    val launch: (AppInfo) -> Unit = { app ->
        ctx.packageManager.getLaunchIntentForPackage(app.pkg)?.let { ctx.startActivity(it) }
    }

    val t = rememberInfiniteTransition(label = "glow")
    val glow by t.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse), label = "g")

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF05060F), Color(0xFF0B1030), Color(0xFF120A2A))))
            .drawBehind {
                drawCircle(
                    Brush.radialGradient(listOf(accent.copy(alpha = 0.28f), Color.Transparent), Offset(size.width * (0.2f + 0.6f * glow), size.height * 0.25f), size.width * 0.8f),
                    radius = size.width * 0.8f, center = Offset(size.width * (0.2f + 0.6f * glow), size.height * 0.25f)
                )
            }
    ) {
        VerticalPager(pager, Modifier.fillMaxSize()) { page ->
            if (page == 0) HomePage(dockApps, shape, iconSize, accent, apps.size, { scope.launch { pager.animateScrollToPage(1) } }, { showSettings = true }, launch) { selected = it }
            else DrawerPage(apps, shape, iconSize, accent, { app -> launch(app); scope.launch { pager.scrollToPage(0) } }) { selected = it }
        }
    }

    selected?.let { app ->
        val pinned = app.pkg in dock
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(app.label) },
            confirmButton = {
                TextButton(onClick = {
                    dock = if (pinned) dock - app.pkg else (dock + app.pkg).takeLast(5)
                    prefs.edit().putString("dock", dock.joinToString(",")).apply()
                    selected = null
                }) { Text(if (pinned) "Tirar do dock" else "Fixar no dock") }
            },
            dismissButton = {
                TextButton(onClick = {
                    ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}")))
                    selected = null
                }) { Text("Info do app") }
            }
        )
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Personalizar") },
            confirmButton = { TextButton(onClick = { showSettings = false }) { Text("Pronto") } },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Formato dos ícones")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconShape.values().forEach { s ->
                            FilterChip(selected = shape == s, onClick = { shape = s; prefs.edit().putString("shape", s.name).apply() }, label = { Text(s.label, fontSize = 12.sp) })
                        }
                    }
                    Text("Cor neon")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Palette.forEachIndexed { i, c ->
                            Box(
                                Modifier.size(34.dp).clip(CircleShape).background(c)
                                    .border(if (i == accentIdx) 3.dp else 0.dp, Color.White, CircleShape)
                                    .clickable { accentIdx = i; prefs.edit().putInt("accent", i).apply() }
                            )
                        }
                    }
                    Text("Tamanho dos ícones")
                    Slider(value = iconSize, onValueChange = { iconSize = it; prefs.edit().putFloat("size", it).apply() }, valueRange = 44f..76f)
                }
            }
        )
    }
}

@Composable
fun HomePage(
    dockApps: List<AppInfo>, shape: IconShape, size: Float, accent: Color, count: Int,
    openDrawer: () -> Unit, openSettings: () -> Unit, launch: (AppInfo) -> Unit, onLong: (AppInfo) -> Unit
) {
    val ctx = LocalContext.current
    val now by produceState(LocalDateTime.now()) { while (true) { value = LocalDateTime.now(); delay(1000) } }
    val battery by produceState(0) {
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        while (true) { value = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY); delay(30000) }
    }
    val br = Locale("pt", "BR")
    val date = now.format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", br)).replaceFirstChar { it.uppercase() }
    val greeting = when (now.hour) { in 5..11 -> "Bom dia"; in 12..17 -> "Boa tarde"; else -> "Boa noite" }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text("⚙", fontSize = 22.sp, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.clickable { openSettings() }.padding(8.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), fontSize = 92.sp, fontWeight = FontWeight.Thin, color = Color.White)
        Text(date, fontSize = 18.sp, color = accent)
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp), accent).padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(greeting, fontSize = 20.sp, color = Color.White, fontWeight = FontWeight.Medium)
                Text("$count apps instalados", fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("$battery%", fontSize = 26.sp, color = accent, fontWeight = FontWeight.Light)
                Box(Modifier.width(70.dp).height(5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f))) {
                    Box(Modifier.fillMaxWidth(battery / 100f).fillMaxHeight().background(accent))
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text("⌃  deslize para ver os apps", color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally).clickable { openDrawer() }.padding(8.dp))
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(32.dp), accent).padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            dockApps.forEach { AppIcon(it, shape, size.dp, accent, false, { launch(it) }, { onLong(it) }) }
        }
    }
}

@Composable
fun DrawerPage(apps: List<AppInfo>, shape: IconShape, size: Float, accent: Color, launch: (AppInfo) -> Unit, onLong: (AppInfo) -> Unit) {
    var query by remember { mutableStateOf("") }
    val list = apps.filter { it.label.contains(query, ignoreCase = true) }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text("Buscar app…", color = Color.White.copy(alpha = 0.4f)) },
            shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                focusedBorderColor = accent, unfocusedBorderColor = accent.copy(alpha = 0.35f), cursorColor = accent,
                focusedContainerColor = Color.White.copy(alpha = 0.06f), unfocusedContainerColor = Color.White.copy(alpha = 0.06f)
            )
        )
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(GridCells.Adaptive(size.dp + 28.dp), Modifier.fillMaxSize()) {
            items(list, key = { it.pkg }) { app ->
                AppIcon(app, shape, size.dp, accent, true, { launch(app) }, { onLong(app) })
            }
        }
    }
}

@Composable
fun AppIcon(app: AppInfo, shape: IconShape, size: Dp, accent: Color, showLabel: Boolean, onClick: () -> Unit, onLong: () -> Unit) {
    val s = shape.toShape()
    Column(Modifier.combinedClickable(onClick = onClick, onLongClick = onLong).padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            bitmap = app.icon, contentDescription = app.label,
            modifier = Modifier.size(size).clip(s).background(Color.White.copy(alpha = 0.08f)).border(1.dp, accent.copy(alpha = 0.35f), s)
        )
        if (showLabel) {
            Spacer(Modifier.height(4.dp))
            Text(app.label, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(size + 12.dp))
        }
    }
}
