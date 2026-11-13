package com.maya.ai.presentation.chat

import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.maya.ai.MayaApplication
import com.maya.ai.ai.AIManager
import com.maya.ai.data.models.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun ChatScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { MayaApplication.instance.preferencesManager }
    val ai = remember { AIManager(context.applicationContext, prefs) }
    var draft by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(listOf<Message>()) }
    var busy by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    var speaking by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("READY • MAYA ONLINE") }
    val listState = rememberLazyListState()
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(Unit) {
        tts = TextToSpeech(context) { result ->
            if (result == TextToSpeech.SUCCESS) tts?.language = Locale.getDefault()
        }
        onDispose {
            recognizer?.destroy()
            tts?.stop()
            tts?.shutdown()
        }
    }

    fun send(text: String) {
        if (text.isBlank() || busy) return
        val clean = text.trim()
        messages = messages + Message(content = clean, isFromUser = true)
        draft = ""
        busy = true
        status = "PROCESSING REQUEST"
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                ai.sendMessage(clean, messages.dropLast(1))
            }
            result.fold(
                onSuccess = { answer ->
                    messages = messages + Message(content = answer, isFromUser = false)
                    status = "RESPONSE RECEIVED"
                    tts?.speak(answer, TextToSpeech.QUEUE_FLUSH, null, "maya_reply")
                },
                onFailure = { error ->
                    val detail = error.message ?: "Unknown connection error"
                    messages = messages + Message(content = "Connection error: $detail\n\nOpen Settings and verify your FreeLLMAPI URL, key and model.", isFromUser = false)
                    status = "CONNECTION ERROR"
                }
            )
            busy = false
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF030B16))) {
        Column(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MayaOrb(active = listening || speaking || busy, modifier = Modifier.size(188.dp))
            Text(status, color = Color(0xFF48E9F5), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text("MAYA AI  •  FREE LLM CORE", color = Color(0xFF91AEBB), style = MaterialTheme.typography.labelSmall)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty()) item {
                Text("Hello. I'm Maya. Type a message or tap the microphone to talk.",
                    color = Color(0xFF9DBBC8), modifier = Modifier.fillMaxWidth().padding(18.dp))
            }
            items(messages) { message -> MessageBubble(message) }
            if (busy) item { Text("Maya is thinking…", color = Color(0xFF52DDEB), modifier = Modifier.padding(8.dp)) }
        }
        Row(
            Modifier.fillMaxWidth().background(Color(0xFF071725)).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = {
                if (listening) {
                    recognizer?.stopListening()
                    listening = false
                    status = "READY • MAYA ONLINE"
                } else if (SpeechRecognizer.isRecognitionAvailable(context)) {
                    val sr = SpeechRecognizer.createSpeechRecognizer(context)
                    recognizer = sr
                    sr.setRecognitionListener(object : android.speech.RecognitionListener {
                        override fun onReadyForSpeech(params: android.os.Bundle?) { status = "LISTENING…" }
                        override fun onBeginningOfSpeech() { status = "HEARING YOU" }
                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() { listening = false }
                        override fun onError(error: Int) { listening = false; status = "VOICE INPUT ERROR ($error)" }
                        override fun onResults(results: android.os.Bundle?) {
                            listening = false
                            val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                            if (!spoken.isNullOrBlank()) send(spoken) else status = "NO SPEECH DETECTED"
                        }
                        override fun onPartialResults(partialResults: android.os.Bundle?) {}
                        override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
                    })
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    }
                    listening = true
                    sr.startListening(intent)
                } else status = "SPEECH RECOGNIZER UNAVAILABLE"
            }) {
                Icon(if (listening) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = "Voice input", tint = Color(0xFF53EAF5))
            }
            OutlinedTextField(
                value = draft, onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message Maya…", color = Color(0xFF7895A5)) },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF25D9E8), unfocusedBorderColor = Color(0xFF245064),
                    cursorColor = Color(0xFF25D9E8)
                )
            )
            IconButton(onClick = { send(draft) }, enabled = draft.isNotBlank() && !busy) {
                Icon(Icons.Default.Send, contentDescription = "Send", tint = Color(0xFF53EAF5))
            }
        }
    }
}

@Composable
private fun MayaOrb(active: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "orb")
    val rotation by transition.animateFloat(0f, 360f,
        infiniteRepeatable(tween(if (active) 2200 else 6500, easing = LinearEasing)), label = "rotation")
    val pulse by transition.animateFloat(0.88f, if (active) 1.08f else 0.98f,
        infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse")
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension * .37f * pulse
        drawCircle(Brush.radialGradient(listOf(Color(0xFF55FFFF).copy(alpha=.48f), Color(0xFF087BBA).copy(alpha=.18f), Color.Transparent), c, radius*1.55f), radius*1.55f)
        drawCircle(Brush.radialGradient(listOf(Color(0xFF0A7898), Color(0xFF031C31), Color(0xFF010A15)), c, radius), radius)
        drawCircle(Color(0xFF49F4FF).copy(alpha=.85f), radius, style= androidx.compose.ui.graphics.drawscope.Stroke(width=3.dp.toPx()))
        drawCircle(Color(0xFFB8FFFF).copy(alpha=.38f), radius*.82f, style= androidx.compose.ui.graphics.drawscope.Stroke(width=1.dp.toPx()))
        rotate(rotation, c) {
            drawOval(Color(0xFF3DECF7).copy(alpha=.9f), topLeft=Offset(c.x-radius*1.28f,c.y-radius*.28f), size=androidx.compose.ui.geometry.Size(radius*2.56f,radius*.56f), style=androidx.compose.ui.graphics.drawscope.Stroke(width=2.dp.toPx()))
            drawCircle(Color.White.copy(alpha=.9f), radius*.035f, Offset(c.x+radius*1.28f,c.y))
        }
        for (i in 0..11) {
            val a = Math.toRadians((i*30.0)+rotation)
            val p = Offset(c.x + kotlin.math.cos(a).toFloat()*radius*1.16f, c.y + kotlin.math.sin(a).toFloat()*radius*1.16f)
            drawCircle(Color(0xFF5CFAFF).copy(alpha=if(active).9f else .48f), if(active) 3.dp.toPx() else 1.8.dp.toPx(), p)
        }
        drawCircle(Color.White.copy(alpha=.9f), radius*.045f, c)
    }
}

@Composable
fun MessageBubble(message: Message) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.isFromUser) Arrangement.End else Arrangement.Start) {
        Surface(
            modifier = Modifier.widthIn(max = 310.dp),
            shape = RoundedCornerShape(18.dp),
            color = if (message.isFromUser) Color(0xFF07546B) else Color(0xFF102B3C)
        ) {
            Text(message.content, Modifier.padding(13.dp), color = Color(0xFFE4FAFF))
        }
    }
}
