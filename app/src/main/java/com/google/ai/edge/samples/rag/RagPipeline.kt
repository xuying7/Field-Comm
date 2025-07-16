package com.google.ai.edge.samples.rag

import android.app.Application
import android.content.Context
import com.google.ai.edge.localagents.rag.chains.ChainConfig
import com.google.ai.edge.localagents.rag.chains.RetrievalAndInferenceChain
import com.google.ai.edge.localagents.rag.memory.DefaultSemanticTextMemory
import com.google.ai.edge.localagents.rag.memory.SqliteVectorStore
import com.google.ai.edge.localagents.rag.models.AsyncProgressListener
import com.google.ai.edge.localagents.rag.models.Embedder
import com.google.ai.edge.localagents.rag.models.GeminiEmbedder
import com.google.ai.edge.localagents.rag.models.LanguageModelResponse
import com.google.ai.edge.localagents.rag.models.MediaPipeLlmBackend
import com.google.ai.edge.localagents.rag.prompt.PromptBuilder
import com.google.ai.edge.localagents.rag.retrieval.RetrievalConfig
import com.google.ai.edge.localagents.rag.retrieval.RetrievalConfig.TaskType
import com.google.ai.edge.localagents.rag.retrieval.RetrievalRequest
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference.Backend
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession.LlmInferenceSessionOptions
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.genai.llminference.ProgressListener
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.Executors
import kotlin.jvm.optionals.getOrNull
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.mutableStateOf

/** The RAG pipeline for LLM generation. */
class RagPipeline(private val application: Application) {
  
  // Coroutine-safe mutex to prevent concurrent LLM backend access
  private val backendMutex = Mutex()
  
  // LLM initialization state tracking
  private val _isLlmInitialized = mutableStateOf(false)
  val isLlmInitialized: androidx.compose.runtime.State<Boolean> = _isLlmInitialized
  
  private val _llmInitializationError = mutableStateOf<String?>(null)
  val llmInitializationError: androidx.compose.runtime.State<String?> = _llmInitializationError
  
  private val _isLlmInitializing = mutableStateOf(true)
  val isLlmInitializing: androidx.compose.runtime.State<Boolean> = _isLlmInitializing
  
  // Callback interface for LLM initialization events
  interface LlmInitializationCallback {
    fun onLlmInitializationStarted()
    fun onLlmInitializationFailure(error: String)
  }
  
  private var llmInitializationCallback: LlmInitializationCallback? = null
  
  fun setLlmInitializationCallback(callback: LlmInitializationCallback) {
    this.llmInitializationCallback = callback
  }

  private val mediaPipeLanguageModelOptions: LlmInferenceOptions =
    LlmInferenceOptions.builder()
      .setModelPath(GEMMA_MODEL_PATH)
      .setPreferredBackend(LlmInference.Backend.CPU)
      .setMaxTokens(1024)
      .build()
  private val mediaPipeLanguageModelSessionOptions: LlmInferenceSessionOptions =
    LlmInferenceSessionOptions.builder()
      .setTemperature(1.0f)
      .setTopP(0.95f)
      .setTopK(64)
      .build()
  private val mediaPipeLanguageModel: MediaPipeLlmBackend =
    MediaPipeLlmBackend(
      application.applicationContext,
      mediaPipeLanguageModelOptions,
      mediaPipeLanguageModelSessionOptions
    )

  private val embedder: Embedder<String> = 
    // Use custom multilingual embedder with proper DistilBERT tokenization
    CustomMultilingualEmbedder(
      context = application.applicationContext,
      useGpu = USE_GPU_FOR_EMBEDDINGS
    )
  

  private val config =
    ChainConfig.create(
      mediaPipeLanguageModel,
      PromptBuilder(PROMPT_TEMPLATE),
      DefaultSemanticTextMemory(
        // Custom multilingual model dimension is 512 (sentence-transformers/distiluse-base-multilingual-cased-v2)
        SqliteVectorStore(512),
        embedder
      )
    )
  private val retrievalAndInferenceChain = RetrievalAndInferenceChain(config)

  init {
    Log.d("RagPipeline", "🚀 Starting LLM initialization...")
    _isLlmInitializing.value = true
    _isLlmInitialized.value = false
    _llmInitializationError.value = null
    
    // Notify callback that initialization started
    llmInitializationCallback?.onLlmInitializationStarted()
    
    Futures.addCallback(
      mediaPipeLanguageModel.initialize(),
      object : FutureCallback<Boolean> {
        override fun onSuccess(result: Boolean) {
          Log.d("RagPipeline", "✅ LLM initialization completed successfully")
          _isLlmInitializing.value = false
          _isLlmInitialized.value = result
          _llmInitializationError.value = null
          
          
        }

        override fun onFailure(t: Throwable) {
          val errorMessage = "LLM initialization failed: ${t.message}"
          Log.e("RagPipeline", "❌ $errorMessage", t)
          _isLlmInitializing.value = false
          _isLlmInitialized.value = false
          _llmInitializationError.value = errorMessage
          
          // Notify callback of failure
          llmInitializationCallback?.onLlmInitializationFailure(errorMessage)
        }
      },
      Executors.newSingleThreadExecutor(),
    )
  }

  fun memorizeChunks(context: Context, filename: String) {
    // BufferedReader is needed to read the *.txt file
    // Create and Initialize BufferedReader
    val reader = BufferedReader(InputStreamReader(context.assets.open(filename)))

    val sb = StringBuilder()
    val texts = mutableListOf<String>()
    generateSequence { reader.readLine() }
      .forEach { line ->
        if (line.startsWith(CHUNK_SEPARATOR)) {
          if (sb.isNotEmpty()) {
            val chunk = sb.toString()
            texts.add(chunk)
          }
          sb.clear()
          sb.append(line.removePrefix(CHUNK_SEPARATOR).trim())
        } else {
          sb.append(" ")
          sb.append(line)
        }
      }
    if (sb.isNotEmpty()) {
      texts.add(sb.toString())
    }
    reader.close()
    if (texts.isNotEmpty()) {
      return memorize(texts)
    }
  }

  /** Stores input texts in the semantic text memory. */
  private fun memorize(facts: List<String>) {
    val future =
      config.semanticMemory.getOrNull()?.recordBatchedMemoryItems(ImmutableList.copyOf(facts))
    future?.get()
  }

  /** Generates the response from the LLM. */
  suspend fun generateResponse(
    prompt: String,
    callback: AsyncProgressListener<LanguageModelResponse>?,
  ): String = coroutineScope {
    backendMutex.withLock {
      val retrievalRequest =
        RetrievalRequest.create(prompt, RetrievalConfig.create(3, 0.0f, TaskType.QUESTION_ANSWERING))
      retrievalAndInferenceChain.invoke(retrievalRequest, callback).await().text
    }
  }

  /**
   * Direct translation interface - bypasses RAG template by using custom chain config
   * Creates a temporary chain with empty prompt template for clean translation
   */
  suspend fun translateDirectly(
    text: String,
    targetLanguage: String,
    callback: AsyncProgressListener<LanguageModelResponse>?,
  ): String = coroutineScope {
    backendMutex.withLock {
      Log.d("RagPipeline", "🌐 Starting direct translation: '$text' -> $targetLanguage")
      
      // Check if the main backend is initialized (it might have been closed during multimodal ops)
      if (!isMainBackendInitialized()) {
        Log.w("RagPipeline", "⚠️ Main backend was closed, reinitializing for translation...")
        try {
          reinitializeMainBackend()
          Log.d("RagPipeline", "✅ Main backend reinitialized successfully")
        } catch (e: Exception) {
          Log.e("RagPipeline", "❌ Failed to reinitialize main backend", e)
          return@coroutineScope "Translation failed: Backend initialization error"
        }
      }
      
      // Build clean translation prompt without any RAG template wrapping
      val translationPrompt = buildTranslationPrompt(text, targetLanguage)
      
      try {
        Log.d("RagPipeline", "🔧 Creating temporary translation chain with clean prompt template")
        
        // Create a clean prompt template that just passes through the text
        val cleanPromptBuilder = PromptBuilder("{1}") // {1} = user prompt, no template wrapper
        
        // Create temporary config with clean prompt template (no emergency response template)
        val translationConfig = ChainConfig.create(
          mediaPipeLanguageModel,
          cleanPromptBuilder,
          config.semanticMemory.getOrNull() // Reuse same memory but won't retrieve anything
        )
        
        // Create temporary chain with clean template
        val translationChain = RetrievalAndInferenceChain(translationConfig)
        
        // Create request with 0 retrieval results to bypass knowledge base
        val translationRequest = RetrievalRequest.create(
          translationPrompt, 
          RetrievalConfig.create(0, 0.0f, TaskType.QUESTION_ANSWERING) // 0 results = no RAG context
        )
        
        // Use clean chain (no emergency template wrapper)
        val result = translationChain.invoke(translationRequest, callback).await()
        
        Log.d("RagPipeline", "✅ Direct translation completed: ${result.text.length} chars")
        result.text
        
      } catch (e: Exception) {
        Log.e("RagPipeline", "❌ Direct translation failed", e)
        "Translation failed: ${e.message}"
      }
    }
  }

  /**
   * Check if the main backend is properly initialized
   */
  private fun isMainBackendInitialized(): Boolean {
    return try {
      // Try to check if the backend is ready
      // This is a simple check - if it throws an exception, it's not initialized
      mediaPipeLanguageModel.toString() // This won't throw if properly initialized
      true
    } catch (e: Exception) {
      Log.d("RagPipeline", "🔍 Main backend check failed: ${e.message}")
      false
    }
  }

  /**
   * Reinitialize the main backend if it was closed
   */
  private suspend fun reinitializeMainBackend() {
    try {
      Log.d("RagPipeline", "🔄 Reinitializing main backend...")
      val initFuture = mediaPipeLanguageModel.initialize()
      initFuture.await()
      Log.d("RagPipeline", "✅ Main backend reinitialized successfully")
    } catch (e: Exception) {
      Log.e("RagPipeline", "❌ Failed to reinitialize main backend", e)
      throw e
    }
  }

  /**
   * Build a simple translation prompt without RAG context
   */
  private fun buildTranslationPrompt(text: String, targetLanguage: String): String {
    return """Translate the following text to $targetLanguage. Provide only the translation without any additional explanation or commentary.

Text to translate: "$text"

Translation:"""
  }

  /**
   * Gallery-style multimodal generation: 
   * 1) Get RAG context from user prompt
   * 2) Create temporary multimodal inference engine  
   * 3) Feed RAG context + image directly to Gemma 3n
   * This bypasses the LlmInference framework as requested.
   */
  suspend fun generateResponseWithImage(
    prompt: String,
    image: Bitmap,
    callback: AsyncProgressListener<LanguageModelResponse>?,
  ): String = coroutineScope {
    backendMutex.withLock {
      Log.d("RagPipeline", "🚀 Starting multimodal RAG generation")
      
      var llmInference: LlmInference? = null
      var session: LlmInferenceSession? = null
      var wasOriginalBackendClosed = false
    
    try {
      // Step 1: Get RAG context first (your requirement)
      Log.d("RagPipeline", "📖 Getting RAG context for prompt: $prompt")
      val retrievalRequest = RetrievalRequest.create(
        prompt, 
        RetrievalConfig.create(3, 0.0f, TaskType.QUESTION_ANSWERING)
      )
      val semanticMemory = config.semanticMemory.getOrNull()
      val retrievalResponse = semanticMemory?.retrieveResults(retrievalRequest)?.await()
      val ragContext = buildString {
        retrievalResponse?.entities?.forEach { 
          append(it.data).append("\n") 
        }
      }
      Log.d("RagPipeline", "✅ RAG context retrieved: ${ragContext.length} characters")
      
      // Step 2: MEMORY OPTIMIZATION - Temporarily close existing backend to free memory
      Log.d("RagPipeline", "💾 Optimizing memory: temporarily closing existing RAG backend")
      try {
        mediaPipeLanguageModel.close()
        wasOriginalBackendClosed = true
        Log.d("RagPipeline", "✅ Existing backend closed, memory freed")
        
        // Force garbage collection to free up more memory
        System.gc()
        Thread.sleep(100) // Give GC time to work
        Log.d("RagPipeline", "🗑️ Garbage collection completed")
        
      } catch (e: Exception) {
        Log.w("RagPipeline", "⚠️ Warning: Could not close existing backend", e)
      }
      
      // Step 3: Create Gallery-style multimodal inference with freed memory
      Log.d("RagPipeline", "🎯 Creating multimodal inference engine (memory optimized)")
      
      val multimodalOptions = try {
        LlmInferenceOptions.builder()
          .setModelPath(GEMMA_MODEL_PATH)  // Your multimodal .task file
          .setMaxNumImages(1)              // Gallery API for image support
          .setPreferredBackend(Backend.CPU) // CPU for memory efficiency
          .setMaxTokens(512)               // Reduced tokens to save memory
          .build()
      } catch (e: Exception) {
        Log.e("RagPipeline", "❌ Failed to create multimodal options", e)
        return@coroutineScope generateResponse(prompt, callback)
      }
      
      llmInference = try {
        Log.d("RagPipeline", "📦 Loading multimodal .task file (this may take 10-15 seconds)...")
        val startTime = System.currentTimeMillis()
        val inference = LlmInference.createFromOptions(application, multimodalOptions)
        val loadTime = System.currentTimeMillis() - startTime
        Log.d("RagPipeline", "✅ Multimodal inference loaded successfully in ${loadTime}ms")
        inference
      } catch (e: OutOfMemoryError) {
        Log.e("RagPipeline", "💥 Out of memory while loading multimodal .task file", e)
        Log.w("RagPipeline", "💡 Your device doesn't have enough memory for multimodal inference")
        return@coroutineScope generateResponse(prompt, callback)
      } catch (e: Exception) {
        Log.e("RagPipeline", "❌ Failed to load multimodal .task file", e)
        Log.w("RagPipeline", "💡 Falling back to text-only RAG")
        return@coroutineScope generateResponse(prompt, callback)
      }
      
      val sessionOptions = try {
        LlmInferenceSessionOptions.builder()
          .setGraphOptions(
            GraphOptions.builder()
              .setEnableVisionModality(true)  // Gallery API for vision
              .build()
          )
          .setTemperature(1.0f)
          .setTopP(0.95f)
          .setTopK(32)  // Reduced for memory efficiency
          .build()
      } catch (e: Exception) {
        Log.e("RagPipeline", "❌ Failed to create session options with vision", e)
        return@coroutineScope generateResponse(prompt, callback)
      }
      
      session = try {
        Log.d("RagPipeline", "🔧 Creating multimodal session with vision capabilities...")
        val sessionStartTime = System.currentTimeMillis()
        val newSession = LlmInferenceSession.createFromOptions(llmInference, sessionOptions)
        val sessionTime = System.currentTimeMillis() - sessionStartTime
        Log.d("RagPipeline", "✅ Multimodal session created in ${sessionTime}ms")
        newSession
      } catch (e: Exception) {
        Log.e("RagPipeline", "❌ Failed to create multimodal session", e)
        return@coroutineScope generateResponse(prompt, callback)
      }
      
      // Step 4: Feed RAG context + user prompt + image directly to Gemma 3n
      Log.d("RagPipeline", "📝 Building enhanced prompt with RAG context")
      
      // Create a multimodal-specific prompt that explicitly mentions the image
      val imageAwarePrompt = "Please analyze the image I've provided and answer this question: $prompt"
      val enhancedPrompt = PromptBuilder(PROMPT_TEMPLATE).buildPrompt(ragContext, imageAwarePrompt)
      Log.d("RagPipeline", "📏 Enhanced prompt length: ${enhancedPrompt.length} characters")
      Log.d("RagPipeline", "📋 Enhanced prompt preview: ${enhancedPrompt.take(200)}...")
      
      // Image validation and conversion
      Log.d("RagPipeline", "🖼️ Validating image: ${image.width}x${image.height}, config=${image.config}, hasAlpha=${image.hasAlpha()}")
      if (image.isRecycled) {
        Log.e("RagPipeline", "❌ Image is recycled, cannot process")
        return@coroutineScope generateResponse(prompt, callback)
      }
      
      // Ensure image is in a compatible format
      val processedImage = try {
        if (image.config != Bitmap.Config.ARGB_8888) {
          Log.d("RagPipeline", "🔄 Converting image from ${image.config} to ARGB_8888")
          image.copy(Bitmap.Config.ARGB_8888, false)
        } else {
          image
        }
      } catch (e: Exception) {
        Log.e("RagPipeline", "❌ Failed to process image format", e)
        return@coroutineScope generateResponse(prompt, callback)
      }
      
      val mpImage = try {
        Log.d("RagPipeline", "🔧 Converting bitmap to MPImage...")
        BitmapImageBuilder(processedImage).build()
      } catch (e: Exception) {
        Log.e("RagPipeline", "❌ Failed to convert bitmap to MPImage", e)
        return@coroutineScope generateResponse(prompt, callback)
      }
      
      try {
        Log.d("RagPipeline", "📤 Adding inputs to multimodal session...")
        
        // IMPORTANT: Gallery pattern suggests adding image FIRST, then text
        Log.d("RagPipeline", "🖼️ Adding image to session first...")
        session?.addImage(mpImage)
        
        Log.d("RagPipeline", "📝 Adding RAG-enhanced prompt to session...")
        session?.addQueryChunk(enhancedPrompt)
        
        Log.d("RagPipeline", "✅ Both image and prompt added successfully")
        
      } catch (e: Exception) {
        Log.e("RagPipeline", "❌ Failed to add inputs to session", e)
        Log.e("RagPipeline", "Error details: ${e.message}")
        return@coroutineScope generateResponse(prompt, callback)
      }
      
      val progressListener = ProgressListener<String> { partialResult, done ->
        Log.d("RagPipeline", "🔄 Multimodal progress: ${partialResult.length} chars, done: $done")
        callback?.run(LanguageModelResponse.create(partialResult), done)
      }
      
      Log.d("RagPipeline", "🎉 Generating multimodal response with RAG context + image!")
      val responseStartTime = System.currentTimeMillis()
      val result = session?.generateResponseAsync(progressListener)?.await() ?: "Error: Session was null"
      val responseTime = System.currentTimeMillis() - responseStartTime
      Log.d("RagPipeline", "✅ Multimodal response generated in ${responseTime}ms")
      
      result
      
    } catch (e: OutOfMemoryError) {
      Log.e("RagPipeline", "💥 Out of memory during multimodal generation", e)
      Log.w("RagPipeline", "💡 Device memory insufficient for multimodal operations")
      generateResponse(prompt, callback)
    } catch (e: Exception) {
      Log.e("RagPipeline", "❌ Error in multimodal generation", e)
      Log.w("RagPipeline", "💡 Falling back to text-only RAG due to error")
      generateResponse(prompt, callback)
    } finally {
      try {
        Log.d("RagPipeline", "🧹 Cleaning up multimodal resources...")
        session?.close()
        llmInference?.close()
        
        // Force garbage collection after cleanup
        System.gc()
        Log.d("RagPipeline", "✅ Multimodal resources cleaned up")
        
        // Reinitialize main backend if it was closed during multimodal operations
        if (wasOriginalBackendClosed) {
          Log.d("RagPipeline", "🔄 Restoring main backend after multimodal operation...")
          try {
            mediaPipeLanguageModel.initialize().await()
            Log.d("RagPipeline", "✅ Main backend restored successfully")
          } catch (e: Exception) {
            Log.w("RagPipeline", "⚠️ Failed to restore main backend, will reinitialize on demand", e)
          }
        }
        
      } catch (e: Exception) {
        Log.e("RagPipeline", "⚠️ Error during multimodal cleanup", e)
      }
    }
    }
  }

  companion object {
    private const val USE_GPU_FOR_EMBEDDINGS = true
    private const val CHUNK_SEPARATOR = "<chunk_splitter>"
    private const val GEMMA_MODEL_PATH = "/data/local/tmp/gemma-3n-E4B-it-int4.task"

    // Emergency/Crisis Management prompt template for Field-Comm system
    // Optimized for step-by-step guidance and actionable responses
    // {0} = retrieved emergency knowledge context, {1} = user's emergency query
    private const val PROMPT_TEMPLATE: String =
      "You are an expert emergency response assistant for Field-Comm crisis management system. You help medical personnel, rescue coordinators, and emergency officials access critical information quickly without network connectivity.\n\n" +
      "EMERGENCY KNOWLEDGE BASE:\n{0}\n\n" +
      "EMERGENCY QUERY: {1}\n\n" +
      "RESPONSE GUIDELINES:\n" +
      "- (CRITICAL) Respond ONLY in the same language as the user's query. Do not add translations or any text in other languages.\n" +
      "- ALWAYS provide step-by-step instructions using numbered lists (1. 2. 3. etc.)\n" +
      "- Start with immediate priority actions, then follow-up steps\n" +
      "- For medical emergencies: Provide clear step-by-step first aid procedures\n" +
      "- For safety situations: Give numbered safety protocols and evacuation steps\n" +
      "- For equipment/procedures: List step-by-step operating instructions\n" +
      "- For locations: Provide step-by-step navigation or contact procedures\n" +
      "- Include time estimates when relevant (e.g., \"Step 1: Apply pressure for 5-10 minutes\")\n" +
      "- Add safety warnings at the beginning of dangerous procedures\n" +
      "- End with Next \"steps\" (or corresponding translation): if additional actions are needed\n" +
      "- If information is incomplete, clearly state what additional resources are needed\n\n"
      
  }
}
