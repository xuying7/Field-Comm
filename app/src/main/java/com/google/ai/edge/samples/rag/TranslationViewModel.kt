package com.google.ai.edge.samples.rag

import android.app.Application
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/**
 * ViewModel for the translation screen.
 * Manages translation state and communicates with the RAG pipeline.
 */
class TranslationViewModel(application: Application) : AndroidViewModel(application) {
    private val ragPipeline = RagPipeline(application)
    private val backgroundExecutor = Executors.newSingleThreadExecutor()
    
    // Translation states
    private val _originalText = mutableStateOf("")
    val originalText: State<String> = _originalText
    
    private val _translatedText = mutableStateOf("")
    val translatedText: State<String> = _translatedText
    
    private val _selectedLanguage = mutableStateOf("English")
    val selectedLanguage: State<String> = _selectedLanguage
    
    private val _isTranslating = mutableStateOf(false)
    val isTranslating: State<Boolean> = _isTranslating
    
    companion object {
        private const val TAG = "TranslationViewModel"
    }
    
    /**
     * Set the original text to be translated
     */
    fun setOriginalText(text: String) {
        _originalText.value = text
    }
    
    /**
     * Set the selected target language
     */
    fun setSelectedLanguage(language: String) {
        _selectedLanguage.value = language
        
        // If we have original text, re-translate with new language
        if (_originalText.value.isNotBlank()) {
            translateText(_originalText.value, language)
        }
    }
    
    /**
     * Translate the given text to the target language
     */
    fun translateText(text: String, targetLanguage: String) {
        if (text.isBlank()) {
            Log.w(TAG, "⚠️ Cannot translate empty text")
            return
        }
        
        if (_isTranslating.value) {
            Log.w(TAG, "⚠️ Translation already in progress")
            return
        }
        
        viewModelScope.launch {
            _isTranslating.value = true
            _translatedText.value = ""
            
            Log.d(TAG, "🌐 Starting translation: '$text' -> $targetLanguage")
            
            try {
                withContext(Dispatchers.IO) {
                    // Create translation prompt
                    val translationPrompt = buildTranslationPrompt(text, targetLanguage)
                    Log.d(TAG, "📝 Translation prompt: $translationPrompt")
                    
                    // Use RAG pipeline for translation
                    ragPipeline.generateResponse(translationPrompt) { response, done ->
                        viewModelScope.launch {
                            // Update translated text progressively
                            _translatedText.value = response.text
                            
                            if (done) {
                                _isTranslating.value = false
                                Log.d(TAG, "✅ Translation completed: ${response.text}")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error during translation", e)
                viewModelScope.launch {
                    _isTranslating.value = false
                    _translatedText.value = "Translation failed: ${e.message}"
                }
            }
        }
    }
    
    /**
     * Build a translation prompt for the RAG pipeline
     */
    private fun buildTranslationPrompt(text: String, targetLanguage: String): String {
        return """
Please translate the following text to $targetLanguage. 
Provide only the translation without any additional explanation or commentary.

Text to translate: "$text"

Translation:
        """.trimIndent()
    }
    
    /**
     * Clear all translation data
     */
    fun clearTranslation() {
        _originalText.value = ""
        _translatedText.value = ""
        _isTranslating.value = false
    }
    
    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "🧹 Cleaning up TranslationViewModel...")
        
        // Clean up executor
        backgroundExecutor.shutdown()
    }
}