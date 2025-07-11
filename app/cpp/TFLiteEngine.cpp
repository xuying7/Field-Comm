#include <iostream>
#include <fstream>
#include <cstring>
#include <vector>
#include <sys/time.h>
#include "tensorflow/lite/core/interpreter.h"
#include "tensorflow/lite/kernels/register.h"
#include "tensorflow/lite/model.h"
#include "tensorflow/lite/optional_debug_tools.h"
#include "tensorflow/lite/delegates/gpu/delegate.h"

#include "TFLiteEngine.h"
#include "input_features.h"
#include "filters_vocab_multilingual.h"
#include "filters_vocab_en.h"
#include "whisper.h"
#include "wav_util.h"

#define INFERENCE_ON_AUDIO_FILE 1
#define TIME_DIFF_MS(start, end) (((end.tv_sec - start.tv_sec) * 1000000) + (end.tv_usec - start.tv_usec))/1000
#define TFLITE_MINIMAL_CHECK(x)                              \
  if (!(x)) {                                                \
    fprintf(stderr, "Error at %s:%d", __FILE__, __LINE__); \
    exit(1);                                                 \
  }

int TFLiteEngine:: loadModel(const char *modelPath, const bool isMultilingual) {
    std::cout << "Entering " << __func__ << "()" << std::endl;

    timeval start_time{}, end_time{};
    if (!g_whisper_tflite.is_whisper_tflite_initialized) {

        gettimeofday(&start_time, NULL);
        std::cout << "Initializing TFLite..." << std::endl;

        /////////////// Load filters and vocab data ///////////////

       const char* vocabData = nullptr;
        if (isMultilingual)
            vocabData = reinterpret_cast<const char*>(filters_vocab_multilingual);
        else
            vocabData = reinterpret_cast<const char*>(filters_vocab_en);

        // Read the magic number
        int magic = 0;
        std::memcpy(&magic, vocabData, sizeof(magic));
        vocabData += sizeof(magic);

        // Check the magic number
        if (magic != 0x57535052) { // 'WSPR'
            std::cerr << "Invalid vocab data (bad magic)" << std::endl;
            return -1;
        }

        // Load mel filters
        std::memcpy(&filters.n_mel, vocabData, sizeof(filters.n_mel));
        vocabData += sizeof(filters.n_mel);

        std::memcpy(&filters.n_fft, vocabData, sizeof(filters.n_fft));
        vocabData += sizeof(filters.n_fft);

        std::cout << "n_mel:" << filters.n_mel << " n_fft:" << filters.n_fft << std::endl;

        filters.data.resize(filters.n_mel * filters.n_fft);
        std::memcpy(filters.data.data(), vocabData, filters.data.size() * sizeof(float));
        vocabData += filters.data.size() * sizeof(float);

        // Load vocab
        int n_vocab = 0;
        std::memcpy(&n_vocab, vocabData, sizeof(n_vocab));
        vocabData += sizeof(n_vocab);

        std::cout << "n_vocab:" << n_vocab << std::endl;

        for (int i = 0; i < n_vocab; i++) {
            int len = 0;
            std::memcpy(&len, vocabData, sizeof(len));
            vocabData += sizeof(len);

            std::string word(vocabData, len);
            vocabData += len;

            g_vocab.id_to_token[i] = word;
        }

        // add additional vocab ids
        int n_vocab_additional = 51864;
        if (isMultilingual) {
            n_vocab_additional = 51865;
            g_vocab.token_eot++;
            g_vocab.token_sot++;
            g_vocab.token_prev++;
            g_vocab.token_solm++;
            g_vocab.token_not++;
            g_vocab.token_beg++;
        }

        for (int i = n_vocab; i < n_vocab_additional; i++) {
            std::string word;
            if (i > g_vocab.token_beg) {
                word = "[_TT_" + std::to_string(i - g_vocab.token_beg) + "]";
            } else if (i == g_vocab.token_eot) {
                word = "[_EOT_]";
            } else if (i == g_vocab.token_sot) {
                word = "[_SOT_]";
            } else if (i == g_vocab.token_prev) {
                word = "[_PREV_]";
            } else if (i == g_vocab.token_not) {
                word = "[_NOT_]";
            } else if (i == g_vocab.token_beg) {
                word = "[_BEG_]";
            } else {
                word = "[_extra_token_" + std::to_string(i) + "]";
            }
            g_vocab.id_to_token[i] = word;
            // printf("%s: g_vocab[%d] = '%s'", __func__, i, word.c_str());
        }


        /////////////// Load tflite model buffer ///////////////

        // Open the TFLite model file for reading
        std::ifstream modelFile(modelPath, std::ios::binary | std::ios::ate);
        if (!modelFile.is_open()) {
            std::cerr << "Unable to open model file: " << modelPath << std::endl;
            return -1;
        }

        // Get the size of the model file
        std::streamsize size = modelFile.tellg();
        modelFile.seekg(0, std::ios::beg);

        // Allocate memory for the model buffer
        char *buffer = new char[size];

        // Read the model data into the buffer
        if (modelFile.read(buffer, size)) {
            modelFile.close();
        } else {
            std::cerr << "Error reading model data from file." << std::endl;
        }

        g_whisper_tflite.size = size;
        g_whisper_tflite.buffer = buffer;

        g_whisper_tflite.model = tflite::FlatBufferModel::BuildFromBuffer(g_whisper_tflite.buffer, g_whisper_tflite.size);
        TFLITE_MINIMAL_CHECK(g_whisper_tflite.model != nullptr);

        // Build the interpreter with the InterpreterBuilder.
        tflite::InterpreterBuilder builder(*(g_whisper_tflite.model), g_whisper_tflite.resolver);

        builder(&(g_whisper_tflite.interpreter));
        TFLITE_MINIMAL_CHECK(g_whisper_tflite.interpreter != nullptr);

        // Allocate tensor buffers.
        TFLITE_MINIMAL_CHECK(g_whisper_tflite.interpreter->AllocateTensors() == kTfLiteOk);

        g_whisper_tflite.input = g_whisper_tflite.interpreter->typed_input_tensor<float>(0);
        g_whisper_tflite.is_whisper_tflite_initialized = true;

        gettimeofday(&end_time, NULL);
        std::cout << "Time taken for TFLite initialization: " << TIME_DIFF_MS(start_time, end_time) << " ms" << std::endl;
    }

    std::cout << "Exiting " << __func__ << "()" << std::endl;
    return 0;
}

std::string TFLiteEngine::transcribeBuffer(std::vector<float> samples) {
    timeval start_time{}, end_time{};
    gettimeofday(&start_time, NULL);

    // === DETAILED AUDIO INPUT LOGGING ===
    std::cout << "=== AUDIO INPUT ANALYSIS ===" << std::endl;
    std::cout << "Raw input samples count: " << samples.size() << std::endl;
    
    if (samples.size() > 0) {
        float min_val = *std::min_element(samples.begin(), samples.end());
        float max_val = *std::max_element(samples.begin(), samples.end());
        float sum = 0.0f;
        for (float sample : samples) {
            sum += sample;
        }
        float avg_val = sum / samples.size();
        
        std::cout << "Audio stats - Min: " << min_val << ", Max: " << max_val << ", Avg: " << avg_val << std::endl;
        
        // Show first 10 and last 10 samples
        std::cout << "First 10 samples: ";
        for (int i = 0; i < std::min(10, (int)samples.size()); i++) {
            std::cout << samples[i] << " ";
        }
        std::cout << std::endl;
        
        std::cout << "Last 10 samples: ";
        for (int i = std::max(0, (int)samples.size() - 10); i < (int)samples.size(); i++) {
            std::cout << samples[i] << " ";
        }
        std::cout << std::endl;
        
        // Check for silence
        int silent_samples = 0;
        for (float sample : samples) {
            if (std::abs(sample) < 0.001f) silent_samples++;
        }
        float silence_ratio = (float)silent_samples / samples.size();
        std::cout << "Silence ratio: " << silence_ratio << " (" << silent_samples << "/" << samples.size() << ")" << std::endl;
    }

    // === AUDIO PREPROCESSING LOGGING ===
    std::cout << "=== AUDIO PREPROCESSING ===" << std::endl;
    std::cout << "Expected chunk size: " << (WHISPER_SAMPLE_RATE * WHISPER_CHUNK_SIZE) << " samples" << std::endl;
    std::cout << "Before resize: " << samples.size() << " samples" << std::endl;
    
    // Hack if the audio file size is less than 30ms append with 0's
    samples.resize((WHISPER_SAMPLE_RATE * WHISPER_CHUNK_SIZE), 0);
    std::cout << "After resize: " << samples.size() << " samples" << std::endl;
    
    const auto processor_count = std::thread::hardware_concurrency();
    std::cout << "Using " << processor_count << " CPU cores for processing" << std::endl;

    // === MEL-SPECTROGRAM COMPUTATION LOGGING ===
    std::cout << "=== MEL-SPECTROGRAM COMPUTATION ===" << std::endl;
    std::cout << "Input parameters:" << std::endl;
    std::cout << "  - Sample rate: " << WHISPER_SAMPLE_RATE << std::endl;
    std::cout << "  - N_FFT: " << WHISPER_N_FFT << std::endl;
    std::cout << "  - Hop length: " << WHISPER_HOP_LENGTH << std::endl;
    std::cout << "  - N_MEL: " << WHISPER_N_MEL << std::endl;
    std::cout << "  - Filters n_mel: " << filters.n_mel << std::endl;
    std::cout << "  - Filters n_fft: " << filters.n_fft << std::endl;
    std::cout << "  - Filters data size: " << filters.data.size() << std::endl;

    if (!log_mel_spectrogram(samples.data(), samples.size(), WHISPER_SAMPLE_RATE, WHISPER_N_FFT,
                             WHISPER_HOP_LENGTH, WHISPER_N_MEL, processor_count, filters, mel)) {
        std::cerr << "❌ FAILED to compute mel spectrogram" << std::endl;
        return "";
    }

    std::cout << "✅ Mel-spectrogram computed successfully" << std::endl;
    std::cout << "Mel spectrogram dimensions: " << mel.n_mel << " x " << mel.n_len << std::endl;
    std::cout << "Expected dimensions: " << WHISPER_N_MEL << " x " << WHISPER_MEL_LEN << std::endl;
    std::cout << "Mel data size: " << mel.data.size() << std::endl;
    
    // Show mel spectrogram statistics
    if (mel.data.size() > 0) {
        float mel_min = *std::min_element(mel.data.begin(), mel.data.end());
        float mel_max = *std::max_element(mel.data.begin(), mel.data.end());
        float mel_sum = 0.0f;
        for (float val : mel.data) {
            mel_sum += val;
        }
        float mel_avg = mel_sum / mel.data.size();
        std::cout << "Mel stats - Min: " << mel_min << ", Max: " << mel_max << ", Avg: " << mel_avg << std::endl;
        
        // Show first few mel values
        std::cout << "First 10 mel values: ";
        for (int i = 0; i < std::min(10, (int)mel.data.size()); i++) {
            std::cout << mel.data[i] << " ";
        }
        std::cout << std::endl;
    }

    gettimeofday(&end_time, NULL);
    std::cout << "Time taken for Spectrogram: " << TIME_DIFF_MS(start_time, end_time) << " ms" << std::endl;

    // === MODEL INPUT PREPARATION ===
    std::cout << "=== MODEL INPUT PREPARATION ===" << std::endl;
    
    if (INFERENCE_ON_AUDIO_FILE) {
        std::cout << "Using computed mel-spectrogram as model input" << std::endl;
        std::cout << "Copying " << (mel.n_mel * mel.n_len) << " float values to model input" << std::endl;
        memcpy(g_whisper_tflite.input, mel.data.data(), mel.n_mel * mel.n_len * sizeof(float));
    } else {
        std::cout << "Using pre-generated input features" << std::endl;
        std::cout << "Copying " << (WHISPER_N_MEL * WHISPER_MEL_LEN) << " float values to model input" << std::endl;
        memcpy(g_whisper_tflite.input, _content_input_features_bin, WHISPER_N_MEL * WHISPER_MEL_LEN * sizeof(float));
    }

    // === TENSORFLOW LITE INFERENCE ===
    std::cout << "=== TENSORFLOW LITE INFERENCE ===" << std::endl;
    std::cout << "Model interpreter status: " << (g_whisper_tflite.interpreter ? "✅ Valid" : "❌ NULL") << std::endl;
    std::cout << "Setting " << processor_count << " threads for inference" << std::endl;
    
    gettimeofday(&start_time, NULL);

    // Run inference
    g_whisper_tflite.interpreter->SetNumThreads(processor_count);
    auto invoke_status = g_whisper_tflite.interpreter->Invoke();
    
    if (invoke_status != kTfLiteOk) {
        std::cerr << "❌ INFERENCE FAILED with status: " << invoke_status << std::endl;
        return "";
    }
    
    std::cout << "✅ Inference completed successfully" << std::endl;

    gettimeofday(&end_time, NULL);
    std::cout << "Time taken for Interpreter: " << TIME_DIFF_MS(start_time, end_time) << " ms" << std::endl;

    // === OUTPUT TENSOR ANALYSIS ===
    std::cout << "=== OUTPUT TENSOR ANALYSIS ===" << std::endl;
    
    int output = g_whisper_tflite.interpreter->outputs()[0];
    TfLiteTensor *output_tensor = g_whisper_tflite.interpreter->tensor(output);
    TfLiteIntArray *output_dims = output_tensor->dims;
    
    std::cout << "Output tensor info:" << std::endl;
    std::cout << "  - Tensor index: " << output << std::endl;
    std::cout << "  - Tensor type: " << TfLiteTypeGetName(output_tensor->type) << std::endl;
    std::cout << "  - Dimensions count: " << output_dims->size << std::endl;
    
    for (int i = 0; i < output_dims->size; i++) {
        std::cout << "  - Dim[" << i << "]: " << output_dims->data[i] << std::endl;
    }
    
    // assume output dims to be something like (1, 1, ... ,size)
    auto output_size = output_dims->data[output_dims->size - 1];
    std::cout << "Final output size: " << output_size << std::endl;

    int *output_int = g_whisper_tflite.interpreter->typed_output_tensor<int>(0);
    std::cout << "Output pointer: " << (void*)output_int << std::endl;

    // === DETAILED TOKEN ANALYSIS ===
    std::cout << "=== DETAILED TOKEN ANALYSIS ===" << std::endl;
    std::cout << "Vocabulary info:" << std::endl;
    std::cout << "  - Total vocab entries: " << g_vocab.id_to_token.size() << std::endl;
    std::cout << "  - EOT token: " << g_vocab.token_eot << std::endl;
    std::cout << "  - SOT token: " << g_vocab.token_sot << std::endl;
    std::cout << "  - Token_beg: " << g_vocab.token_beg << std::endl;
    
    std::string text = "";
    std::cout << "Processing " << output_size << " output tokens:" << std::endl;

    for (int i = 0; i < output_size; i++) {
        int token_id = output_int[i];
        std::cout << "Token[" << i << "]: ID=" << token_id;
        
        if (token_id == g_vocab.token_eot) {
            std::cout << " (END_OF_TEXT - stopping)" << std::endl;
            break;
        }

        if (token_id < g_vocab.token_eot) {
            // Look up token in vocabulary
            auto token_iter = g_vocab.id_to_token.find(token_id);
            if (token_iter != g_vocab.id_to_token.end()) {
                std::string token_str = token_iter->second;
                std::cout << " -> '" << token_str << "' (length=" << token_str.length() << ")";
                
                // Add detailed character analysis
                if (!token_str.empty()) {
                    std::cout << " [chars: ";
                    for (char c : token_str) {
                        if (c == ' ') {
                            std::cout << "SPACE ";
                        } else if (c >= 32 && c <= 126) {
                            std::cout << "'" << c << "' ";
                        } else {
                            std::cout << "(" << (int)c << ") ";
                        }
                    }
                    std::cout << "]";
                }
                
                text += token_str;
                std::cout << " -> Current text: '" << text << "'";
            } else {
                std::cout << " (NOT_FOUND_IN_VOCAB)";
            }
        } else {
            std::cout << " (SPECIAL_TOKEN >= EOT, skipping)";
        }
        std::cout << std::endl;
    }

    // === FINAL RESULT ANALYSIS ===
    std::cout << "=== FINAL RESULT ANALYSIS ===" << std::endl;
    std::cout << "Final text length: " << text.length() << std::endl;
    std::cout << "Final text: '" << text << "'" << std::endl;
    
    if (!text.empty()) {
        std::cout << "Character breakdown: ";
        for (size_t i = 0; i < text.length(); i++) {
            char c = text[i];
            if (c == ' ') {
                std::cout << "[SPACE]";
            } else if (c >= 32 && c <= 126) {
                std::cout << c;
            } else {
                std::cout << "[" << (int)c << "]";
            }
        }
        std::cout << std::endl;
    }

    return text;
}

std::string TFLiteEngine::transcribeFile(const char *waveFile) {
    std::vector<float> pcmf32 = readWAVFile(waveFile);
    size_t originalSize = pcmf32.size();

    // Determine the number of chunks required to process the entire file
    size_t totalChunks = (originalSize + (WHISPER_SAMPLE_RATE * WHISPER_CHUNK_SIZE) - 1) /
                         (WHISPER_SAMPLE_RATE * WHISPER_CHUNK_SIZE);

    std::string text;
    for (size_t chunkIndex = 0; chunkIndex < totalChunks; ++chunkIndex) {
        // Extract a chunk of audio data
        size_t startSample = chunkIndex * WHISPER_SAMPLE_RATE * WHISPER_CHUNK_SIZE;
        size_t endSample = std::min(startSample + (WHISPER_SAMPLE_RATE * WHISPER_CHUNK_SIZE),
                                    originalSize);
        std::vector<float> chunk(pcmf32.begin() + startSample, pcmf32.begin() + endSample);

        // Pad the chunk if it's smaller than the expected size
        if (chunk.size() < WHISPER_SAMPLE_RATE * WHISPER_CHUNK_SIZE) {
            chunk.resize(WHISPER_SAMPLE_RATE * WHISPER_CHUNK_SIZE, 0);
        }

        // Transcribe the chunk and append the result to the text
        std::string chunkText = transcribeBuffer(chunk);
        text += chunkText;
    }
    return text;
}

void TFLiteEngine::freeModel() {
    std::cout << "Entering " << __func__ << "()" << std::endl;

    if (g_whisper_tflite.interpreter)
        g_whisper_tflite.interpreter.reset();  // Reset interpreter to release resources

    if (g_whisper_tflite.model)
        g_whisper_tflite.model.reset();        // Reset model to free memory

    if (g_whisper_tflite.buffer) {
        std::cout << __func__ << ": free buffer " << g_whisper_tflite.buffer << " memory" << std::endl;
        delete[] g_whisper_tflite.buffer;
    }

    // Set the flag to false to avoid issues in the re-initialization of the model
    if (g_whisper_tflite.is_whisper_tflite_initialized) {
        g_whisper_tflite.is_whisper_tflite_initialized = false;
    }

    // Reset the whisper_vocab structure to clear the vocab data
    g_vocab.reset();

    std::cout << "Exiting " << __func__ << "()" << std::endl;
}
