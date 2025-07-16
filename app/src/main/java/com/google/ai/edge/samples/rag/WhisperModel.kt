package com.google.ai.edge.samples.rag

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * WhisperModel - High-level interface using the proven WhisperEngineNative implementation
 * 
 * This follows the proven architecture pattern while maintaining compatibility 
 * with the existing ChatViewModel interface.
 */
class WhisperModel(private val context: Context) {
    
    private var whisperEngine: WhisperEngineNative? = null
    private val mInProgress = AtomicBoolean(false)
    private var mIsInitialized = false
    
    companion object {
        private const val TAG = "WhisperModel"
        private const val MODEL_PATH = "models/whisper-small.tflite"
        
        // Messages for status updates
        const val MSG_PROCESSING = "Processing..."
        const val MSG_PROCESSING_DONE = "Processing done...!"
        const val MSG_MODEL_NOT_FOUND = "Model file not found...!"
        const val MSG_INITIALIZATION_FAILED = "Model initialization failed"
    }
    
    /**
     * Interface for WhisperModel callbacks - maintains compatibility
     */
    interface WhisperListener {
        fun onUpdateReceived(message: String)
        fun onResultReceived(result: String)
    }
    
    private var mUpdateListener: WhisperListener? = null
    
    fun setListener(listener: WhisperListener) {
        this.mUpdateListener = listener
    }
    
    /**
     * Initialize the Whisper model - following proven pattern
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "🚀 Initializing Whisper model (proven pattern)...")
            sendUpdate("Initializing Whisper model...")
            
            // Check if model file exists
            val modelFile = File(MODEL_PATH)
            if (!modelFile.exists()) {
                Log.e(TAG, "❌ Model file not found at: $MODEL_PATH")
                sendUpdate(MSG_MODEL_NOT_FOUND)
                return@withContext false
            }
            
            Log.d(TAG, "📁 Model file found: ${modelFile.length()} bytes")
            
            // Initialize native engine following proven pattern
            whisperEngine = WhisperEngineNative(context)
            
            // Load model - vocabulary is embedded in C++ implementation
            val success = whisperEngine?.initialize(
                modelPath = MODEL_PATH,
                vocabPath = "", // Not used in native implementation
                multilingual = true // Enable multilingual support
            ) ?: false
            
            if (success) {
                mIsInitialized = true
                Log.d(TAG, "✅ Whisper model initialized successfully")
                sendUpdate("Whisper model initialized successfully")
                true
            } else {
                Log.e(TAG, "❌ Failed to initialize native engine")
                sendUpdate(MSG_INITIALIZATION_FAILED)
                false
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error initializing Whisper model", e)
            sendUpdate(MSG_INITIALIZATION_FAILED)
            false
        }
    }
    
    /**
     * Transcribe audio buffer - main transcription method
     */
    suspend fun transcribeAudio(audioSamples: FloatArray): String? = withContext(Dispatchers.IO) {
        if (!mInProgress.compareAndSet(false, true)) {
            Log.d(TAG, "Transcription already in progress...")
            return@withContext null
        }
        
        return@withContext try {
            if (!isInitialized()) {
                Log.e(TAG, "❌ Model not initialized")
                sendUpdate("Engine not initialized")
                return@withContext null
            }
            
            // === DETAILED AUDIO ANALYSIS ON KOTLIN SIDE ===
            Log.d(TAG, "=== KOTLIN AUDIO ANALYSIS ===")
            Log.d(TAG, "🎙️ Starting transcription...")
            Log.d(TAG, "📊 Input audio samples count: ${audioSamples.size}")
            
            if (audioSamples.isNotEmpty()) {
                val minVal = audioSamples.minOrNull() ?: 0f
                val maxVal = audioSamples.maxOrNull() ?: 0f
                val avgVal = audioSamples.average().toFloat()
                val silentCount = audioSamples.count { kotlin.math.abs(it) < 0.001f }
                val silenceRatio = silentCount.toFloat() / audioSamples.size
                
                Log.d(TAG, "📈 Audio statistics:")
                Log.d(TAG, "  - Min: $minVal, Max: $maxVal, Avg: $avgVal")
                Log.d(TAG, "  - Silence ratio: $silenceRatio ($silentCount/${audioSamples.size})")
                
                // Show first and last samples
                val firstSamples = audioSamples.take(10).joinToString(" ", "First 10: ")
                val lastSamples = audioSamples.takeLast(10).joinToString(" ", "Last 10: ")
                Log.d(TAG, "  - $firstSamples")
                Log.d(TAG, "  - $lastSamples")
                
                // Check for obvious problems
                when {
                    silenceRatio > 0.9f -> Log.w(TAG, "⚠️ WARNING: Audio is mostly silent ($silenceRatio)")
                    maxVal > 1.0f -> Log.w(TAG, "⚠️ WARNING: Audio values exceed 1.0 (max: $maxVal)")
                    minVal < -1.0f -> Log.w(TAG, "⚠️ WARNING: Audio values below -1.0 (min: $minVal)")
                    maxVal < 0.01f && minVal > -0.01f -> Log.w(TAG, "⚠️ WARNING: Audio signal very weak (range: $minVal to $maxVal)")
                    else -> Log.d(TAG, "✅ Audio signal looks reasonable")
                }
                
                // Expected sample rate analysis
                val durationSeconds = audioSamples.size / 16000.0f
                Log.d(TAG, "📏 Assuming 16kHz: duration = ${durationSeconds}s")
                if (durationSeconds < 0.1f) {
                    Log.w(TAG, "⚠️ WARNING: Very short audio (${durationSeconds}s)")
                } else if (durationSeconds > 30.5f) {
                    Log.w(TAG, "⚠️ WARNING: Very long audio (${durationSeconds}s)")
                }
            }
            
            sendUpdate(MSG_PROCESSING)
            
            val startTime = System.currentTimeMillis()
            
            // === CALL TO NATIVE ENGINE ===
            Log.d(TAG, "=== CALLING NATIVE ENGINE ===")
            Log.d(TAG, "📞 Calling whisperEngine.transcribeBuffer() with ${audioSamples.size} samples...")
            
            // Use the proven engine for transcription
            val result = whisperEngine?.transcribeBuffer(audioSamples) ?: ""
            
            val timeTaken = System.currentTimeMillis() - startTime
            
            // === DETAILED RESULT ANALYSIS ===
            Log.d(TAG, "=== TRANSCRIPTION RESULT ANALYSIS ===")
            Log.d(TAG, "⏱️ Time taken for transcription: ${timeTaken}ms")
            Log.d(TAG, "📝 Raw result from native: '$result'")
            Log.d(TAG, "📏 Result length: ${result.length} characters")
            
            if (result.isNotEmpty()) {
                Log.d(TAG, "🔍 Character analysis:")
                val charBreakdown = result.mapIndexed { index, char ->
                    when {
                        char == ' ' -> "[$index:SPACE]"
                        char.code in 32..126 -> "[$index:'$char']"
                        else -> "[$index:${char.code}]"
                    }
                }.joinToString(" ")
                Log.d(TAG, "  Characters: $charBreakdown")
                
                // Look for suspicious patterns
                val commaCount = result.count { it == ',' }
                val questionCount = result.count { it == '?' }
                val digitCount = result.count { it.isDigit() }
                val letterCount = result.count { it.isLetter() }
                
                Log.d(TAG, "🔍 Content analysis:")
                Log.d(TAG, "  - Letters: $letterCount, Digits: $digitCount")
                Log.d(TAG, "  - Commas: $commaCount, Questions: $questionCount")
                
                if (digitCount > letterCount) {
                    Log.w(TAG, "⚠️ WARNING: More digits than letters - possible encoding issue")
                }
                if (commaCount > 3) {
                    Log.w(TAG, "⚠️ WARNING: Too many commas - possible token fragmentation")
                }
                
                // Check for expected words from "hello how are you good morning"
                val expectedWords = listOf("hello", "how", "are", "you", "good", "morning")
                val foundWords = expectedWords.filter { result.lowercase().contains(it) }
                Log.d(TAG, "🎯 Expected word detection: ${foundWords.size}/${expectedWords.size}")
                Log.d(TAG, "  Found: ${foundWords.joinToString(", ")}")
                
                if (foundWords.isEmpty()) {
                    Log.e(TAG, "❌ CRITICAL: No expected words found in result!")
                    Log.e(TAG, "  Expected: ${expectedWords.joinToString(", ")}")
                    Log.e(TAG, "  Got: '$result'")
                }
            } else {
                Log.w(TAG, "⚠️ Empty result from native engine")
            }
            
            val finalResult = if (result.isNotBlank()) result else "No speech detected"
            sendResult(finalResult)
            sendUpdate(MSG_PROCESSING_DONE)
            
            Log.d(TAG, "✅ Transcription process completed")
            Log.d(TAG, "📤 Final result: '$finalResult'")
            
            finalResult
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ EXCEPTION during transcription", e)
            Log.e(TAG, "  Exception type: ${e.javaClass.simpleName}")
            Log.e(TAG, "  Exception message: ${e.message}")
            Log.e(TAG, "  Stack trace: ${e.stackTraceToString()}")
            sendUpdate("Transcription failed: ${e.message}")
            null
        } finally {
            mInProgress.set(false)
            Log.d(TAG, "🏁 Transcription finally block executed")
        }
    }
    
    /**
     * Check if engine is initialized
     */
    private fun isInitialized(): Boolean {
        return mIsInitialized && whisperEngine != null && whisperEngine?.isInitialized() == true
    }
    
    /**
     * Send update message to listener
     */
    private fun sendUpdate(message: String) {
        mUpdateListener?.onUpdateReceived(message)
    }
    
    /**
     * Send result message to listener
     */
    private fun sendResult(message: String) {
        mUpdateListener?.onResultReceived(message)
    }
    
    /**
     * Clean up resources
     */
    fun close() {
        try {
            whisperEngine?.deinitialize()
            whisperEngine = null
            mIsInitialized = false
            Log.d(TAG, "✅ WhisperModel resources released")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error closing WhisperModel", e)
        }
    }
} 