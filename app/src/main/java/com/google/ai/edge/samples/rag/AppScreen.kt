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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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

@Composable
fun AppNavigation(viewModel: ChatViewModel) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "chatbot") {
        composable("chatbot") {
            ChatScreen(
                viewModel = viewModel,
                navController = navController
            )
        }
        composable("translation") {
            TranslationScreen(navController = navController)
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
                selectedImageUris = selectedImageUris + it
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

    Scaffold(
        topBar = {
            FieldCommAppBar(
                title = "Field-Comm",
                actionIcon = Icons.Filled.Translate,
                actionContentDescription = "Translation Mode",
                actionText = "Translation",
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
                onClearImage = { uri -> selectedImageUris = selectedImageUris - uri }
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
fun TranslationScreen(navController: NavController) {
    var expanded by remember { mutableStateOf(false) }
    val languages = listOf("English", "Spanish", "French", "German", "Chinese")
    var selectedLanguage by remember { mutableStateOf(languages[0]) }

    Scaffold(
        topBar = {
            FieldCommAppBar(
                title = "Field-Comm",
                actionIcon = Icons.Filled.Chat,
                actionContentDescription = "Chatbot Mode",
                actionText = "Chatbot",
                onActionClick = { navController.navigate("chatbot") }
            )
        },
        containerColor = MaterialTheme.colorScheme.onPrimary,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    modifier = Modifier.menuAnchor(),
                    value = selectedLanguage,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Target Language") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        focusedLabelColor = MaterialTheme.colorScheme.onBackground,
                        focusedTrailingIconColor = MaterialTheme.colorScheme.onBackground,
                        focusedContainerColor = MaterialTheme.colorScheme.onPrimary,
                        unfocusedContainerColor = MaterialTheme.colorScheme.onPrimary,
                    )
                )
                MaterialTheme(
                    colorScheme = MaterialTheme.colorScheme.copy(
                        primary = MaterialTheme.colorScheme.onBackground,
                        surface = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        languages.forEach { language ->
                            DropdownMenuItem(
                                text = { Text(language) },
                                onClick = {
                                    selectedLanguage = language
                                    expanded = false
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                            )
                        }
                    }
                }
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
                MarkdownText(
                    markdown = messageData.message,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 24.sp
                    )
                )
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
fun SendMessageView(
    viewModel: ChatViewModel,
    onPhotoClick: () -> Unit,
    selectedImageUris: List<android.net.Uri>,
    onClearImage: (android.net.Uri) -> Unit
) {
    val localFocusManager = LocalFocusManager.current
    var text by rememberSaveable { mutableStateOf("") }

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
            if (selectedImageUris.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(selectedImageUris) { uri ->
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
                                onClick = { onClearImage(uri) },
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
            // Text input row
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = {
                    Text(
                        "Ask Me Everything",
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

            // Action buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPhotoClick) {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = "Photo",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { /* TODO: Handle voice input */ }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = "Voice input",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = {
                            if (text.isNotBlank() || selectedImageUris.isNotEmpty()) {
                                localFocusManager.clearFocus()
                                viewModel.requestResponse(text, selectedImageUris)
                                text = ""
                                selectedImageUris.forEach(onClearImage)
                            }
                        },
                        enabled = text.isNotBlank() || selectedImageUris.isNotEmpty()
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