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
import android.util.Log

/** Instantiates the View Model for the chat view. */
class ChatViewModel constructor(private val application: Application) :
  AndroidViewModel(application) {
  private val ragPipeline = RagPipeline(application)
  private val whisperModel = WhisperModel(application)
  private val audioRecorder = AudioRecorder()
  
  internal val messages = emptyList<MessageData>().toMutableStateList()
  internal val statistics = mutableStateOf("")
  private val executorService = Executors.newSingleThreadExecutor()
  private val backgroundExecutor: Executor = Executors.newSingleThreadExecutor()
  
  // Text accumulator specifically for multimodal responses (image path)
  @Volatile
  private var multimodalStreamingText = ""
  
  // Audio recording state
  private val _isRecording = mutableStateOf(false)
  val isRecording: androidx.compose.runtime.State<Boolean> = _isRecording
  
  private val _recordingDuration = mutableStateOf(0L)
  val recordingDuration: androidx.compose.runtime.State<Long> = _recordingDuration
  
  private val _transcriptionInProgress = mutableStateOf(false)
  val transcriptionInProgress: androidx.compose.runtime.State<Boolean> = _transcriptionInProgress
  
  // Audio initialization state
  private val _audioInitialized = mutableStateOf(false)
  val audioInitialized: androidx.compose.runtime.State<Boolean> = _audioInitialized
  
  // LLM initialization state - exposed from RagPipeline
  val isLlmInitialized: androidx.compose.runtime.State<Boolean> = ragPipeline.isLlmInitialized
  val isLlmInitializing: androidx.compose.runtime.State<Boolean> = ragPipeline.isLlmInitializing
  val llmInitializationError: androidx.compose.runtime.State<String?> = ragPipeline.llmInitializationError

  init {
    // Set up LLM initialization callback
    ragPipeline.setLlmInitializationCallback(object : RagPipeline.LlmInitializationCallback {
      override fun onLlmInitializationStarted() {
        Log.d("ChatViewModel", "🚀 LLM initialization started")
        viewModelScope.launch {
          statistics.value = "Initializing AI model..."
        }
      }
      
      
      
      override fun onLlmInitializationFailure(error: String) {
        Log.e("ChatViewModel", "❌ LLM initialization failed: $error")
        viewModelScope.launch {
          statistics.value = "AI model failed to load: $error"
        }
      }
    })
    
    // Initialize audio components
    viewModelScope.launch {
      withContext(backgroundExecutor.asCoroutineDispatcher()) {
        Log.d("ChatViewModel", "🎙️ Initializing audio components...")
        
        // Set up WhisperModel listener following the proven pattern
        whisperModel.setListener(object : WhisperModel.WhisperListener {
          override fun onUpdateReceived(message: String) {
            Log.d("ChatViewModel", "🔄 Whisper update: $message")
          }
          
          override fun onResultReceived(result: String) {
            Log.d("ChatViewModel", "✅ Whisper result: $result")
          }
        })
        
        // Initialize AudioRecorder
        val audioInitSuccess = audioRecorder.initialize()
        Log.d("ChatViewModel", "🎙️ AudioRecorder initialized: $audioInitSuccess")
        
        // Initialize WhisperModel
        val whisperInitSuccess = whisperModel.initialize()
        Log.d("ChatViewModel", "🗣️ WhisperModel initialized: $whisperInitSuccess")
        
        // Update initialization state
        viewModelScope.launch {
          _audioInitialized.value = audioInitSuccess && whisperInitSuccess
        }
        
        if (audioInitSuccess && whisperInitSuccess) {
          Log.d("ChatViewModel", "✅ All audio components initialized successfully")
        } else {
          Log.e("ChatViewModel", "❌ Failed to initialize audio components")
          Log.e("ChatViewModel", "  - AudioRecorder: $audioInitSuccess")
          Log.e("ChatViewModel", "  - WhisperModel: $whisperInitSuccess")
        }
      }
    }
  }

  /**
   * Public entry point from the UI. Accepts the user prompt and the list of selected image URIs.
   * If the list is non-empty, the first image is converted to a [android.graphics.Bitmap] and the
   * multimodal RAG path is used. Otherwise we fall back to text-only generation.
   */
  @SuppressWarnings("FutureReturnValueIgnored")
  fun requestResponse(prompt: String, imageUris: List<Uri> = emptyList()) {
    // Add the user's message to the UI immediately.
    appendMessage(MessageOwner.User, prompt, imageUris)

    // Add a loading placeholder message for the model while we wait for the response.
    appendLoadingMessage(MessageOwner.Model)
    
    executorService.submit {
      viewModelScope.launch { requestResponseFromModel(prompt, imageUris) }
    }
  }

  private suspend fun requestResponseFromModel(prompt: String, imageUris: List<Uri>) {
    val fullResponse =
      withContext(backgroundExecutor.asCoroutineDispatcher()) {
        try {
          if (imageUris.isNotEmpty()) {
            // Reset accumulator for new multimodal response
            multimodalStreamingText = ""
            
            // Decode the first image URI to a Bitmap.
            val bitmap =
              application.contentResolver.openInputStream(imageUris.first())?.use { stream ->
                BitmapFactory.decodeStream(stream)
              }
            if (bitmap != null) {
              // Use the multimodal path with text accumulation (multimodal callbacks provide incremental tokens)
              ragPipeline.generateResponseWithImage(prompt, bitmap) { response, done ->
                // MULTIMODAL FIX: Accumulate incremental tokens since multimodal callback provides partial tokens
                synchronized(this@ChatViewModel) {
                  multimodalStreamingText += response.text
                }
                
                viewModelScope.launch {
                  // Update UI with accumulated text for proper multimodal streaming display
                  synchronized(this@ChatViewModel) {
                    val cleanedText = multimodalStreamingText.trim()
                    if (cleanedText.isNotEmpty()) {
                      updateLastMessage(MessageOwner.Model, cleanedText)
                    }
                  }
                }
              }
            } else {
              "Failed to load image."
            }
          } else {
            // Fallback: text-only path (response.text contains full accumulated text)
            ragPipeline.generateResponse(prompt) { response, done ->
              // TEXT-ONLY: Use response.text directly since it contains full accumulated text
              viewModelScope.launch {
                // Validate response before updating UI
                val cleanedText = response.text.trim()
                if (cleanedText.isNotEmpty()) {
                  updateLastMessage(MessageOwner.Model, cleanedText)
                }
              }
            }
          }
        } catch (e: Exception) {
          Log.e("ChatViewModel", "❌ Error generating response", e)
          viewModelScope.launch {
            updateLastMessage(MessageOwner.Model, "Sorry, I encountered an error while generating a response.")
          }
          "Error occurred during response generation."
        }
      }
    // No need for final update - the streaming callback handles the complete text
  }

  /**
   * Start audio recording for speech-to-text conversion
   */
  fun startAudioRecording(onTranscriptionResult: (String) -> Unit) {
    if (_isRecording.value) {
      Log.w("ChatViewModel", "⚠️ Recording already in progress")
      return
    }
    
    if (!_audioInitialized.value) {
      Log.e("ChatViewModel", "❌ Audio components not initialized yet")
      return
    }
    
    viewModelScope.launch {
      withContext(backgroundExecutor.asCoroutineDispatcher()) {
        Log.d("ChatViewModel", "🎙️ Starting audio recording...")
        
        try {
          audioRecorder.startRecording(object : AudioRecorder.AudioRecordingCallback {
            override fun onRecordingStarted() {
              Log.d("ChatViewModel", "✅ Recording started")
              viewModelScope.launch {
                _isRecording.value = true
                _recordingDuration.value = 0L
              }
            }
            
            override fun onRecordingProgress(durationMs: Long) {
              viewModelScope.launch {
                _recordingDuration.value = durationMs
              }
            }
            
            override fun onRecordingStopped(audioSamples: FloatArray) {
              Log.d("ChatViewModel", "🛑 Recording stopped, processing audio...")
              Log.d("ChatViewModel", "📊 Audio samples received: ${audioSamples.size}")
              Log.d("ChatViewModel", "📊 Audio duration: ${audioSamples.size / 16000.0f} seconds")
              Log.d("ChatViewModel", "📊 Audio sample range: ${audioSamples.minOrNull()} to ${audioSamples.maxOrNull()}")
              
              viewModelScope.launch {
                _isRecording.value = false
                _transcriptionInProgress.value = true
                
                Log.d("ChatViewModel", "🔄 Starting transcription process...")
                
                // Process audio with Whisper model
                withContext(backgroundExecutor.asCoroutineDispatcher()) {
                  try {
                    Log.d("ChatViewModel", "🎙️ Calling whisperModel.transcribeAudio()...")
                    val transcription = whisperModel.transcribeAudio(audioSamples)
                    Log.d("ChatViewModel", "🎙️ Transcription completed: ${transcription?.length ?: 0} characters")
                    
                    viewModelScope.launch {
                      _transcriptionInProgress.value = false
                      if (transcription != null && transcription.isNotBlank()) {
                        Log.d("ChatViewModel", "✅ Transcription successful: $transcription")
                        onTranscriptionResult(transcription)
                      } else {
                        Log.w("ChatViewModel", "⚠️ Transcription was empty or null")
                        Log.w("ChatViewModel", "⚠️ Transcription value: '$transcription'")
                      }
                    }
                  } catch (e: Exception) {
                    Log.e("ChatViewModel", "❌ Error during transcription", e)
                    viewModelScope.launch {
                      _transcriptionInProgress.value = false
                    }
                  }
                }
              }
            }
            
            override fun onRecordingError(error: String) {
              Log.e("ChatViewModel", "❌ Recording error: $error")
              viewModelScope.launch {
                _isRecording.value = false
                _transcriptionInProgress.value = false
              }
            }
          })
        } catch (e: Exception) {
          Log.e("ChatViewModel", "❌ Error starting recording", e)
          _isRecording.value = false
        }
      }
    }
  }
  
  /**
   * Stop audio recording
   */
  fun stopAudioRecording() {
    if (!_isRecording.value) {
      Log.w("ChatViewModel", "⚠️ No recording in progress")
      return
    }
    
    Log.d("ChatViewModel", "🛑 Stopping audio recording...")
    
    // Tell the AudioRecorder to stop - this will cause the recording loop to exit
    // and trigger onRecordingStopped with the audio data
    viewModelScope.launch {
      withContext(backgroundExecutor.asCoroutineDispatcher()) {
        try {
          Log.d("ChatViewModel", "🛑 Telling AudioRecorder to stop...")
          // Set the AudioRecorder's isRecording flag to false
          // This will cause the recording loop to exit and call onRecordingStopped
          audioRecorder.stopRecording(object : AudioRecorder.AudioRecordingCallback {
            override fun onRecordingStarted() {}
            override fun onRecordingProgress(durationMs: Long) {}
            override fun onRecordingStopped(audioSamples: FloatArray) {
              Log.d("ChatViewModel", "🔄 Stop callback - audio samples should be processed by start callback")
            }
            override fun onRecordingError(error: String) {
              Log.e("ChatViewModel", "❌ Error stopping recording: $error")
              viewModelScope.launch {
                _isRecording.value = false
                _transcriptionInProgress.value = false
              }
            }
          })
        } catch (e: Exception) {
          Log.e("ChatViewModel", "❌ Error stopping recording", e)
          _isRecording.value = false
        }
      }
    }
  }
  
  /**
   * Toggle audio recording state
   */
  fun toggleAudioRecording(onTranscriptionResult: (String) -> Unit) {
    Log.d("ChatViewModel", "🎙️ Toggle audio recording called")
    Log.d("ChatViewModel", "📊 Current recording state: ${_isRecording.value}")
    
    if (_isRecording.value) {
      Log.d("ChatViewModel", "🛑 Currently recording, will stop recording...")
      stopAudioRecording()
    } else {
      Log.d("ChatViewModel", "▶️ Not recording, will start recording...")
      startAudioRecording(onTranscriptionResult)
    }
  }

  suspend fun memorizeChunks(filename: String) {
    withContext(backgroundExecutor.asCoroutineDispatcher()) {
      ragPipeline.memorizeChunks(application.applicationContext, filename)
    }
  }

  /**
   * Public interface for translation using the existing model instance
   * This avoids loading the model twice by reusing the same RagPipeline
   */
  suspend fun translateText(
    text: String,
    targetLanguage: String,
    callback: com.google.ai.edge.localagents.rag.models.AsyncProgressListener<com.google.ai.edge.localagents.rag.models.LanguageModelResponse>?
  ): String {
    return withContext(backgroundExecutor.asCoroutineDispatcher()) {
      ragPipeline.translateDirectly(text, targetLanguage, callback)
    }
  }

  private fun appendMessage(
    role: MessageOwner,
    message: String,
    imageUris: List<Uri> = emptyList()
  ) {
    messages.add(MessageData(role, message, imageUris))
  }

  private fun appendLoadingMessage(role: MessageOwner) {
    messages.add(MessageData(role, "", emptyList(), isLoading = true))
  }

  private fun updateLastMessage(role: MessageOwner, message: String) {
    if (messages.isNotEmpty() && messages.last().owner == role) {
      val last = messages.last()
      messages[messages.lastIndex] = last.copy(message = message, isLoading = false)
    } else {
      appendMessage(role, message)
    }
  }
  
  override fun onCleared() {
    super.onCleared()
    Log.d("ChatViewModel", "🧹 Cleaning up ChatViewModel...")
    
    // Clean up audio resources
    audioRecorder.release()
    whisperModel.close()
    
    // Clean up executors
    executorService.shutdown()
    backgroundExecutor.let { 
      if (it is java.util.concurrent.ExecutorService) {
        it.shutdown()
      }
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
  val imageUris: List<Uri> = emptyList(),
  val isLoading: Boolean = false
)