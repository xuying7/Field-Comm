package com.google.ai.edge.samples.rag

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.google.ai.edge.samples.rag.ui.theme.RagTheme

class MainActivity : ComponentActivity() {
  lateinit var chatViewModel: ChatViewModel

  @OptIn(ExperimentalMaterial3Api::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    chatViewModel = ChatViewModel(application)
    setContent {
      RagTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          AppNavigation(chatViewModel)
        }
      }
    }
    lifecycleScope.launch {
      chatViewModel.memorizeChunks("sample_context.txt")
    }
  }

  private companion object {
    const val TAG = "MainActivity"
    const val PERMISSIONS_REQUEST = 1
  }
}
