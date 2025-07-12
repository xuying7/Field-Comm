package com.google.ai.edge.samples.rag

import android.content.Context
import com.google.ai.edge.localagents.rag.models.Embedder
import com.google.ai.edge.localagents.rag.models.EmbeddingRequest
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.Executors
import org.tensorflow.lite.Interpreter

/**
 * Universal Sentence Encoder implementation using TensorFlow Lite.
 * Loads the model from external storage for on-device embedding computation.
 */
class UniversalSentenceEncoderEmbedder(
  private val context: Context,
  private val modelPath: String
) : Embedder<String> {

  private val interpreter: Interpreter
  private val executor = Executors.newSingleThreadExecutor()

  init {
    android.util.Log.e("UniversalSentenceEncoder", "🚀 CONSTRUCTOR CALLED with modelPath: $modelPath")
    try {
      // Check if model file exists
      val modelFile = File(modelPath)
      android.util.Log.e("UniversalSentenceEncoder", "📁 Checking if model exists at: $modelPath")
      if (!modelFile.exists()) {
        android.util.Log.e("UniversalSentenceEncoder", "❌ MODEL FILE NOT FOUND at: $modelPath")
        throw RuntimeException("Universal Sentence Encoder model not found at: $modelPath")
      }
      android.util.Log.e("UniversalSentenceEncoder", "✅ Model file found: ${modelFile.length()} bytes")
      
      val interpreterOptions = Interpreter.Options()
      // Disable accelerators for text models with custom ops
      interpreterOptions.setUseXNNPACK(false)
      interpreterOptions.setUseNNAPI(false)
      interpreterOptions.setNumThreads(4)
      
      val modelByteBuffer = loadModelFile(modelPath)
      interpreter = Interpreter(modelByteBuffer, interpreterOptions)
      
    } catch (e: Exception) {
      throw RuntimeException("Failed to initialize Universal Sentence Encoder. Error: ${e.message}", e)
    }
  }

  private fun loadModelFile(modelPath: String): ByteBuffer {
    val file = File(modelPath)
    val inputStream = FileInputStream(file)
    val fileChannel = inputStream.channel
    return fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size())
  }
  
  /**
   * Implements the batch embedding method required by the interface.
   */
  override fun getBatchEmbeddings(
    request: EmbeddingRequest<String>
  ): ListenableFuture<ImmutableList<ImmutableList<Float>>> {
    android.util.Log.d("UniversalSentenceEncoder", "🔢 getBatchEmbeddings called with ${request.getEmbedData().size} items")
    val future = SettableFuture.create<ImmutableList<ImmutableList<Float>>>()
    executor.execute {
      try {
        val batchEmbeddings = ImmutableList.builder<ImmutableList<Float>>()
        // --- CORRECTED: Use request.getEmbedData() to access the list ---
        for (embedData in request.getEmbedData()) {
          val text = embedData.data
          android.util.Log.d("UniversalSentenceEncoder", "📄 Processing text: ${text.take(50)}...")
          batchEmbeddings.add(ImmutableList.copyOf(runInference(text)))
        }
        val result = batchEmbeddings.build()
        android.util.Log.d("UniversalSentenceEncoder", "✅ getBatchEmbeddings completed: ${result.size} embeddings generated")
        future.set(result)
      } catch (e: Exception) {
        android.util.Log.e("UniversalSentenceEncoder", "❌ getBatchEmbeddings failed: ${e.message}", e)
        future.setException(e)
      }
    }
    return future
  }
  
  /**
   * Implements the single item embedding method required by the interface.
   */
  override fun getEmbeddings(
    request: EmbeddingRequest<String>
  ): ListenableFuture<ImmutableList<Float>> {
    android.util.Log.d("UniversalSentenceEncoder", "🔍 getEmbeddings called for single item")
    val future = SettableFuture.create<ImmutableList<Float>>()
    executor.execute {
      try {
        // --- CORRECTED: Use request.getEmbedData() to get the first item ---
        val embedData = request.getEmbedData().first()
        val text = embedData.data
        android.util.Log.d("UniversalSentenceEncoder", "🔍 Processing query: ${text.take(50)}...")
        val embedding = runInference(text)
        android.util.Log.d("UniversalSentenceEncoder", "✅ getEmbeddings completed: ${embedding.size}-dim vector")
        future.set(ImmutableList.copyOf(embedding))
      } catch (e: Exception) {
        android.util.Log.e("UniversalSentenceEncoder", "❌ getEmbeddings failed: ${e.message}", e)
        future.setException(e)
      }
    }
    return future
  }
  
  /**
   * Helper function to run inference for a single string using TensorFlow Lite.
   */
  private fun runInference(text: String): List<Float> {
    try {
      val startTime = System.currentTimeMillis()
      val inputs = arrayOf(text)
      val output = Array(1) { FloatArray(512) } // Universal Sentence Encoder outputs 512-dim vectors
      interpreter.run(inputs, output)
      val inferenceTime = System.currentTimeMillis() - startTime
      android.util.Log.d("UniversalSentenceEncoder", "⏱️ Inference time: ${inferenceTime}ms for text length: ${text.length}")
      return output[0].toList()
    } catch (e: Exception) {
      android.util.Log.e("UniversalSentenceEncoder", "❌ Inference failed: ${e.message}")
      throw RuntimeException("Inference failed for text: '$text'. Error: ${e.message}", e)
    }
  }

  /**
   * Test method to verify our embedder actually works
   */
  fun testEmbedder(): Boolean {
    return try {
      android.util.Log.d("UniversalSentenceEncoder", "🧪 Testing embedder with sample text...")
      val testText = "This is a test sentence for embedding."
      val embedding = runInference(testText)
      android.util.Log.d("UniversalSentenceEncoder", "✅ Test successful: Generated ${embedding.size}-dim vector")
      android.util.Log.d("UniversalSentenceEncoder", "📊 Sample values: [${embedding.take(5).joinToString(", ")}...]")
      embedding.size == 512 && embedding.any { it != 0.0f }
    } catch (e: Exception) {
      android.util.Log.e("UniversalSentenceEncoder", "❌ Test failed: ${e.message}", e)
      false
    }
  }

  fun close() {
    interpreter.close()
    executor.shutdown()
  }
}