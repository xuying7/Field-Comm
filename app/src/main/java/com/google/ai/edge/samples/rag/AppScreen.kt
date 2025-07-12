package com.google.ai.edge.samples.rag

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api

import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Send
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import dev.jeziellago.compose.markdowntext.MarkdownText
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

import androidx.compose.foundation.clickable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Objects
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.wrapContentSize
import androidx.core.content.ContextCompat
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import coil.compose.AsyncImage
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.filled.FiberManualRecord
import android.util.Log
import androidx.compose.runtime.DisposableEffect
import android.speech.tts.TextToSpeech
import java.util.Locale


@Composable
fun AppNavigation(chatViewModel: ChatViewModel) {
    val navController = rememberNavController()
    
    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            MainScreen(navController = navController)
        }
        composable("chatbot") {
            ChatScreen(
                viewModel = chatViewModel,
                navController = navController
            )
        }
        composable("translation") {
            TranslationScreen(
                navController = navController,
                chatViewModel = chatViewModel
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Field-Comm", 
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    ) 
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.onPrimary,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.onPrimary
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Push buttons up from center
            Spacer(modifier = Modifier.weight(1f))
            
            // Translation Mode Button
            ModeButton(
                title = "Translation Mode",
                description = "Translate spoken words between languages in real-time. ",
                icon = Icons.Filled.Translate,
                onClick = { navController.navigate("translation") }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Chatbot Mode Button  
            ModeButton(
                title = "Chatbot Mode",
                description = "Chat with AI assistant to gain information using voice, image or text. ",
                icon = Icons.Filled.Chat,
                onClick = { navController.navigate("chatbot") }
            )
            
            // More space at bottom to push buttons up
            Spacer(modifier = Modifier.weight(1.5f))
        }
    }
}

@Composable
private fun ModeButton(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp, 
            Color.Black.copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Surface(
                modifier = Modifier.size(60.dp),
                shape = CircleShape,
                color = Color.Black
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    tint = Color.White
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Text content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Black.copy(alpha = 0.7f),
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun FieldCommAppBar(
    title: String,
    actionIcon: ImageVector,
    actionContentDescription: String,
    actionText: String,
    onActionClick: () -> Unit
) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
        actions = {
            OutlinedButton(
                onClick = onActionClick,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.padding(end = 8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground)
            ) {
                Icon(
                    imageVector = actionIcon,
                    contentDescription = actionContentDescription,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(actionText)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.onPrimary,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            actionIconContentColor = MaterialTheme.colorScheme.onBackground
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel, navController: NavController) {
    val localFocusManager = LocalFocusManager.current
    val context = LocalContext.current
    var openBottomSheet by rememberSaveable { mutableStateOf(false) }
    val bottomSheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    var tempPhotoUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var selectedImageUris by remember { mutableStateOf<List<android.net.Uri>>(emptyList()) }
    

    val cameraLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                tempPhotoUri?.let {
                    selectedImageUris = selectedImageUris + it
                }
            }
            openBottomSheet = false
        }

    val galleryLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                selectedImageUris = selectedImageUris + uri
            }
            openBottomSheet = false
        }

    fun launchCamera() {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val imagePath = File(context.cacheDir, "images")
        imagePath.mkdirs()
        val file = File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            imagePath
        )
        tempPhotoUri = FileProvider.getUriForFile(
            context,
            "com.google.ai.edge.samples.rag.provider",
            file
        )
        cameraLauncher.launch(tempPhotoUri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            launchCamera()
        } else {
            openBottomSheet = false
        }
    }
    
    // Audio permission launcher
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted, the viewModel will handle the actual recording
            Log.d("ChatScreen", "🎙️ Audio permission granted")
        } else {
            Log.w("ChatScreen", "⚠️ Audio permission denied")
        }
    }

    Scaffold(
        topBar = {
            FieldCommAppBar(
                title = "Field-Comm",
                actionIcon = Icons.Filled.Translate,
                actionContentDescription = "Translation Mode",
                actionText = "Translation Mode",
                onActionClick = { navController.navigate("translation") }
            )
        },
        containerColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.pointerInput(Unit) {
            detectTapGestures(onTap = { localFocusManager.clearFocus() })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 2.dp)
                .imePadding()
        ) {
            if (viewModel.statistics.value.isNotBlank()) {
                Text(
                    text = viewModel.statistics.value,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(vertical = 2.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(2.dp))
            }

            val lazyColumnListState = rememberLazyListState()

            // Chat-style list: newest message at the bottom, stay pinned when keyboard opens
            LazyColumn(
                state = lazyColumnListState,
                modifier = Modifier.weight(1f),
                reverseLayout = true,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 4.dp)
            ) {
                items(items = viewModel.messages.asReversed()) { message ->
                    MessageView(messageData = message)
                }
            }

            // Scroll to newest (item 0 after reversing) whenever list grows
            LaunchedEffect(viewModel.messages.size) {
                if (viewModel.messages.isNotEmpty()) {
                    lazyColumnListState.scrollToItem(0)
                }
            }

            SendMessageView(
                viewModel = viewModel,
                onPhotoClick = { openBottomSheet = true },
                selectedImageUris = selectedImageUris,
                onClearImage = { uri -> selectedImageUris = selectedImageUris - uri },
                onAudioPermissionNeeded = { audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
            )
        }
    }

    if (openBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { openBottomSheet = false },
            sheetState = bottomSheetState,
            dragHandle = {
                Surface(
                    modifier = Modifier.padding(vertical = 8.dp).width(32.dp).height(4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {}
            },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ActionItem(
                    icon = Icons.Outlined.CameraAlt,
                    text = "Camera",
                    onClick = {
                        when (PackageManager.PERMISSION_GRANTED) {
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            ) -> {
                                launchCamera()
                            }
                            else -> {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }
                    }
                )
                ActionItem(
                    icon = Icons.Outlined.AddPhotoAlternate,
                    text = "Gallery",
                    onClick = {
                        galleryLauncher.launch("image/*")
                        openBottomSheet = false
                    }
                )
            }
        }
    }
}

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
        
        // Set language for TTS
        val locale = when (language.lowercase()) {
            "english" -> Locale.ENGLISH
            "chinese" -> Locale("zh", "CN") // Mandarin Chinese (Simplified)
            "arabic" -> Locale("ar")
            "farsi" -> Locale("fa") // Persian
            "kurdish" -> Locale("ku")
            "turkish" -> Locale("tr")
            "urdu" -> Locale("ur")
            // Additional languages for broader support
         
         
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
            
            // Audio recording status
            val isRecording by chatViewModel.isRecording
            val recordingDuration by chatViewModel.recordingDuration
            val transcriptionInProgress by chatViewModel.transcriptionInProgress
            val audioInitialized by chatViewModel.audioInitialized
            
            // Recording status indicator (only show in voice mode)
            if (!isTextInputMode && isRecording) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.FiberManualRecord,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Recording... ${recordingDuration / 1000}s",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            // Transcription progress indicator (only show in voice mode)
            if (!isTextInputMode && transcriptionInProgress) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Converting speech to text...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            // Translation progress indicator
            if (isTranslating) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
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
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
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
                            isTranslating = isTranslating, // Add translation state
                            transcriptionInProgress = transcriptionInProgress, // Add transcription state
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
                                imageVector = androidx.compose.material.icons.Icons.Filled.Keyboard,
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
    isTranslating: Boolean, // Add translation state
    transcriptionInProgress: Boolean, // Add transcription state
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
        // Instruction text - positioned above the button to avoid overlap
        if (!isRecording && !isTranslating && !transcriptionInProgress) {
            Text(
                text = "Press and hold to speak",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
        
        // Press and hold microphone button
        androidx.compose.material3.FloatingActionButton(
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
                !audioInitialized -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                isRecording -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.primary
            },
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(
                imageVector = if (isRecording) Icons.Filled.FiberManualRecord else Icons.Filled.Mic,
                contentDescription = when {
                    !audioInitialized -> "Initializing audio..."
                    isRecording -> "Recording... Release to stop"
                    else -> "Press and hold to speak"
                },
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



@Composable
fun SendMessageView(
    viewModel: ChatViewModel? = null,
    onPhotoClick: (() -> Unit)? = null,
    selectedImageUris: List<android.net.Uri> = emptyList(),
    onClearImage: ((android.net.Uri) -> Unit)? = null,
    onAudioPermissionNeeded: (() -> Unit)? = null,
    placeholder: String = "Ask Me Everything",
    showCamera: Boolean = true,
    showAudio: Boolean = true,
    onTextSubmit: ((String) -> Unit)? = null,
    onAudioTranscription: ((String) -> Unit)? = null
) {
    val localFocusManager = LocalFocusManager.current
    val context = LocalContext.current
    var text by rememberSaveable { mutableStateOf("") }
    
    // Optimize state reading with remember and derivedStateOf
    val audioStates = remember(viewModel) {
        AudioStates(
            isRecording = viewModel?.isRecording?.value ?: false,
            recordingDuration = viewModel?.recordingDuration?.value ?: 0L,
            transcriptionInProgress = viewModel?.transcriptionInProgress?.value ?: false,
            audioInitialized = viewModel?.audioInitialized?.value ?: true
        )
    }
    
    val isRecording by (viewModel?.isRecording ?: remember { mutableStateOf(false) })
    val recordingDuration by (viewModel?.recordingDuration ?: remember { mutableStateOf(0L) })
    val transcriptionInProgress by (viewModel?.transcriptionInProgress ?: remember { mutableStateOf(false) })
    val audioInitialized by (viewModel?.audioInitialized ?: remember { mutableStateOf(true) })

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
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
            // Image attachments
            if (selectedImageUris.isNotEmpty() && showCamera) {
                ImageAttachmentRow(
                    imageUris = selectedImageUris,
                    onClearImage = onClearImage
                )
            }
            
            // Status indicators
            StatusIndicators(
                isRecording = isRecording,
                recordingDuration = recordingDuration,
                transcriptionInProgress = transcriptionInProgress,
                audioInitialized = audioInitialized,
                showAudio = showAudio
            )
            
            // Text input
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = {
                    Text(
                        placeholder,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.onPrimary,
                    unfocusedContainerColor = MaterialTheme.colorScheme.onPrimary
                ),
                minLines = 1,
                maxLines = 3
            )

            // Action buttons
            ActionButtonsRow(
                showCamera = showCamera,
                showAudio = showAudio,
                text = text,
                selectedImageUris = selectedImageUris,
                isRecording = isRecording,
                transcriptionInProgress = transcriptionInProgress,
                audioInitialized = audioInitialized,
                onPhotoClick = onPhotoClick,
                onAudioClick = {
                    if (!audioInitialized) {
                        Log.d("SendMessageView", "⚠️ Audio components not initialized yet")
                        return@ActionButtonsRow
                    }
                    
                    when (PackageManager.PERMISSION_GRANTED) {
                        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) -> {
                            viewModel?.toggleAudioRecording { transcriptionResult ->
                                text = transcriptionResult
                                onAudioTranscription?.invoke(transcriptionResult)
                            }
                        }
                        else -> onAudioPermissionNeeded?.invoke()
                    }
                },
                onSendClick = {
                    if (text.isNotBlank() || selectedImageUris.isNotEmpty()) {
                        localFocusManager.clearFocus()
                        
                        if (onTextSubmit != null) {
                            onTextSubmit(text)
                        } else {
                            viewModel?.requestResponse(text, selectedImageUris)
                            selectedImageUris.forEach { onClearImage?.invoke(it) }
                        }
                        text = ""
                    }
                }
            )
        }
    }
}

// Performance optimization: Extract data class for audio states
private data class AudioStates(
    val isRecording: Boolean,
    val recordingDuration: Long,
    val transcriptionInProgress: Boolean,
    val audioInitialized: Boolean
)

@Composable
private fun ImageAttachmentRow(
    imageUris: List<android.net.Uri>,
    onClearImage: ((android.net.Uri) -> Unit)?
) {
    LazyRow(
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        items(imageUris) { uri ->
            Box {
                AsyncImage(
                    model = uri,
                    contentDescription = "Selected image",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
                IconButton(
                    onClick = { onClearImage?.invoke(uri) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 8.dp, y = (-8).dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Clear image",
                        tint = Color.DarkGray.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusIndicators(
    isRecording: Boolean,
    recordingDuration: Long,
    transcriptionInProgress: Boolean,
    audioInitialized: Boolean,
    showAudio: Boolean
) {
    // Recording status indicator
    if (isRecording && showAudio) {
        StatusRow(
            icon = Icons.Default.FiberManualRecord,
            text = "Recording... ${recordingDuration / 1000}s",
            color = MaterialTheme.colorScheme.error
        )
    }
    
    // Transcription progress indicator
    if (transcriptionInProgress && showAudio) {
        StatusRow(
            text = "Converting speech to text...",
            color = MaterialTheme.colorScheme.primary,
            showProgress = true
        )
    }
    
    // Audio initialization indicator
    if (!audioInitialized && showAudio) {
        StatusRow(
            text = "Initializing audio components...",
            color = MaterialTheme.colorScheme.secondary,
            showProgress = true
        )
    }
}

@Composable
private fun StatusRow(
    icon: ImageVector? = null,
    text: String,
    color: androidx.compose.ui.graphics.Color,
    showProgress: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showProgress) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 2.dp,
                color = color
            )
        } else if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(12.dp)
            )
        }
        
        if (showProgress || icon != null) {
            Spacer(modifier = Modifier.width(4.dp))
        }
        
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color
        )
    }
}

@Composable
private fun ActionButtonsRow(
    showCamera: Boolean,
    showAudio: Boolean,
    text: String,
    selectedImageUris: List<android.net.Uri>,
    isRecording: Boolean,
    transcriptionInProgress: Boolean,
    audioInitialized: Boolean,
    onPhotoClick: (() -> Unit)?,
    onAudioClick: () -> Unit,
    onSendClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showCamera) {
            IconButton(onClick = { onPhotoClick?.invoke() }) {
                Icon(
                    imageVector = Icons.Filled.CameraAlt,
                    contentDescription = "Photo",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Spacer(modifier = Modifier.width(48.dp))
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showAudio) {
                IconButton(
                    onClick = onAudioClick,
                    enabled = audioInitialized && !transcriptionInProgress
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Filled.FiberManualRecord else Icons.Filled.Mic,
                        contentDescription = when {
                            !audioInitialized -> "Initializing audio..."
                            isRecording -> "Stop recording"
                            else -> "Start voice input"
                        },
                        tint = when {
                            !audioInitialized -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            transcriptionInProgress -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            isRecording -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            IconButton(
                onClick = onSendClick,
                enabled = (text.isNotBlank() || selectedImageUris.isNotEmpty())
            ) {
                Icon(
                    imageVector = Icons.Filled.Send,
                    contentDescription = "Send message",
                    tint = if (text.isNotBlank() || selectedImageUris.isNotEmpty())
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun MessageView(messageData: MessageData) {
    SelectionContainer {
        val fromModel = messageData.owner == MessageOwner.Model

        Row(
            horizontalArrangement = if (fromModel) Arrangement.Start else Arrangement.End,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp)
        ) {
            if (fromModel) {
                if (messageData.isLoading) {
                    // Show loading indicator for model responses
                    ModelLoadingIndicator()
                } else {
                    MarkdownText(
                        markdown = messageData.message,
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 24.sp
                        )
                    )
                }
            } else {
                Column(
                    modifier = Modifier.widthIn(max = 280.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (messageData.imageUris.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(messageData.imageUris) { uri ->
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                ) {
                                    AsyncImage(
                                        model = uri,
                                        contentDescription = "Sent image",
                                        modifier = Modifier.sizeIn(maxWidth = 120.dp, maxHeight = 120.dp)
                                    )
                                }
                            }
                        }
                    }
                    if (messageData.message.isNotBlank()) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.inverseSurface,
                                contentColor = MaterialTheme.colorScheme.inverseOnSurface
                            ),
                            shape = RoundedCornerShape(
                                topStart = 20.dp,
                                topEnd = 4.dp,
                                bottomEnd = 20.dp,
                                bottomStart = 20.dp
                            ),
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = 1.dp,
                                pressedElevation = 2.dp
                            )
                        ) {
                            Text(
                                text = messageData.message,
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.padding(8.dp)
    ) {
            Surface(
                shape = CircleShape,
                tonalElevation = 2.dp,
                modifier = Modifier.size(56.dp)
            ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().clickable(onClick = onClick)) {
                Icon(imageVector = icon, contentDescription = text)
            }
        }

        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun ModelLoadingIndicator() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(
            topStart = 20.dp,
            topEnd = 20.dp,
            bottomEnd = 4.dp,
            bottomStart = 20.dp
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp
        ),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "AI is thinking...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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