package com.google.ai.edge.samples.rag

import android.Manifest
import android.content.pm.PackageManager
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslationScreen(navController: NavController, chatViewModel: ChatViewModel) {
    var expanded by remember { mutableStateOf(false) }
    var selectedLanguage by remember { mutableStateOf("English") }
    var translatedText by remember { mutableStateOf("") }
    var isTranslating by remember { mutableStateOf(false) }
    var isTextInputMode by remember { mutableStateOf(false) } // New: Toggle between voice and text input
    var inputText by remember { mutableStateOf("") } // New: Text input for manual translation
    val languages = listOf("English", "Chinese", "Arabic", "Farsi", "Kurdish", "Turkish", "Urdu")
    val context = LocalContext.current

    // Reuse existing ChatViewModel to avoid loading model twice
    // This ensures we only have one model instance in memory
    val coroutineScope = rememberCoroutineScope()
    
    // TTS (Text-to-Speech) setup
    var textToSpeech by remember { mutableStateOf<TextToSpeech?>(null) }
    var isTtsReady by remember { mutableStateOf(false) }
    
    // Initialize TTS
    DisposableEffect(Unit) {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsReady = true
                Log.d("TranslationScreen", "🔊 TTS initialized successfully")
            } else {
                Log.e("TranslationScreen", "❌ TTS initialization failed")
            }
        }
        
        onDispose {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
            Log.d("TranslationScreen", "🔊 TTS disposed")
        }
    }
    
    // Function to speak text
    fun speakText(text: String, language: String) {
        if (!isTtsReady || textToSpeech == null) {
            Log.w("TranslationScreen", "⚠️ TTS not ready")
            return
        }
        
        // Set language for TTS - using most common regional codes
        val locale = when (language.lowercase()) {
            "english" -> Locale.ENGLISH
            "chinese" -> Locale("zh", "CN") // Mandarin Chinese (Simplified)
            "arabic" -> Locale("ar", "SA") // Arabic (Saudi Arabia) - most common
            "farsi" -> Locale("fa", "IR") // Persian (Iran)
            "kurdish" -> Locale("ku") // Kurdish (limited support)
            "turkish" -> Locale("tr", "TR") // Turkish (Turkey)
            "urdu" -> Locale("ur", "PK") // Urdu (Pakistan) - most common
            else -> Locale.ENGLISH
        }
        
        val result = textToSpeech?.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w("TranslationScreen", "⚠️ Language $language not supported, using English")
            textToSpeech?.setLanguage(Locale.ENGLISH)
        }
        
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        Log.d("TranslationScreen", "🔊 Speaking: $text")
    }
    
    // Audio permission launcher
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d("TranslationScreen", "🎙️ Audio permission granted")
        } else {
            Log.w("TranslationScreen", "⚠️ Audio permission denied")
        }
    }

    Scaffold(
        topBar = {
            FieldCommAppBar(
                title = "Field-Comm",
                actionIcon = Icons.Filled.Chat,
                actionContentDescription = "Chatbot Mode",
                actionText = "Chatbot Mode",
                onActionClick = { navController.navigate("chatbot") }
            )
        },
        containerColor = MaterialTheme.colorScheme.onPrimary
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 2.dp)
        ) {
            // Language selector
            LanguageSelector(
                selectedLanguage = selectedLanguage,
                languages = languages,
                expanded = expanded,
                onExpandedChange = { expanded = it },
                onLanguageSelected = { language ->
                    selectedLanguage = language
                    expanded = false
                }
            )
            
            // Translation result display - Enhanced with larger, more prominent text and speaker
            if (translatedText.isNotBlank() || isTranslating) {
                TranslationResultCard(
                    translatedText = translatedText,
                    isTranslating = isTranslating,
                    selectedLanguage = selectedLanguage,
                    onSpeakText = { speakText(translatedText, selectedLanguage) },
                    isTtsReady = isTtsReady
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Audio states for functionality (not display)
            val isRecording by chatViewModel.isRecording
            val transcriptionInProgress by chatViewModel.transcriptionInProgress
            val audioInitialized by chatViewModel.audioInitialized
            
            // LLM initialization status
            val isLlmInitializing by chatViewModel.isLlmInitializing
            val isLlmInitialized by chatViewModel.isLlmInitialized
            val llmInitializationError by chatViewModel.llmInitializationError
            val recordingDuration by chatViewModel.recordingDuration

            StatusIndicators(
                isLlmInitializing = isLlmInitializing,
                isLlmInitialized = isLlmInitialized,
                llmInitializationError = llmInitializationError,
                isRecording = isRecording,
                recordingDuration = recordingDuration,
                transcriptionInProgress = transcriptionInProgress,
                isTranslating = isTranslating
            )

            // Input mode toggle and input area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                // Input area based on mode
                if (isTextInputMode) {
                    // Text input mode - Similar to chat input but simplified
                    TranslationTextInput(
                        text = inputText,
                        onTextChange = { inputText = it },
                        onTranslate = { text ->
                            if (text.isNotBlank()) {
                                translateText(
                                    text = text,
                                    selectedLanguage = selectedLanguage,
                                    chatViewModel = chatViewModel,
                                    coroutineScope = coroutineScope,
                                    onTranslating = { isTranslating = true; translatedText = "" },
                                    onProgress = { translatedText = it },
                                    onComplete = { 
                                        isTranslating = false
                                        // Auto-speak translation when complete
                                        if (translatedText.isNotBlank() && isTtsReady) {
                                            speakText(translatedText, selectedLanguage)
                                        }
                                    }
                                )
                            }
                        },
                        enabled = !isTranslating,
                        onSwitchToVoice = { isTextInputMode = false } // Add callback to switch to voice mode
                    )
                } else {
                    // Voice input mode - Press and hold microphone with keyboard toggle
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        PressAndHoldMicrophoneButton(
                            audioInitialized = audioInitialized,
                            isRecording = isRecording,
                            isTranslating = isTranslating,
                            transcriptionInProgress = transcriptionInProgress,
                            isLlmInitialized = isLlmInitialized,
                            chatViewModel = chatViewModel,
                            context = context,
                            audioPermissionLauncher = audioPermissionLauncher,
                            onTranscriptionResult = { transcription ->
                                if (transcription.isNotBlank()) {
                                    translateText(
                                        text = transcription,
                                        selectedLanguage = selectedLanguage,
                                        chatViewModel = chatViewModel,
                                        coroutineScope = coroutineScope,
                                        onTranslating = { isTranslating = true; translatedText = "" },
                                        onProgress = { translatedText = it },
                                        onComplete = { 
                                            isTranslating = false
                                            // Auto-speak translation when complete
                                            if (translatedText.isNotBlank() && isTtsReady) {
                                                speakText(translatedText, selectedLanguage)
                                            }
                                        }
                                    )
                                }
                            }
                        )
                        
                        // Text mode toggle button - positioned at bottom right of voice button
                        IconButton(
                            onClick = { isTextInputMode = true },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = (-8).dp, y = (-16).dp) // Move up by using negative y offset
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                                    shape = CircleShape
                                )
                                .size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Keyboard,
                                contentDescription = "Switch to text input mode",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TranslationResultCard(
    translatedText: String,
    isTranslating: Boolean,
    selectedLanguage: String,
    onSpeakText: (() -> Unit)? = null,
    isTtsReady: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header with language and speaker icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Translation to $selectedLanguage:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // Speaker icon - only show when TTS is ready and text is available
                if (isTtsReady && translatedText.isNotBlank() && !isTranslating) {
                    IconButton(
                        onClick = { onSpeakText?.invoke() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VolumeUp,
                            contentDescription = "Speak translation",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (isTranslating) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Translating...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            if (translatedText.isNotBlank()) {
                SelectionContainer {
                    Text(
                        text = translatedText,
                        style = MaterialTheme.typography.headlineSmall, // Larger text for better visibility
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 32.sp, // Increased line height for better readability
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TranslationTextInput(
    text: String,
    onTextChange: (String) -> Unit,
    onTranslate: (String) -> Unit,
    enabled: Boolean,
    onSwitchToVoice: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.onPrimary
        ),
        shape = RoundedCornerShape(28.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Text input
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = {
                    Text(
                        "Type text to translate...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                    disabledBorderColor = androidx.compose.ui.graphics.Color.Transparent, // Fix border during translation
                    focusedContainerColor = MaterialTheme.colorScheme.onPrimary,
                    unfocusedContainerColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.onPrimary, // Keep same background when disabled
                    disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f) // Slightly dimmed text when disabled
                ),
                minLines = 2,
                maxLines = 4,
                enabled = enabled
            )

            // Bottom action buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Microphone icon at bottom left inside container
                IconButton(
                    onClick = onSwitchToVoice,
                    enabled = enabled
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = "Switch to voice input",
                        tint = if (enabled)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
                
                // Send button at bottom right (changed from translate icon)
                IconButton(
                    onClick = { onTranslate(text) },
                    enabled = enabled && text.isNotBlank()
                ) {
                    Icon(
                        imageVector = Icons.Filled.Send, // Changed from Translate to Send
                        contentDescription = "Send text for translation",
                        tint = if (enabled && text.isNotBlank())
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PressAndHoldMicrophoneButton(
    audioInitialized: Boolean,
    isRecording: Boolean,
    isTranslating: Boolean,
    transcriptionInProgress: Boolean,
    isLlmInitialized: Boolean, // Use LLM initialization instead
    chatViewModel: ChatViewModel,
    context: android.content.Context,
    audioPermissionLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    onTranscriptionResult: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Status indicators - show recording duration and transcription progress
        if (!isTranslating && isLlmInitialized && !isRecording && !transcriptionInProgress) {
            Text(
                text = "Press and hold to speak",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
        
        // Press and hold microphone button
        FloatingActionButton(
            onClick = { 
                // Handle simple tap as backup
                if (!audioInitialized) {
                    Log.d("TranslationScreen", "⚠️ Audio components not initialized yet")
                    return@FloatingActionButton
                }
                
                when (PackageManager.PERMISSION_GRANTED) {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) -> {
                        chatViewModel.toggleAudioRecording(onTranscriptionResult)
                    }
                    else -> audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            modifier = Modifier
                .size(80.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            Log.d("TranslationScreen", "🎙️ Press detected")
                            // Start recording on press
                            if (!audioInitialized) {
                                Log.d("TranslationScreen", "⚠️ Audio components not initialized yet")
                                return@detectTapGestures
                            }
                            
                            when (PackageManager.PERMISSION_GRANTED) {
                                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) -> {
                                    if (!isRecording) {
                                        Log.d("TranslationScreen", "🎙️ Starting recording...")
                                        chatViewModel.startAudioRecording(onTranscriptionResult)
                                    }
                                    
                                    // Wait for release
                                    val released = tryAwaitRelease()
                                    Log.d("TranslationScreen", "🎙️ Release detected: $released")
                                    
                                    // Stop recording on release
                                    if (isRecording) {
                                        Log.d("TranslationScreen", "🛑 Stopping recording...")
                                        chatViewModel.stopAudioRecording()
                                    }
                                }
                                else -> {
                                    Log.d("TranslationScreen", "📋 Requesting audio permission...")
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        }
                    )
                },
            containerColor = when {
                isRecording -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.primary
            },
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(
                imageVector = if (isRecording) Icons.Filled.FiberManualRecord else Icons.Filled.Mic,
                contentDescription = if (isRecording) "Recording... Release to stop" else "Press and hold to speak",
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

@Composable
private fun LanguageSelector(
    selectedLanguage: String,
    languages: List<String>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onLanguageSelected: (String) -> Unit
) {
    var textFieldWidth by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onExpandedChange(!expanded) }
                .onGloballyPositioned { coordinates ->
                    textFieldWidth = with(density) { coordinates.size.width.toDp() }
                },
            value = selectedLanguage,
            onValueChange = {},
            readOnly = true,
            enabled = false,
            label = { Text("Translate to") },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = "Dropdown arrow"
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier
                .wrapContentHeight()
                .width(textFieldWidth) // Match the text field width exactly
        ) {
            languages.forEach { language ->
                DropdownMenuItem(
                    text = { Text(language) },
                    onClick = { onLanguageSelected(language) }
                )
            }
        }
    }
}

/**
 * Helper function to translate text using the existing model instance from ChatViewModel
 * This avoids loading the model twice and reuses the same RagPipeline
 */
private fun translateText(
    text: String,
    selectedLanguage: String,
    chatViewModel: ChatViewModel,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onTranslating: () -> Unit,
    onProgress: (String) -> Unit,
    onComplete: () -> Unit
) {
    if (text.isBlank()) return
    
    onTranslating()
    
    // Use the provided coroutine scope
    coroutineScope.launch {
        try {
            Log.d("TranslationScreen", "🌐 Starting translation via existing model")
            
            // Use the public translation method to avoid reflection
            chatViewModel.translateText(
                text = text,
                targetLanguage = selectedLanguage,
                callback = { response, done ->
                    coroutineScope.launch {
                        onProgress(response.text)
                        if (done) {
                            onComplete()
                            Log.d("TranslationScreen", "✅ Translation completed")
                        }
                    }
                }
            )
            
        } catch (e: Exception) {
            Log.e("TranslationScreen", "❌ Translation failed", e)
            onProgress("Translation failed: ${e.message}")
            onComplete()
        }
    }
} 