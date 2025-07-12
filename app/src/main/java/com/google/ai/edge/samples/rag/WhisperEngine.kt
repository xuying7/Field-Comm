package com.google.ai.edge.samples.rag

import java.io.IOException

interface WhisperEngine {
    fun isInitialized(): Boolean
    
    @Throws(IOException::class)
    fun initialize(modelPath: String, vocabPath: String, multilingual: Boolean): Boolean
    
    fun deinitialize()
    
    fun transcribeBuffer(samples: FloatArray): String
} 