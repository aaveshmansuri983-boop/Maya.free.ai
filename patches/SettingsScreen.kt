package com.maya.ai.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.maya.ai.MayaApplication
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val prefs = remember { MayaApplication.instance.preferencesManager }
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("http://127.0.0.1:3001/v1") }
    var model by remember { mutableStateOf("gemini-3.8-flash") }
    var saved by remember { mutableStateOf(false) }
    var showKey by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        key = prefs.openAIApiKey.first().orEmpty()
        baseUrl = prefs.openAIBaseUrl.first()
        model = prefs.openAIModel.first()
    }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("MAYA AI • CONTROL CENTER", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary) }
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("FreeLLMAPI connection", style = MaterialTheme.typography.titleMedium)
                    Text("Termux API server • no paid subscription required", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value=baseUrl, onValueChange={baseUrl=it; saved=false}, modifier=Modifier.fillMaxWidth(), label={Text("Base URL")}, singleLine=true)
                    OutlinedTextField(value=key, onValueChange={key=it; saved=false}, modifier=Modifier.fillMaxWidth(), label={Text("Unified API key")}, singleLine=true, visualTransformation=if(showKey) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation())
                    Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(checked=showKey, onCheckedChange={showKey=it})
                        Text("Show key")
                    }
                    OutlinedTextField(value=model, onValueChange={model=it; saved=false}, modifier=Modifier.fillMaxWidth(), label={Text("Primary model ID")}, singleLine=true)
                    Text("Set this to the exact model ID shown by your FreeLLMAPI dashboard. Gemini 3.8 Flash is your intended primary.", style=MaterialTheme.typography.bodySmall)
                    Button(onClick={
                        scope.launch {
                            prefs.setOpenAIApiKey(key.trim())
                            prefs.setOpenAIBaseUrl(baseUrl.trim().trimEnd('/'))
                            prefs.setOpenAIModel(model.trim())
                            prefs.setCurrentAIProvider(com.maya.ai.data.models.AIProvider.OPENAI)
                            saved=true
                        }
                    }, modifier=Modifier.fillMaxWidth(), enabled=baseUrl.startsWith("http") && model.isNotBlank()) { Text("Save connection") }
                    if(saved) Text("Settings saved on this device.", color=MaterialTheme.colorScheme.primary)
                }
            }
        }
        item {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("Voice & device", style=MaterialTheme.typography.titleMedium)
                    Text("Voice input uses Android Speech Recognition. Spoken replies use the phone's built-in Text-to-Speech engine.", style=MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("Keep Termux FreeLLMAPI running on port 3001 while using the local API URL.", style=MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
