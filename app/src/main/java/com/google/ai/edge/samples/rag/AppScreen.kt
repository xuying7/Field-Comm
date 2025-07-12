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
    
    // Audio states for functionality and display
    val isRecording by (viewModel?.isRecording ?: remember { mutableStateOf(false) })
    val recordingDuration by (viewModel?.recordingDuration ?: remember { mutableStateOf(0L) })
    val transcriptionInProgress by (viewModel?.transcriptionInProgress ?: remember { mutableStateOf(false) })
    val audioInitialized by (viewModel?.audioInitialized ?: remember { mutableStateOf(true) })
    
    // LLM initialization states
    val isLlmInitializing by (viewModel?.isLlmInitializing ?: remember { mutableStateOf(false) })
    val isLlmInitialized by (viewModel?.isLlmInitialized ?: remember { mutableStateOf(true) })
    val llmInitializationError by (viewModel?.llmInitializationError ?: remember { mutableStateOf<String?>(null) })

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
            
            // Status indicators - LLM status, recording, and transcription
            StatusIndicators(
                isLlmInitializing = isLlmInitializing,
                isLlmInitialized = isLlmInitialized,
                llmInitializationError = llmInitializationError,
                isRecording = isRecording,
                recordingDuration = recordingDuration,
                transcriptionInProgress = transcriptionInProgress
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
                },
                isLlmInitialized = isLlmInitialized,
                llmInitializationError = llmInitializationError
            )
        }
    }
}



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
fun StatusIndicators(
    isLlmInitializing: Boolean = false,
    isLlmInitialized: Boolean = false,
    llmInitializationError: String? = null,
    isRecording: Boolean = false,
    recordingDuration: Long = 0L,
    transcriptionInProgress: Boolean = false,
    isTranslating: Boolean = false
) {
    // Recording indicator with duration
    if (isRecording) {
        val seconds = recordingDuration / 1000
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        val timeText = if (minutes > 0) {
            String.format("%d:%02d (%d sec)", minutes, remainingSeconds, seconds)
        } else {
            String.format("%d sec", remainingSeconds)
        }
        
        StatusRow(
            icon = Icons.Filled.FiberManualRecord,
            text = "Recording... $timeText",
            color = MaterialTheme.colorScheme.error,
            showProgress = false
        )
    }
    // Transcription indicator
    else if (transcriptionInProgress) {
        StatusRow(
            text = "Converting to text...",
            color = MaterialTheme.colorScheme.primary,
            showProgress = true
        )
    } else if (isTranslating) {
        StatusRow(
            text = "Translating...",
            color = MaterialTheme.colorScheme.primary,
            showProgress = true
        )
    }
    // LLM initialization indicator
    else if (isLlmInitializing && !isLlmInitialized) {
        StatusRow(
            text = "Loading AI model...",
            color = MaterialTheme.colorScheme.secondary,
            showProgress = true
        )
    }
    // LLM initialization error indicator
    else if (llmInitializationError != null && !isLlmInitializing) {
        StatusRow(
            text = "AI model failed to load",
            color = MaterialTheme.colorScheme.error,
            showProgress = false
        )
    }
}

@Composable
fun StatusRow(
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
    onSendClick: () -> Unit,
    // Add LLM initialization parameters
    isLlmInitialized: Boolean = true,
    llmInitializationError: String? = null
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
                    enabled = !transcriptionInProgress
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Filled.FiberManualRecord else Icons.Filled.Mic,
                        contentDescription = when {
                            !audioInitialized -> "Initializing audio..."
                            isRecording -> "Stop recording"
                            else -> "Start voice input"
                        },
                        tint = when {
                            transcriptionInProgress -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            isRecording -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            IconButton(
                onClick = onSendClick,
                enabled = (text.isNotBlank() || selectedImageUris.isNotEmpty()) && 
                         isLlmInitialized && llmInitializationError == null
            ) {
                Icon(
                    imageVector = Icons.Filled.Send,
                    contentDescription = when {
                        !isLlmInitialized -> "AI model not ready"
                        llmInitializationError != null -> "AI model failed to load"
                        else -> "Send message"
                    },
                    tint = if ((text.isNotBlank() || selectedImageUris.isNotEmpty()) && 
                              isLlmInitialized && llmInitializationError == null)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}





