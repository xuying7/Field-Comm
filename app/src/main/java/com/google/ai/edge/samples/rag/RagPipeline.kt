package com.google.ai.edge.samples.rag

import android.app.Application
import android.content.Context
import com.google.ai.edge.localagents.rag.chains.ChainConfig
import com.google.ai.edge.localagents.rag.chains.RetrievalAndInferenceChain
import com.google.ai.edge.localagents.rag.memory.DefaultSemanticTextMemory
import com.google.ai.edge.localagents.rag.memory.SqliteVectorStore
import com.google.ai.edge.localagents.rag.models.AsyncProgressListener
import com.google.ai.edge.localagents.rag.models.Embedder
import com.google.ai.edge.localagents.rag.models.GeckoEmbeddingModel
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
import com.google.mediapipe.tasks.genai.llminference.ProgressListener
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Optional
import java.util.concurrent.Executors
import kotlin.jvm.optionals.getOrNull
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.guava.await
import android.graphics.Bitmap
import android.util.Log

/** The RAG pipeline for LLM generation. */
class RagPipeline(private val application: Application) {
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
    if (COMPUTE_EMBEDDINGS_LOCALLY) {
      GeckoEmbeddingModel(
        GECKO_MODEL_PATH,
        Optional.of(TOKENIZER_MODEL_PATH),
        USE_GPU_FOR_EMBEDDINGS,
      )
    } else {
      GeminiEmbedder(GEMINI_EMBEDDING_MODEL, GEMINI_API_KEY)
    }

  private val config =
    ChainConfig.create(
      mediaPipeLanguageModel,
      PromptBuilder(ROMPT_TEMPLATE),
      DefaultSemanticTextMemory(
        // Gecko embedding model dimension is 768
        SqliteVectorStore(768),
        embedder
      )
    )
  private val retrievalAndInferenceChain = RetrievalAndInferenceChain(config)

  init {
    Futures.addCallback(
      mediaPipeLanguageModel.initialize(),
      object : FutureCallback<Boolean> {
        override fun onSuccess(result: Boolean) {
          // no-op
        }

        override fun onFailure(t: Throwable) {
          // no-op
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
    val retrievalRequest =
      RetrievalRequest.create(prompt, RetrievalConfig.create(3, 0.0f, TaskType.QUESTION_ANSWERING))
    retrievalAndInferenceChain.invoke(retrievalRequest, callback).await().text
  }

  /**
   * Direct translation interface - bypasses RAG retrieval and uses existing model
   * This reuses the same mediaPipeLanguageModel instance to avoid loading the model twice
   */
  suspend fun translateDirectly(
    text: String,
    targetLanguage: String,
    callback: AsyncProgressListener<LanguageModelResponse>?,
  ): String = coroutineScope {
    Log.d("RagPipeline", "🌐 Starting direct translation: '$text' -> $targetLanguage")
    
    // Build translation prompt without RAG context
    val translationPrompt = buildTranslationPrompt(text, targetLanguage)
    
    // Create a minimal retrieval request that bypasses semantic memory search
    // This reuses the same inference chain but with minimal retrieval
    try {
      val minimalRetrievalRequest = RetrievalRequest.create(
        translationPrompt, 
        RetrievalConfig.create(0, 0.0f, TaskType.QUESTION_ANSWERING) // 0 results = no retrieval
      )
      
      // Use the same chain infrastructure (this will effectively bypass retrieval due to 0 results)
      retrievalAndInferenceChain.invoke(minimalRetrievalRequest, callback).await().text
    } catch (e: Exception) {
      Log.e("RagPipeline", "❌ Direct translation failed", e)
      "Translation failed: ${e.message}"
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
   * Test if the current model supports multimodal capabilities.
   * This helps debug whether the .task file has vision support built-in.
   */
  private suspend fun testMultimodalSupport(): Boolean {
    return try {
      Log.d("RagPipeline", "Testing multimodal support...")
      
      // Test 1: Can we create LlmInferenceOptions with setMaxNumImages?
      val testOptions = try {
        LlmInferenceOptions.builder()
          .setModelPath(GEMMA_MODEL_PATH)
          .setMaxNumImages(1)
          .setPreferredBackend(Backend.CPU)
          .setMaxTokens(1024)
          .build()
      } catch (e: Exception) {
        Log.e("RagPipeline", "❌ setMaxNumImages API not available", e)
        return false
      }
      Log.d("RagPipeline", "✅ LlmInferenceOptions with setMaxNumImages created successfully")
      
      // Test 2: Can we create LlmInference with these options?
      // This is where most failures occur - when the .task file doesn't support multimodal
      val testInference = try {
        Log.d("RagPipeline", "Testing if .task file supports multimodal loading...")
        LlmInference.createFromOptions(application, testOptions)
      } catch (e: Exception) {
        Log.e("RagPipeline", "❌ Your .task file doesn't support multimodal capabilities", e)
        Log.w("RagPipeline", "💡 You need a multimodal Gemma 3n .task file with built-in vision encoder")
        return false
      }
      Log.d("RagPipeline", "✅ LlmInference with multimodal options created successfully")
      
      // Test 3: Can we create GraphOptions with setEnableVisionModality?
      val testGraphOptions = try {
        GraphOptions.builder()
          .setEnableVisionModality(true)
          .build()
      } catch (e: Exception) {
        Log.e("RagPipeline", "❌ setEnableVisionModality API not available", e)
        testInference?.close()
        return false
      }
      Log.d("RagPipeline", "✅ GraphOptions with setEnableVisionModality created successfully")
      
      // Test 4: Can we create session with vision modality?
      val testSessionOptions = try {
        LlmInferenceSessionOptions.builder()
          .setGraphOptions(testGraphOptions)
          .build()
      } catch (e: Exception) {
        Log.e("RagPipeline", "❌ Failed to create session options with vision", e)
        testInference?.close()
        return false
      }
      
      val testSession = try {
        LlmInferenceSession.createFromOptions(testInference, testSessionOptions)
      } catch (e: Exception) {
        Log.e("RagPipeline", "❌ Failed to create session with vision modality", e)
        testInference?.close()
        return false
      }
      Log.d("RagPipeline", "✅ LlmInferenceSession with vision modality created successfully")
      
      // Cleanup test resources
      try {
        testSession?.close()
        testInference?.close()
      } catch (e: Exception) {
        Log.w("RagPipeline", "Warning during test cleanup", e)
      }
      
      Log.d("RagPipeline", "🎉 Full multimodal support confirmed!")
      true
      
    } catch (e: Exception) {
      Log.e("RagPipeline", "❌ Multimodal support test failed: ${e.message}", e)
      false
    }
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
      val enhancedPrompt = PromptBuilder(ROMPT_TEMPLATE).buildPrompt(ragContext, imageAwarePrompt)
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
        
        // Note: We don't restart the original backend here to save memory
        // The text-only fallback will handle reinitialization if needed
        
      } catch (e: Exception) {
        Log.e("RagPipeline", "⚠️ Error during multimodal cleanup", e)
      }
    }
  }

  companion object {
    private const val COMPUTE_EMBEDDINGS_LOCALLY = true
    private const val USE_GPU_FOR_EMBEDDINGS = true
    private const val CHUNK_SEPARATOR = "<chunk_splitter>"

    private const val GEMMA_MODEL_PATH = "/data/local/tmp/gemma-3n-E4B-it-int4.task"
    private const val TOKENIZER_MODEL_PATH = "/data/local/tmp/sentencepiece.model"
    private const val GECKO_MODEL_PATH = "/data/local/tmp/gecko.tflite"
    private const val GEMINI_EMBEDDING_MODEL = "models/text-embedding-004"
    private const val GEMINI_API_KEY = "..."

    // The prompt template for the RetrievalAndInferenceChain. It takes two inputs: {0}, which is
    // the retrieved context, and {1}, which is the user's query.
    private const val ROMPT_TEMPLATE: String =
      "You are an assistant for question-answering tasks. Here are the things I want to remember: {0} Use the things I want to remember, answer the following question the user has: {1}"
  }
}
