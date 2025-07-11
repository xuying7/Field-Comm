package com.google.ai.edge.samples.rag

import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shared AudioManager for both chat and translation screens.
 * Handles audio recording, transcription, and memory management.
 */
class AudioManager private constructor() {
    private var whisperModel: WhisperModel? = null
    private var audioRecorder: AudioRecorder? = null
    private val backgroundExecutor: Executor = Executors.newSingleThreadExecutor()
    
    // Audio recording state
    private val _isRecording = mutableStateOf(false)
    val isRecording: State<Boolean> = _isRecording
    
    private val _recordingDuration = mutableStateOf(0L)
    val recordingDuration: State<Long> = _recordingDuration
    
    private val _transcriptionInProgress = mutableStateOf(false)
    val transcriptionInProgress: State<Boolean> = _transcriptionInProgress
    
    private val _audioInitialized = mutableStateOf(false)
    val audioInitialized: State<Boolean> = _audioInitialized
    
    companion object {
        private const val TAG = "AudioManager"
        
        @Volatile
        private var INSTANCE: AudioManager? = null
        
        fun getInstance(): AudioManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AudioManager().also { INSTANCE = it }
            }
        }
    }
    
    /**
     * Initialize audio components with application context
     */
    fun initialize(application: android.app.Application, scope: CoroutineScope) {
        if (_audioInitialized.value) {
            Log.d(TAG, "🎙️ Audio components already initialized")
            return
        }
        
        scope.launch {
            withContext(Dispatchers.IO) {
                Log.d(TAG, "🎙️ Initializing shared audio components...")
                
                // Initialize WhisperModel
                whisperModel = WhisperModel(application)
                whisperModel?.setListener(object : WhisperModel.WhisperListener {
                    override fun onUpdateReceived(message: String) {
                        Log.d(TAG, "🔄 Whisper update: $message")
                    }
                    
                    override fun onResultReceived(result: String) {
                        Log.d(TAG, "✅ Whisper result: $result")
                    }
                })
                
                // Initialize AudioRecorder
                audioRecorder = AudioRecorder()
                
                val audioInitSuccess = audioRecorder?.initialize() ?: false
                val whisperInitSuccess = whisperModel?.initialize() ?: false
                
                Log.d(TAG, "🎙️ AudioRecorder initialized: $audioInitSuccess")
                Log.d(TAG, "🗣️ WhisperModel initialized: $whisperInitSuccess")
                
                scope.launch {
                    _audioInitialized.value = audioInitSuccess && whisperInitSuccess
                }
                
                if (audioInitSuccess && whisperInitSuccess) {
                    Log.d(TAG, "✅ Shared audio components initialized successfully")
                } else {
                    Log.e(TAG, "❌ Failed to initialize shared audio components")
                }
            }
        }
    }
    
    /**
     * Start audio recording
     */
    fun startRecording(scope: CoroutineScope, onTranscriptionResult: (String) -> Unit) {
        if (_isRecording.value) {
            Log.w(TAG, "⚠️ Recording already in progress")
            return
        }
        
        if (!_audioInitialized.value) {
            Log.e(TAG, "❌ Audio components not initialized yet")
            return
        }
        
        scope.launch {
            withContext(Dispatchers.IO) {
                Log.d(TAG, "🎙️ Starting shared audio recording...")
                
                val audioRecorderInstance = audioRecorder
                if (audioRecorderInstance == null) {
                    Log.e(TAG, "❌ AudioRecorder instance is null")
                    return@withContext
                }
                
                try {
                    audioRecorderInstance.startRecording(object : AudioRecorder.AudioRecordingCallback {
                        override fun onRecordingStarted() {
                            Log.d(TAG, "✅ Recording started")
                            scope.launch {
                                _isRecording.value = true
                                _recordingDuration.value = 0L
                            }
                        }
                        
                        override fun onRecordingProgress(durationMs: Long) {
                            scope.launch {
                                _recordingDuration.value = durationMs
                            }
                        }
                        
                        override fun onRecordingStopped(audioSamples: FloatArray) {
                            Log.d(TAG, "🛑 Recording stopped, processing audio...")
                            
                            scope.launch {
                                _isRecording.value = false
                                _transcriptionInProgress.value = true
                                
                                withContext(Dispatchers.IO) {
                                    try {
                                        val whisperModelInstance = whisperModel
                                        if (whisperModelInstance == null) {
                                            Log.e(TAG, "❌ WhisperModel instance is null")
                                            _transcriptionInProgress.value = false
                                            return@withContext
                                        }
                                        
                                        val transcription = whisperModelInstance.transcribeAudio(audioSamples)
                                        
                                        scope.launch {
                                            _transcriptionInProgress.value = false
                                            if (transcription != null && transcription.isNotBlank()) {
                                                onTranscriptionResult(transcription)
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "❌ Error during transcription", e)
                                        scope.launch {
                                            _transcriptionInProgress.value = false
                                        }
                                    }
                                }
                            }
                        }
                        
                        override fun onRecordingError(error: String) {
                            Log.e(TAG, "❌ Recording error: $error")
                            scope.launch {
                                _isRecording.value = false
                                _transcriptionInProgress.value = false
                            }
                        }
                    })
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error starting recording", e)
                    _isRecording.value = false
                }
            }
        }
    }
    
    /**
     * Stop audio recording
     */
    fun stopRecording(scope: CoroutineScope) {
        if (!_isRecording.value) {
            Log.w(TAG, "⚠️ No recording in progress")
            return
        }
        
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val audioRecorderInstance = audioRecorder
                    if (audioRecorderInstance == null) {
                        Log.e(TAG, "❌ AudioRecorder instance is null")
                        return@withContext
                    }
                    
                    audioRecorderInstance.stopRecording(object : AudioRecorder.AudioRecordingCallback {
                        override fun onRecordingStarted() {}
                        override fun onRecordingProgress(durationMs: Long) {}
                        override fun onRecordingStopped(audioSamples: FloatArray) {}
                        override fun onRecordingError(error: String) {
                            Log.e(TAG, "❌ Error stopping recording: $error")
                        }
                    })
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error stopping recording", e)
                    scope.launch {
                        _isRecording.value = false
                    }
                }
            }
        }
    }
    
    /**
     * Toggle recording state
     */
    fun toggleRecording(scope: CoroutineScope, onTranscriptionResult: (String) -> Unit) {
        if (_isRecording.value) {
            stopRecording(scope)
        } else {
            startRecording(scope, onTranscriptionResult)
        }
    }
    
    /**
     * Clean up resources
     */
    fun release() {
        Log.d(TAG, "🧹 Releasing shared audio components...")
        
        // Stop recording if in progress
        if (_isRecording.value) {
            _isRecording.value = false
        }
        
        // Release audio components
        audioRecorder?.release()
        audioRecorder = null
        
        whisperModel?.close()
        whisperModel = null
        
        // Clean up executor
        (backgroundExecutor as? java.util.concurrent.ExecutorService)?.shutdown()
        
        // Reset state
        _audioInitialized.value = false
        _isRecording.value = false
        _recordingDuration.value = 0L
        _transcriptionInProgress.value = false
        
        Log.d(TAG, "✅ Shared audio components released")
    }
}