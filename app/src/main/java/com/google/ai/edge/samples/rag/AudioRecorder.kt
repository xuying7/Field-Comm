package com.google.ai.edge.samples.rag

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AudioRecorder handles audio recording for speech-to-text conversion.
 * 
 * Records audio at 16kHz sample rate, mono channel, 16-bit PCM format
 * and converts to Float32 format expected by Whisper model.
 */
class AudioRecorder {
    private var audioRecord: AudioRecord? = null
    private val isRecording = AtomicBoolean(false)
    private val audioData = mutableListOf<Float>()
    
    companion object {
        private const val TAG = "AudioRecorder"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_MULTIPLIER = 2
    }
    
    /**
     * Interface for audio recording events
     */
    interface AudioRecordingCallback {
        fun onRecordingStarted()
        fun onRecordingProgress(durationMs: Long)
        fun onRecordingStopped(audioSamples: FloatArray)
        fun onRecordingError(error: String)
    }
    
    /**
     * Initialize the audio recorder
     */
    fun initialize(): Boolean {
        return try {
            Log.d(TAG, "Initializing AudioRecorder...")
            
            // Calculate buffer size
            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )
            
            if (minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
                Log.e(TAG, "❌ AudioRecord.getMinBufferSize() returned ERROR_BAD_VALUE")
                Log.e(TAG, "  Sample rate: $SAMPLE_RATE")
                Log.e(TAG, "  Channel config: $CHANNEL_CONFIG")
                Log.e(TAG, "  Audio format: $AUDIO_FORMAT")
                return false
            }
            
            if (minBufferSize == AudioRecord.ERROR) {
                Log.e(TAG, "❌ AudioRecord.getMinBufferSize() returned ERROR")
                return false
            }
            
            val bufferSize = minBufferSize * BUFFER_SIZE_MULTIPLIER
            Log.d(TAG, "📊 Buffer size: $bufferSize (min: $minBufferSize)")
            
            // Create AudioRecord instance
            Log.d(TAG, "🔧 Creating AudioRecord instance...")
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
            
            // Check if AudioRecord was created successfully
            val state = audioRecord?.state
            Log.d(TAG, "📊 AudioRecord state: $state (expected: ${AudioRecord.STATE_INITIALIZED})")
            
            if (state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "❌ AudioRecord initialization failed")
                Log.e(TAG, "  State: $state")
                Log.e(TAG, "  Expected: ${AudioRecord.STATE_INITIALIZED}")
                
                audioRecord?.release()
                audioRecord = null
                return false
            }
            
            Log.d(TAG, "✅ AudioRecorder initialized successfully")
            true
            
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Security exception during AudioRecorder initialization", e)
            Log.e(TAG, "  This might be due to missing RECORD_AUDIO permission")
            false
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error initializing AudioRecorder", e)
            false
        }
    }
    
    /**
     * Start recording audio
     */
    suspend fun startRecording(callback: AudioRecordingCallback) = withContext(Dispatchers.IO) {
        if (audioRecord == null) {
            callback.onRecordingError("AudioRecorder not initialized")
            return@withContext
        }
        
        if (isRecording.get()) {
            callback.onRecordingError("Recording already in progress")
            return@withContext
        }
        
        try {
            Log.d(TAG, "🎙️ Starting audio recording...")
            
            // Clear previous audio data
            audioData.clear()
            
            // Start recording
            audioRecord?.startRecording()
            
            if (audioRecord?.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                callback.onRecordingError("Failed to start recording")
                return@withContext
            }
            
            isRecording.set(true)
            callback.onRecordingStarted()
            
            // Recording loop
            val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            val audioBuffer = ShortArray(bufferSize)
            val startTime = System.currentTimeMillis()
            
            Log.d(TAG, "🔄 Recording loop started...")
            
            while (isRecording.get()) {
                val samplesRead = audioRecord?.read(audioBuffer, 0, bufferSize) ?: 0
                
                if (samplesRead > 0) {
                    // === DETAILED AUDIO PROCESSING LOGGING ===
                    if (audioData.size < 1000) { // Only log first few chunks to avoid spam
                        Log.d(TAG, "📖 Read $samplesRead samples from AudioRecord")
                        
                        // Show first few raw PCM samples
                        val rawSamplePreview = audioBuffer.take(10).joinToString(" ", "Raw PCM: ")
                        Log.d(TAG, "  $rawSamplePreview")
                    }
                    
                    // Convert 16-bit PCM to float and add to audio data
                    val beforeSize = audioData.size
                    for (i in 0 until samplesRead) {
                        // Convert from 16-bit PCM to float [-1, 1]
                        val rawPcm = audioBuffer[i]
                        val floatSample = rawPcm.toFloat() / Short.MAX_VALUE.toFloat()
                        audioData.add(floatSample)
                        
                        // Log conversion details for first few samples
                        if (audioData.size <= 10) {
                            Log.d(TAG, "  PCM[$i]: $rawPcm -> Float: $floatSample")
                        }
                    }
                    
                    // Track conversion statistics every 1000 samples
                    if (audioData.size / 1000 > beforeSize / 1000) {
                        val recentSamples = audioData.takeLast(samplesRead)
                        val recentMin = recentSamples.minOrNull() ?: 0f
                        val recentMax = recentSamples.maxOrNull() ?: 0f
                        val recentAvg = recentSamples.average().toFloat()
                        val recentSilent = recentSamples.count { kotlin.math.abs(it) < 0.001f }
                        
                        Log.d(TAG, "📊 Chunk stats (samples ${beforeSize}-${audioData.size}):")
                        Log.d(TAG, "  Range: $recentMin to $recentMax, Avg: $recentAvg")
                        Log.d(TAG, "  Silent samples: $recentSilent/${recentSamples.size}")
                        
                        // Check for audio quality issues
                        if (recentMax - recentMin < 0.01f) {
                            Log.w(TAG, "⚠️ WARNING: Very low audio dynamic range in this chunk")
                        }
                        if (recentSilent > recentSamples.size * 0.8) {
                            Log.w(TAG, "⚠️ WARNING: Mostly silent chunk detected")
                        }
                    }
                    
                    // Update progress
                    val currentTime = System.currentTimeMillis()
                    val durationMs = currentTime - startTime
                    callback.onRecordingProgress(durationMs)
                    
                } else if (samplesRead < 0) {
                    Log.e(TAG, "❌ Error reading audio data: $samplesRead")
                    when (samplesRead) {
                        AudioRecord.ERROR_INVALID_OPERATION -> Log.e(TAG, "  ERROR_INVALID_OPERATION")
                        AudioRecord.ERROR_BAD_VALUE -> Log.e(TAG, "  ERROR_BAD_VALUE") 
                        AudioRecord.ERROR_DEAD_OBJECT -> Log.e(TAG, "  ERROR_DEAD_OBJECT")
                        AudioRecord.ERROR -> Log.e(TAG, "  ERROR")
                        else -> Log.e(TAG, "  Unknown error code: $samplesRead")
                    }
                    break
                } else {
                    // samplesRead == 0, no new data but continue
                    if (audioData.size % 10000 == 0) { // Log occasionally during silence
                        Log.d(TAG, "📊 No new samples, current buffer: ${audioData.size} samples")
                    }
                }
            }
            
            Log.d(TAG, "🛑 Recording loop ended")
            
            // === FINAL AUDIO ANALYSIS ===
            Log.d(TAG, "=== FINAL AUDIO ANALYSIS ===")
            val audioSamples = audioData.toFloatArray()
            
            Log.d(TAG, "📊 Final audio samples: ${audioSamples.size}")
            Log.d(TAG, "⏱️ Final duration: ${audioSamples.size / SAMPLE_RATE.toFloat()} seconds")
            
            if (audioSamples.isNotEmpty()) {
                val finalMin = audioSamples.minOrNull() ?: 0f
                val finalMax = audioSamples.maxOrNull() ?: 0f
                val finalAvg = audioSamples.average().toFloat()
                val finalSilent = audioSamples.count { kotlin.math.abs(it) < 0.001f }
                val finalSilenceRatio = finalSilent.toFloat() / audioSamples.size
                
                Log.d(TAG, "📈 COMPLETE AUDIO STATISTICS:")
                Log.d(TAG, "  - Sample count: ${audioSamples.size}")
                Log.d(TAG, "  - Duration: ${audioSamples.size / SAMPLE_RATE.toFloat()}s")
                Log.d(TAG, "  - Range: $finalMin to $finalMax")
                Log.d(TAG, "  - Average: $finalAvg")
                Log.d(TAG, "  - Silent samples: $finalSilent/${audioSamples.size} ($finalSilenceRatio)")
                
                // Show first and last 10 samples for debugging
                val firstSamples = audioSamples.take(10).joinToString(" ", "First 10: ")
                val lastSamples = audioSamples.takeLast(10).joinToString(" ", "Last 10: ")
                Log.d(TAG, "  - $firstSamples")
                Log.d(TAG, "  - $lastSamples")
                
                // Audio quality assessment
                val dynamicRange = finalMax - finalMin
                Log.d(TAG, "🔍 AUDIO QUALITY ASSESSMENT:")
                Log.d(TAG, "  - Dynamic range: $dynamicRange")
                
                when {
                    finalSilenceRatio > 0.95f -> {
                        Log.e(TAG, "❌ CRITICAL: Audio is ${finalSilenceRatio*100}% silent!")
                        Log.e(TAG, "  This indicates a recording problem")
                    }
                    finalSilenceRatio > 0.8f -> {
                        Log.w(TAG, "⚠️ WARNING: Audio is ${finalSilenceRatio*100}% silent")
                        Log.w(TAG, "  User might not have spoken clearly")
                    }
                    dynamicRange < 0.01f -> {
                        Log.w(TAG, "⚠️ WARNING: Very low dynamic range ($dynamicRange)")
                        Log.w(TAG, "  Audio might be too quiet or compressed")
                    }
                    finalMax > 0.95f -> {
                        Log.w(TAG, "⚠️ WARNING: Audio might be clipping (max: $finalMax)")
                    }
                    finalMax < 0.1f -> {
                        Log.w(TAG, "⚠️ WARNING: Audio signal very weak (max: $finalMax)")
                    }
                    else -> {
                        Log.d(TAG, "✅ Audio quality looks reasonable")
                    }
                }
                
                // Check for expected speech patterns
                // Look for typical speech characteristics
                val chunks = audioSamples.toList().chunked(1600) // 0.1 second chunks
                var activeSpeechChunks = 0
                for (chunk in chunks) {
                    val chunkMax = chunk.maxOrNull() ?: 0f
                    val chunkMin = chunk.minOrNull() ?: 0f
                    val chunkRange = chunkMax - chunkMin
                    
                    // Consider it speech if it has reasonable dynamic range
                    if (chunkRange > 0.05f) {
                        activeSpeechChunks++
                    }
                }
                
                val speechRatio = activeSpeechChunks.toFloat() / chunks.size
                Log.d(TAG, "🗣️ SPEECH DETECTION:")
                Log.d(TAG, "  - Active speech chunks: $activeSpeechChunks/${chunks.size}")
                Log.d(TAG, "  - Speech ratio: $speechRatio")
                
                if (speechRatio < 0.1f) {
                    Log.w(TAG, "⚠️ WARNING: Very little speech detected in audio")
                } else if (speechRatio > 0.3f) {
                    Log.d(TAG, "✅ Good amount of speech detected")
                }
            } else {
                Log.e(TAG, "❌ CRITICAL: No audio samples recorded!")
            }
            
            Log.d(TAG, "🔄 Calling onRecordingStopped callback from recording loop...")
            
            callback.onRecordingStopped(audioSamples)
            Log.d(TAG, "✅ onRecordingStopped callback completed from recording loop")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error during recording", e)
            callback.onRecordingError("Recording error: ${e.message}")
        } finally {
            // Clean up
            try {
                audioRecord?.stop()
                Log.d(TAG, "🛑 Audio recording stopped")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error stopping recording", e)
            }
            
            isRecording.set(false)
        }
    }
    
    /**
     * Stop recording and return audio data
     */
    suspend fun stopRecording(callback: AudioRecordingCallback) = withContext(Dispatchers.IO) {
        if (!isRecording.get()) {
            Log.w(TAG, "⚠️ No recording in progress")
            callback.onRecordingError("No recording in progress")
            return@withContext
        }
        
        Log.d(TAG, "🛑 Stopping audio recording...")
        
        // Stop the recording loop
        isRecording.set(false)
        
        // Wait a moment for the recording thread to finish
        kotlinx.coroutines.delay(100)
        
        // Convert audio data to float array
        val audioSamples = audioData.toFloatArray()
        
        Log.d(TAG, "📊 Recording stopped. Total samples: ${audioSamples.size}")
        Log.d(TAG, "⏱️ Recording duration: ${audioSamples.size / SAMPLE_RATE.toFloat()} seconds")
        Log.d(TAG, "📊 Audio data range: ${audioSamples.minOrNull()} to ${audioSamples.maxOrNull()}")
        Log.d(TAG, "🔄 Calling onRecordingStopped callback...")
        
        // Return the recorded audio
        callback.onRecordingStopped(audioSamples)
        
        Log.d(TAG, "✅ onRecordingStopped callback completed")
    }
    
    /**
     * Check if currently recording
     */
    fun isRecording(): Boolean = isRecording.get()
    
    /**
     * Clean up resources
     */
    fun release() {
        Log.d(TAG, "🧹 Releasing AudioRecorder...")
        
        if (isRecording.get()) {
            isRecording.set(false)
        }
        
        audioRecord?.let {
            try {
                if (it.state == AudioRecord.STATE_INITIALIZED) {
                    it.stop()
                }
                it.release()
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error releasing AudioRecord", e)
            }
        }
        
        audioRecord = null
        audioData.clear()
        
        Log.d(TAG, "✅ AudioRecorder released")
    }
} 