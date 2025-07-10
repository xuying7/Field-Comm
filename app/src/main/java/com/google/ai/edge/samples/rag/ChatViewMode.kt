package com.google.ai.edge.samples.rag

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.toMutableStateList
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Instantiates the View Model for the chat view. */
class ChatViewModel constructor(private val application: Application) :
  AndroidViewModel(application) {
  private val ragPipeline = RagPipeline(application)
  internal val messages = emptyList<MessageData>().toMutableStateList()
  internal val statistics = mutableStateOf("")
  private val executorService = Executors.newSingleThreadExecutor()
  private val backgroundExecutor: Executor = Executors.newSingleThreadExecutor()
  
  // Thread-safe accumulator for streaming responses
  @Volatile
  private var currentStreamingText = ""

  /**
   * Public entry point from the UI. Accepts the user prompt and the list of selected image URIs.
   * If the list is non-empty, the first image is converted to a [android.graphics.Bitmap] and the
   * multimodal RAG path is used. Otherwise we fall back to text-only generation.
   */
  @SuppressWarnings("FutureReturnValueIgnored")
  fun requestResponse(prompt: String, imageUris: List<Uri> = emptyList()) {
    // Add the user's message to the UI immediately.
    appendMessage(MessageOwner.User, prompt, imageUris)

    // Add a placeholder message for the model while we wait for the response.
    appendMessage(MessageOwner.Model, "...")
    
    // Reset streaming accumulator for new message
    currentStreamingText = ""

    executorService.submit {
      viewModelScope.launch { requestResponseFromModel(prompt, imageUris) }
    }
  }

  private suspend fun requestResponseFromModel(prompt: String, imageUris: List<Uri>) {
    val fullResponse =
      withContext(backgroundExecutor.asCoroutineDispatcher()) {
        if (imageUris.isNotEmpty()) {
          // Decode the first image URI to a Bitmap.
          val bitmap =
            application.contentResolver.openInputStream(imageUris.first())?.use { stream ->
              BitmapFactory.decodeStream(stream)
            }
          if (bitmap != null) {
            // Use the multimodal path with proper text accumulation
            ragPipeline.generateResponseWithImage(prompt, bitmap) { response, done ->
              // FIXED: Accumulate streaming tokens instead of using response.text directly
              synchronized(this@ChatViewModel) {
                currentStreamingText += response.text
              }
              
              // Update UI on main thread with accumulated text for proper streaming display
              viewModelScope.launch {
                synchronized(this@ChatViewModel) {
                  updateLastMessage(MessageOwner.Model, currentStreamingText)
                }
              }
            }
          } else {
            "Failed to load image."
          }
        } else {
          // Fallback: text-only path with same accumulation pattern
          ragPipeline.generateResponse(prompt) { response, done ->
            // FIXED: Also accumulate text for text-only path for consistency
            synchronized(this@ChatViewModel) {
              currentStreamingText += response.text
            }
            
            // Update UI on main thread for proper streaming display
            viewModelScope.launch {
              synchronized(this@ChatViewModel) {
                updateLastMessage(MessageOwner.Model, currentStreamingText)
              }
            }
          }
        }
      }
    // Don't do final update here - the streaming callback already handles the final text
    // This prevents double-update that causes text overlap
  }

  suspend fun memorizeChunks(filename: String) {
    withContext(backgroundExecutor.asCoroutineDispatcher()) {
      ragPipeline.memorizeChunks(application.applicationContext, filename)
    }
  }

  private fun appendMessage(
    role: MessageOwner,
    message: String,
    imageUris: List<Uri> = emptyList()
  ) {
    messages.add(MessageData(role, message, imageUris))
  }

  private fun updateLastMessage(role: MessageOwner, message: String) {
    if (messages.isNotEmpty() && messages.last().owner == role) {
      val last = messages.last()
      messages[messages.lastIndex] = last.copy(message = message)
    } else {
      appendMessage(role, message)
    }
  }
}

enum class MessageOwner {
  User,
  Model,
}

data class MessageData(
  val owner: MessageOwner,
  val message: String,
  val imageUris: List<Uri> = emptyList()
)