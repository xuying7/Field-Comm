package com.google.ai.edge.samples.rag

import android.content.Context
import android.util.Log

class WhisperEngineNative(private val context: Context) : WhisperEngine {
    
    companion object {
        private const val TAG = "WhisperEngineNative"
        
        init {
            System.loadLibrary("audioEngine")
        }
    }
    
    private val nativePtr: Long = createTFLiteEngine() // Native pointer to the TFLiteEngine instance
    private var mIsInitialized = false

    override fun isInitialized(): Boolean {
        return mIsInitialized
    }

    override fun initialize(modelPath: String, vocabPath: String, multilingual: Boolean): Boolean {
        val ret = loadModel(modelPath, multilingual)
        Log.d(TAG, "Model is loaded...$modelPath")
        
        mIsInitialized = true
        return true
    }

    override fun deinitialize() {
        freeModel()
    }

    override fun transcribeBuffer(samples: FloatArray): String {
        return transcribeBuffer(nativePtr, samples)
    }

    private fun loadModel(modelPath: String, isMultilingual: Boolean): Int {
        return loadModel(nativePtr, modelPath, isMultilingual)
    }

    private fun freeModel() {
        freeModel(nativePtr)
    }

    // Native methods - these must match the C++ JNI implementation exactly
    private external fun createTFLiteEngine(): Long
    private external fun loadModel(nativePtr: Long, modelPath: String, isMultilingual: Boolean): Int
    private external fun freeModel(nativePtr: Long)
    private external fun transcribeBuffer(nativePtr: Long, samples: FloatArray): String
} 