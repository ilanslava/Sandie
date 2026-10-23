package com.futurelauncher

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.*
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

suspend fun askGpt(key:String,history:List<Pair<String,String>>):String=withContext(Dispatchers.IO){
 try{
  val msgs=JSONArray().put(JSONObject().put("role","system").put("content","Você é a assistente de voz de um launcher Android. Responda em português do Brasil, de forma curta e natural (até 3 frases), sem markdown."))
  history.takeLast(8).forEach{msgs.put(JSONObject().put("role",it.first).put("content",it.second))}
  val body=JSONObject().put("model","gpt-4o-mini").put("messages",msgs).toString()
  val c=URL("https://api.openai.com/v1/chat/completions").openConnection() as HttpURLConnection
  c.requestMethod="POST";c.setRequestProperty("Authorization","Bearer $key");c.setRequestProperty("Content-Type","application/json");c.connectTimeout=15000;c.readTimeout=30000;c.doOutput=true;c.outputStream.use{it.write(body.toByteArray())}
  val code=c.responseCode;val txt=(if(code in 200..299)c.inputStream else c.errorStream).bufferedReader().use{it.readText()}
  if(code !in 200..299) "Erro $code: "+runCatching{JSONObject(txt).getJSONObject("error").getString("message")}.getOrDefault("falha na API")
  else JSONObject(txt).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
 }catch(e:Exception){"Não consegui falar com o GPT. Confere a internet."}
}

@Composable fun AssistantDialog(apps:List<AppInfo>,theme:Theme,prefs:SharedPreferences,onClose:()->Unit,openApp:(AppInfo)->Unit){
 val ctx=LocalContext.current;val scope= rememberCoroutineScope();var key by remember{mutableStateOf(prefs.getString("gpt_key","")?:"")};var keyInput by remember{mutableStateOf("")};var editKey by remember{mutableStateOf(false)};var status by remember{mutableStateOf("Toque no microfone")};var listening by remember{mutableStateOf(false)}
 val messages= remember{mutableStateListOf<Pair<String,String>>()};val listState= rememberLazyListState();val ttsReady= remember{mutableStateOf(false)};val tts= remember{TextToSpeech(ctx){ttsReady.value=it==TextToSpeech.SUCCESS}}
 LaunchedEffect(ttsReady.value){if(ttsReady.value)tts.language=Locale("pt","BR")}
 val sr= remember{SpeechRecognizer.createSpeechRecognizer(ctx)};DisposableEffect(Unit){onDispose{sr.destroy();tts.stop();tts.shutdown()}}
 LaunchedEffect(messages.size){if(messages.isNotEmpty())listState.animateScrollToItem(messages.size-1)}
 fun reply(text:String){messages.add("assistant" to text);status="Toque no microfone";tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,"reply")}
 fun handle(text:String){messages.add("user" to text);val m=Regex("^(abrir|abre|abra|iniciar)\\s+(o |a )?(.+)$",RegexOption.IGNORE_CASE).find(text.trim());if(m!=null){val name=m.groupValues[3].lowercase().trim();val app=apps.firstOrNull{it.label.lowercase().contains(name)};if(app!=null){reply("Abrindo ${app.label}");openApp(app);return}};status="Pensando…";scope.launch{reply(askGpt(key,messages.toList()))}}
 LaunchedEffect(sr){sr.setRecognitionListener(object:RecognitionListener{
 override fun onReadyForSpeech(params:Bundle?){}
 override fun onBeginningOfSpeech(){}
 override fun onRmsChanged(rmsdB:Float){}
 override fun onBufferReceived(buffer:ByteArray?){}
 override fun onEndOfSpeech(){listening=false}
 override fun onEvent(eventType:Int,params:Bundle?){}
 override fun onPartialResults(partialResults:Bundle?){partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let{status="“$it”"}}
 override fun onError(error:Int){listening=false;status="Não entendi. Toque e tente de novo."}
 override fun onResults(results:Bundle?){listening=false;val t=results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull();if(t.isNullOrBlank())status="Não entendi. Toque e tente de novo." else handle(t)}
 })}
 fun startListening(){if(!SpeechRecognizer.isRecognitionAvailable(ctx)){status="Reconhecimento de voz indisponível";return};tts.stop();val i=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_LANGUAGE,"pt-BR").putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);listening=true;status="Ouvindo…";sr.startListening(i)}
 val perm=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){if(it)startListening()else status="Preciso da permissão do microfone"}
 val pulse by rememberInfiniteTransition(label="pulse").animateFloat(1f,1.18f,infiniteRepeatable(tween(700),RepeatMode.Reverse),label="scale")
 Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)){
  Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xF005060F),Color(0xF50B0620)))).statusBarsPadding().navigationBarsPadding().padding(20.dp)){
   Spacer(Modifier.height(12.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text("Assistente",fontSize=26.sp,color=theme.a);Row{Text("🔑",fontSize=20.sp,modifier=Modifier.clickable{editKey=true}.padding(10.dp));Text("✕",fontSize=22.sp,color=Color.White,modifier=Modifier.clickable{onClose()}.padding(10.dp))}}
   if(key.isBlank()||editKey){Spacer(Modifier.height(12.dp));Text("Cole sua chave da API da OpenAI. Ela fica só no seu celular.",color=Color.White.copy(alpha=.7f),fontSize=13.sp);Spacer(Modifier.height(8.dp));OutlinedTextField(value=keyInput,onValueChange={keyInput=it},singleLine=true,placeholder={Text("sk-…")},modifier=Modifier.fillMaxWidth(),colors=OutlinedTextFieldDefaults.colors(focusedTextColor=Color.White,unfocusedTextColor=Color.White,focusedBorderColor=theme.a,cursorColor=theme.a));TextButton(onClick={if(keyInput.isNotBlank()){key=keyInput.trim();prefs.edit().putString("gpt_key",key).apply();keyInput="";editKey=false}}){Text("Salvar chave")}}
   LazyColumn(Modifier.weight(1f).padding(vertical=12.dp),state=listState,verticalArrangement=Arrangement.spacedBy(10.dp)){itemsIndexed(messages){_,m->val mine=m.first=="user";Row(Modifier.fillMaxWidth(),horizontalArrangement=if(mine)Arrangement.End else Arrangement.Start){Text(m.second,color=Color.White,fontSize=16.sp,modifier=Modifier.widthIn(max=300.dp).clip(RoundedCornerShape(20.dp)).background(if(mine)theme.a.copy(alpha=.28f)else Color.White.copy(alpha=.1f)).padding(14.dp))}}}
   Text(status,color=Color.White.copy(alpha=.7f),fontSize=14.sp,modifier=Modifier.align(Alignment.CenterHorizontally));Spacer(Modifier.height(14.dp))
   Box(Modifier.align(Alignment.CenterHorizontally).size(88.dp).graphicsLayer{val s=if(listening)pulse else 1f;scaleX=s;scaleY=s}.clip(CircleShape).background(Brush.linearGradient(listOf(theme.a,theme.b))).clickable{if(key.isBlank())status="Cole a chave da API primeiro" else if(listening)sr.stopListening() else if(ContextCompat.checkSelfPermission(ctx,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)startListening()else perm.launch(Manifest.permission.RECORD_AUDIO)},contentAlignment=Alignment.Center){Text("🎤",fontSize=34.sp)}
   Spacer(Modifier.height(12.dp))
  }
 }
}
