package com.google.ai.edge.samples.rag

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

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
            if (viewModel.messages.isEmpty()) {
                // Center the instruction card vertically in the available space
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    ChatInstructionCard()
                }
            } else {
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

@Composable
fun ChatInstructionCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.Chat,
                contentDescription = "Chat mode",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "AI Chat Assistant",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Ask questions and get help with:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Instructions with icons
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InstructionRow(
                    icon = Icons.Filled.Mic,
                    text = "Voice: Press and hold microphone to speak"
                )
                InstructionRow(
                    icon = Icons.Filled.CameraAlt,
                    text = "Image: Tap camera to analyze photos"
                )
                InstructionRow(
                    icon = Icons.Filled.Send,
                    text = "Text: Type your questions below"
                )
            }        
        }
    }
}

@Composable
private fun InstructionRow(
    icon: ImageVector,
    text: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
                    dev.jeziellago.compose.markdowntext.MarkdownText(
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