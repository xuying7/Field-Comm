package com.google.ai.edge.samples.rag

import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * WaveUtil - Utility class for reading WAV files and converting to float samples
 * Based on the proven Java implementation pattern
 */
object WaveUtil {
    
    private const val TAG = "WaveUtil"
    
    /**
     * Get audio samples from WAV file - following proven pattern
     */
    fun getSamples(wavePath: String): FloatArray {
        Log.d(TAG, "Reading WAV file: $wavePath")
        
        return try {
            val file = File(wavePath)
            if (!file.exists()) {
                Log.e(TAG, "❌ WAV file not found: $wavePath")
                return FloatArray(0)
            }
            
            Log.d(TAG, "📁 WAV file size: ${file.length()} bytes")
            
            val inputStream = FileInputStream(file)
            val fileBytes = inputStream.readBytes()
            inputStream.close()
            
            Log.d(TAG, "📖 Read ${fileBytes.size} bytes from file")
            
            // Parse WAV header - simplified version
            val samples = parseWavFile(fileBytes)
            
            Log.d(TAG, "✅ Extracted ${samples.size} audio samples")
            Log.d(TAG, "⏱️ Duration: ${samples.size / 16000.0f} seconds")
            
            if (samples.isNotEmpty()) {
                val min = samples.minOrNull() ?: 0f
                val max = samples.maxOrNull() ?: 0f
                Log.d(TAG, "📊 Sample range: $min to $max")
            }
            
            samples
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error reading WAV file", e)
            FloatArray(0)
        }
    }
    
    /**
     * Parse WAV file bytes and extract float samples
     */
    private fun parseWavFile(bytes: ByteArray): FloatArray {
        Log.d(TAG, "Parsing WAV file...")
        
        if (bytes.size < 44) {
            Log.e(TAG, "❌ File too small to be valid WAV")
            return FloatArray(0)
        }
        
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        
        // Check RIFF header
        val riff = ByteArray(4)
        buffer.get(riff)
        val riffStr = String(riff)
        Log.d(TAG, "RIFF header: $riffStr")
        
        if (riffStr != "RIFF") {
            Log.e(TAG, "❌ Invalid RIFF header: $riffStr")
            return FloatArray(0)
        }
        
        // Skip file size
        buffer.getInt()
        
        // Check WAVE format
        val wave = ByteArray(4)
        buffer.get(wave)
        val waveStr = String(wave)
        Log.d(TAG, "WAVE format: $waveStr")
        
        if (waveStr != "WAVE") {
            Log.e(TAG, "❌ Invalid WAVE format: $waveStr")
            return FloatArray(0)
        }
        
        // Find fmt chunk
        while (buffer.remaining() >= 8) {
            val chunkId = ByteArray(4)
            buffer.get(chunkId)
            val chunkIdStr = String(chunkId)
            val chunkSize = buffer.getInt()
            
            Log.d(TAG, "Chunk: $chunkIdStr, size: $chunkSize")
            
            if (chunkIdStr == "fmt ") {
                // Parse format chunk
                val audioFormat = buffer.getShort().toInt()
                val numChannels = buffer.getShort().toInt()
                val sampleRate = buffer.getInt()
                val byteRate = buffer.getInt()
                val blockAlign = buffer.getShort().toInt()
                val bitsPerSample = buffer.getShort().toInt()
                
                Log.d(TAG, "Audio format: $audioFormat")
                Log.d(TAG, "Channels: $numChannels")
                Log.d(TAG, "Sample rate: $sampleRate")
                Log.d(TAG, "Bits per sample: $bitsPerSample")
                
                // Skip any extra format bytes
                val remainingFmtBytes = chunkSize - 16
                if (remainingFmtBytes > 0) {
                    buffer.position(buffer.position() + remainingFmtBytes)
                }
                
            } else if (chunkIdStr == "data") {
                // Parse data chunk
                Log.d(TAG, "Found data chunk, size: $chunkSize bytes")
                
                val samples = mutableListOf<Float>()
                val numSamples = chunkSize / 2 // Assuming 16-bit samples
                
                Log.d(TAG, "Expected samples: $numSamples")
                
                for (i in 0 until numSamples) {
                    if (buffer.remaining() >= 2) {
                        val sample16 = buffer.getShort()
                        val sampleFloat = sample16.toFloat() / Short.MAX_VALUE.toFloat()
                        samples.add(sampleFloat)
                    } else {
                        break
                    }
                }
                
                Log.d(TAG, "✅ Parsed ${samples.size} samples from data chunk")
                return samples.toFloatArray()
                
            } else {
                // Skip unknown chunk
                buffer.position(buffer.position() + chunkSize)
            }
        }
        
        Log.e(TAG, "❌ No data chunk found in WAV file")
        return FloatArray(0)
    }
} 