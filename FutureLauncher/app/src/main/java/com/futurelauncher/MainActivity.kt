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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    private var resumeTick by mutableIntStateOf(0)
    private var homeTick by mutableIntStateOf(0)
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); WindowCompat.setDecorFitsSystemWindows(window, false); setContent { LauncherApp(resumeTick, homeTick) } }
    override fun onResume() { super.onResume(); resumeTick++ }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); homeTick++ }
}
data class AppInfo(val label:String,val pkg:String,val icon:ImageBitmap)
data class Theme(val name:String,val a:Color,val b:Color)
val Themes = listOf(Theme("Neon",Color(0xFF00E5FF),Color(0xFF7C4DFF)),Theme("Roxo",Color(0xFFE040FB),Color(0xFF6A1B9A)),Theme("Sunset",Color(0xFFFF4081),Color(0xFFFFAB40)),Theme("Matrix",Color(0xFF69F0AE),Color(0xFF00897B)),Theme("Gelo",Color(0xFF82B1FF),Color(0xFFB2EBF2)))
enum class IconShape(val label:String){Circle("Círculo"),Squircle("Quadrado"),Hex("Hexágono")}
val HexShape = GenericShape { s, _ -> val r=s.minDimension/2f; val cx=s.width/2; val cy=s.height/2; for(i in 0 until 6){val a=Math.toRadians((60*i-90).toDouble());val x=cx+r*cos(a).toFloat();val y=cy+r*sin(a).toFloat();if(i==0)moveTo(x,y)else lineTo(x,y)};close() }
fun IconShape.toShape():Shape=when(this){IconShape.Circle->CircleShape;IconShape.Squircle->RoundedCornerShape(30);IconShape.Hex->HexShape}
fun loadApps(ctx:Context):List<AppInfo>{val pm=ctx.packageManager;return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0).filter{it.activityInfo.packageName!=ctx.packageName}.map{AppInfo(it.loadLabel(pm).toString(),it.activityInfo.packageName,it.loadIcon(pm).toBitmap(144,144).asImageBitmap())}.distinctBy{it.pkg}.sortedBy{it.label.lowercase()}}
fun Modifier.glass(shape:Shape,accent:Color)=clip(shape).background(Color.Black.copy(alpha=.28f)).background(Color.White.copy(alpha=.07f)).border(1.dp,Brush.linearGradient(listOf(accent.copy(alpha=.7f),Color.White.copy(alpha=.05f))),shape)
fun Modifier.duotone(a:Color,b:Color)=graphicsLayer{compositingStrategy=CompositingStrategy.Offscreen}.drawWithContent{drawContent();drawRect(Brush.linearGradient(listOf(a,b)),blendMode=BlendMode.Color)}

@Composable fun LauncherApp(resumeTick:Int,homeTick:Int){
 val ctx=LocalContext.current;val haptic=LocalHapticFeedback.current;val scope=rememberCoroutineScope();val prefs=remember{ctx.getSharedPreferences("launcher",Context.MODE_PRIVATE)}
 var shape by remember{mutableStateOf(runCatching{IconShape.valueOf(prefs.getString("shape","Squircle")!!)}.getOrDefault(IconShape.Squircle))}
 var themeIdx by remember{mutableIntStateOf(prefs.getInt("theme",0).coerceIn(0,Themes.lastIndex))};var duo by remember{mutableStateOf(prefs.getBoolean("duo",false))};var iconSize by remember{mutableFloatStateOf(prefs.getFloat("size",58f))}
 var dock by remember{mutableStateOf(prefs.getString("dock","")!!.split(",").filter{it.isNotBlank()})};var selected by remember{mutableStateOf<AppInfo?>(null)};var showSettings by remember{mutableStateOf(false)};var showAssistant by remember{mutableStateOf(false)};var usageRev by remember{mutableIntStateOf(0)}
 val theme=Themes[themeIdx];val apps by produceState(emptyList<AppInfo>(),resumeTick){value=withContext(Dispatchers.IO){loadApps(ctx)}}
 val dockApps=dock.mapNotNull{p->apps.find{it.pkg==p}}.ifEmpty{apps.take(4)};val top=remember(apps,usageRev){apps.filter{prefs.getInt("n_"+it.pkg,0)>0}.sortedByDescending{prefs.getInt("n_"+it.pkg,0)}.take(4)}
 val pager=rememberPagerState(pageCount={2});LaunchedEffect(homeTick){if(homeTick>0)pager.animateScrollToPage(0)};BackHandler(pager.currentPage==1){scope.launch{pager.animateScrollToPage(0)}};val p=(pager.currentPage+pager.currentPageOffsetFraction).coerceIn(0f,1f)
 val launch:(AppInfo)->Unit={app->prefs.edit().putInt("n_"+app.pkg,prefs.getInt("n_"+app.pkg,0)+1).apply();usageRev++;ctx.packageManager.getLaunchIntentForPackage(app.pkg)?.let{ctx.startActivity(it)}}
 val longPress:(AppInfo)->Unit={haptic.performHapticFeedback(HapticFeedbackType.LongPress);selected=it}
 val tr=rememberInfiniteTransition(label="glow");val glow by tr.animateFloat(0f,1f,infiniteRepeatable(tween(9000,easing=LinearEasing),RepeatMode.Reverse),label="g")
 Box(Modifier.fillMaxSize().drawBehind{drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha=.30f+.5f*p),Color(0xFF0B0620).copy(alpha=.55f+.35f*p))));val c=Offset(size.width*(.15f+.7f*glow),size.height*.28f);drawCircle(Brush.radialGradient(listOf(theme.a.copy(alpha=.30f),Color.Transparent),c,size.width*.8f),size.width*.8f,c);val c2=Offset(size.width*(.85f-.7f*glow),size.height*.8f);drawCircle(Brush.radialGradient(listOf(theme.b.copy(alpha=.30f),Color.Transparent),c2,size.width*.7f),size.width*.7f,c2)}){
  VerticalPager(pager,Modifier.fillMaxSize()){page->if(page==0)Box(Modifier.graphicsLayer{alpha=1f-p;scaleX=1f-.1f*p;scaleY=1f-.1f*p}){HomePage(dockApps,top,shape,iconSize,duo,theme,apps.size,{scope.launch{pager.animateScrollToPage(1)}},{showSettings=true},{showAssistant=true},launch,longPress)}else Box(Modifier.graphicsLayer{alpha=p;translationY=(1f-p)*120f}){DrawerPage(apps,shape,iconSize,duo,theme,{app->launch(app);scope.launch{pager.scrollToPage(0)}},longPress)}}
 }
 selected?.let{app->val pinned=app.pkg in dock;AlertDialog(onDismissRequest={selected=null},title={Text(app.label)},confirmButton={TextButton(onClick={dock=if(pinned)dock-app.pkg else(dock+app.pkg).takeLast(5);prefs.edit().putString("dock",dock.joinToString(",")).apply();selected=null}){Text(if(pinned)"Tirar do dock" else "Fixar no dock")}},dismissButton={TextButton(onClick={ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${app.pkg}")));selected=null}){Text("Info do app")}})}
 if(showAssistant) AssistantDialog(apps,theme,prefs,{showAssistant=false}){app->showAssistant=false;launch(app)}
 if(showSettings) AlertDialog(onDismissRequest={showSettings=false},title={Text("Personalizar")},confirmButton={TextButton(onClick={showSettings=false}){Text("Pronto")}},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
 Text("Tema");Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){Themes.forEachIndexed{i,th->Box(Modifier.size(36.dp).clip(CircleShape).background(Brush.linearGradient(listOf(th.a,th.b))).border(if(i==themeIdx)3.dp else 0.dp,Color.White,CircleShape).clickable{themeIdx=i;prefs.edit().putInt("theme",i).apply()})}}
 Text("Formato dos ícones");Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){IconShape.values().forEach{s->FilterChip(selected=shape==s,onClick={shape=s;prefs.edit().putString("shape",s.name).apply()},label={Text(s.label,fontSize=12.sp)})}}
 Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween,modifier=Modifier.fillMaxWidth()){Text("Ícones duotone");Switch(checked=duo,onCheckedChange={duo=it;prefs.edit().putBoolean("duo",it).apply()})};Text("Tamanho dos ícones");Slider(value=iconSize,onValueChange={iconSize=it;prefs.edit().putFloat("size",it).apply()},valueRange=44f..76f)
 }})
}
@Composable fun HomePage(dockApps:List<AppInfo>,top:List<AppInfo>,shape:IconShape,size:Float,duo:Boolean,theme:Theme,count:Int,openDrawer:()->Unit,openSettings:()->Unit,openAssistant:()->Unit,launch:(AppInfo)->Unit,onLong:(AppInfo)->Unit){
 val ctx=LocalContext.current;val now by produceState(LocalDateTime.now()){while(true){value=LocalDateTime.now();delay(1000)}};val battery by produceState(0){val bm=ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager;while(true){value=bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);delay(30000)}}
 val date=now.format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM",Locale("pt","BR"))).replaceFirstChar{it.uppercase()};val greeting=when(now.hour){in 5..11->"Bom dia";in 12..17->"Boa tarde";else->"Boa noite"}
 Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp)){
 Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Row(Modifier.weight(1f).glass(RoundedCornerShape(28.dp),theme.a).clickable{openDrawer()}.padding(horizontal=18.dp,vertical=14.dp)){Text("🔍  Buscar apps…",color=Color.White.copy(alpha=.6f),fontSize=15.sp)};Text("🎤",fontSize=22.sp,modifier=Modifier.clickable{openAssistant()}.padding(10.dp));Text("⚙",fontSize=24.sp,color=Color.White.copy(alpha=.8f),modifier=Modifier.clickable{openSettings()}.padding(12.dp))}
 Spacer(Modifier.height(20.dp));Text(now.format(DateTimeFormatter.ofPattern("HH:mm")),fontSize=96.sp,fontWeight=FontWeight.Thin,style=LocalTextStyle.current.copy(brush=Brush.linearGradient(listOf(Color.White,theme.a,theme.b))))
 Text(date,fontSize=18.sp,color=theme.a);Spacer(Modifier.height(20.dp))
 Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(26.dp),theme.a).padding(18.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Column{Text(greeting,fontSize=22.sp,color=Color.White,fontWeight=FontWeight.Medium);Text("$count apps instalados",fontSize=13.sp,color=Color.White.copy(alpha=.6f))}
 Box(Modifier.size(68.dp),contentAlignment=Alignment.Center){Canvas(Modifier.fillMaxSize()){val w=7.dp.toPx();val tl=Offset(w,w);val sz=Size(this.size.width-2*w,this.size.height-2*w);drawArc(color=Color.White.copy(alpha=.12f),startAngle=-90f,sweepAngle=360f,useCenter=false,topLeft=tl,size=sz,style=Stroke(w,cap=StrokeCap.Round));drawArc(brush=Brush.sweepGradient(listOf(theme.a,theme.b,theme.a)),startAngle=-90f,sweepAngle=360f*battery/100f,useCenter=false,topLeft=tl,size=sz,style=Stroke(w,cap=StrokeCap.Round))};Text("$battery%",fontSize=13.sp,color=Color.White,fontWeight=FontWeight.Medium)}}
 if(top.isNotEmpty()){Spacer(Modifier.height(14.dp));Text("Mais usados",fontSize=12.sp,color=Color.White.copy(alpha=.55f),modifier=Modifier.padding(start=6.dp,bottom=4.dp));Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(26.dp),theme.a).padding(vertical=8.dp),horizontalArrangement=Arrangement.SpaceEvenly){top.forEach{AppIcon(it,shape,(size*.85f).dp,theme,duo,true,{launch(it)},{onLong(it)})}}}
 Spacer(Modifier.weight(1f));Text("⌃  deslize para ver os apps",color=Color.White.copy(alpha=.5f),fontSize=12.sp,modifier=Modifier.align(Alignment.CenterHorizontally).clickable{openDrawer()}.padding(8.dp));Spacer(Modifier.height(8.dp));Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(34.dp),theme.a).padding(vertical=12.dp),horizontalArrangement=Arrangement.SpaceEvenly){dockApps.forEach{AppIcon(it,shape,size.dp,theme,duo,false,{launch(it)},{onLong(it)})}}
 }
}
@Composable fun DrawerPage(apps:List<AppInfo>,shape:IconShape,size:Float,duo:Boolean,theme:Theme,launch:(AppInfo)->Unit,onLong:(AppInfo)->Unit){
 var query by remember{mutableStateOf("")};val list=apps.filter{it.label.contains(query,ignoreCase=true)}
 Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal=16.dp)){Spacer(Modifier.height(16.dp));OutlinedTextField(value=query,onValueChange={query=it},singleLine=true,placeholder={Text("Buscar app…",color=Color.White.copy(alpha=.4f))},shape=RoundedCornerShape(28.dp),modifier=Modifier.fillMaxWidth(),colors=OutlinedTextFieldDefaults.colors(focusedTextColor=Color.White,unfocusedTextColor=Color.White,focusedBorderColor=theme.a,unfocusedBorderColor=theme.a.copy(alpha=.4f),cursorColor=theme.a,focusedContainerColor=Color.Black.copy(alpha=.3f),unfocusedContainerColor=Color.Black.copy(alpha=.3f)));Spacer(Modifier.height(12.dp));LazyVerticalGrid(GridCells.Adaptive(size.dp+28.dp),Modifier.fillMaxSize()){items(list,key={it.pkg}){app->AppIcon(app,shape,size.dp,theme,duo,true,{launch(app)},{onLong(app)})}}}
}
@Composable fun AppIcon(app:AppInfo,shape:IconShape,size:Dp,theme:Theme,duo:Boolean,showLabel:Boolean,onClick:()->Unit,onLong:()->Unit){
 val s=shape.toShape();val src=remember{MutableInteractionSource()};val pressed by src.collectIsPressedAsState();val scale by animateFloatAsState(if(pressed).8f else 1f,spring(Spring.DampingRatioMediumBouncy),label="press")
 Column(Modifier.graphicsLayer{scaleX=scale;scaleY=scale}.combinedClickable(interactionSource=src,indication=null,onClick=onClick,onLongClick=onLong).padding(6.dp),horizontalAlignment=Alignment.CenterHorizontally){Image(app.icon,app.label,Modifier.size(size).clip(s).background(Color.White.copy(alpha=.08f)).then(if(duo)Modifier.duotone(theme.a,theme.b)else Modifier).border(1.dp,theme.a.copy(alpha=.45f),s));if(showLabel){Spacer(Modifier.height(4.dp));Text(app.label,color=Color.White,fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.width(size+12.dp))}}
}
