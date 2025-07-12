# Android RAG Sample - AI-Powered Field Communication App

A comprehensive Android application demonstrating **Retrieval-Augmented Generation (RAG)** with multimodal AI capabilities, featuring speech-to-text, text generation, translation, and image understanding - all running locally on-device.


**.cpp code for integrate whisper.tflite on android are from vilassn/whisper_android! Amazing code!**
https://github.com/vilassn/whisper_android (MIT License)

**TODO: REPLACE THE EMBEDDER TO MULTILINGUAL EMBEDDER**

## 🚀 Key Features

- **🎙️ Speech-to-Text**: Real-time audio transcription using Whisper
- **🤖 AI Chat**: Intelligent responses powered by Gemma 3n language model
- **🌐 Translation**: Multi-language translation with text-to-speech
- **📸 Multimodal AI**: Image understanding with visual question answering
- **🔍 RAG Pipeline**: Context-aware responses using semantic search
- **📱 Fully On-Device**: No internet required, complete privacy

## 📁 Project Overview

This app demonstrates advanced AI capabilities in a practical field communication scenario, perfect for emergency response, international aid, or cross-cultural communication.

### Architecture Components

```
┌─────────────────────────────────────────────────────────────┐
│                    Android RAG Application                   │
├─────────────────────────────────────────────────────────────┤
│  UI Layer (Jetpack Compose)                                │
│  ├── ChatScreen (AI Q&A + Image)                           │
│  └── TranslationScreen (Voice/Text Translation)            │
├─────────────────────────────────────────────────────────────┤
│  Core AI Pipeline (RagPipeline.kt)                         │
│  ├── RAG Engine (Retrieval + Generation)                   │
│  ├── Translation Engine                                     │
│  └── Multimodal Engine                                     │
├─────────────────────────────────────────────────────────────┤
│  Audio Pipeline                                             │
│  ├── AudioRecorder (16kHz capture)                         │
│  ├── WhisperModel (Speech-to-Text)                         │
│  └── WhisperEngineNative (C++ JNI)                         │
├─────────────────────────────────────────────────────────────┤
│  AI Models & Embeddings                                     │
│  ├── Gemma 3n (Text + Vision)                             │
│  ├── Whisper (Speech Recognition)                          │
│  └── Gecko/USE (Text Embeddings)                           │
├─────────────────────────────────────────────────────────────┤
│  Knowledge Base                                             │
│  ├── SQLite Vector Store                                   │
│  ├── Semantic Text Memory                                  │
│  └── Emergency/Field Context                               │
└─────────────────────────────────────────────────────────────┘
```

## 🔧 Core Components Explained

### 1. 🎙️ **Whisper - Speech-to-Text Engine**

**What it is:** OpenAI's Whisper model converted to TensorFlow Lite for on-device speech recognition.

**How it works:**
- Records audio at 16kHz sample rate using `AudioRecorder`
- Converts audio samples to mel-spectrograms in C++
- Processes through TensorFlow Lite Whisper model
- Converts output tokens to readable text using embedded vocabulary

**Key Files:**
- `WhisperModel.kt` - High-level Kotlin interface
- `WhisperEngineNative.kt` - JNI wrapper for C++ implementation
- `app/cpp/` - C++ TensorFlow Lite processing engine

**Model Setup:**
```bash
# Place your Whisper model at:
adb push whisper-small.tflite /data/local/tmp/whisper-small.tflite
```

### 2. 🤖 **Gemma 3n - Multimodal Language Model**

**What it is:** Google's Gemma 3n model with vision capabilities, optimized for mobile deployment.

**How it works:**
- **Text Generation**: Processes user queries with RAG context
- **Image Understanding**: Analyzes images with visual question answering
- **Translation**: Generates translations between multiple languages
- **Multimodal Processing**: Combines text and image inputs simultaneously

**Key Features:**
- **Model**: `gemma-3n-E4B-it-int4.task` (4-bit quantized for mobile)
- **Backend**: MediaPipe LLM Inference with CPU optimization
- **Vision**: Gallery API for image processing
- **Memory Management**: Dynamic backend switching for multimodal operations

**Model Setup:**
```bash
# Place your Gemma model at:
adb push gemma-3n-E4B-it-int4.task /data/local/tmp/gemma-3n-E4B-it-int4.task
```

### 3. 🌐 **Translation System**

**What it is:** AI-powered translation system supporting 7 languages with text-to-speech output.

**Supported Languages:**
- English, Chinese, Arabic, Farsi, Kurdish, Turkish, Urdu

**How it works:**
1. **Input**: Voice (via Whisper) or text typing
2. **Processing**: Gemma 3n generates translations using specialized prompts
3. **Output**: Translated text with automatic text-to-speech playback

**Key Features:**
- **Voice Input**: Press-and-hold microphone for speech input
- **Text Input**: Manual typing with real-time translation
- **Audio Output**: Native Android TTS with language-specific voices
- **Clean Translation**: Bypasses RAG context for accurate translation

### 4. 🎵 **Audio Processing Pipeline**

**What it is:** Complete audio capture and processing system optimized for speech recognition.

**Audio Specifications:**
- **Sample Rate**: 16kHz (optimal for Whisper)
- **Format**: Mono, 16-bit PCM
- **Conversion**: Real-time Short to Float32 conversion
- **Buffer Management**: Efficient circular buffering

**Processing Flow:**
```
🎤 AudioRecord → 📊 PCM Processing → 🔄 Format Conversion → 🎯 Whisper Model → 📝 Text Output
```

**Key Components:**
- `AudioRecorder.kt` - Android audio capture
- `WhisperModel.kt` - Speech-to-text coordinator
- C++ TFLite Engine - Model inference

### 5. 🔍 **RAG (Retrieval-Augmented Generation) Pipeline**

**What it is:** Advanced AI system that combines knowledge retrieval with text generation for contextually-aware responses.

**How RAG Works:**
1. **Knowledge Storage**: Emergency/field context stored in SQLite vector database
2. **Query Processing**: User questions converted to embeddings
3. **Semantic Search**: Finds relevant context using cosine similarity
4. **Response Generation**: Combines retrieved context with user query
5. **AI Response**: Gemma 3n generates contextually-aware answers

**Knowledge Base Content:**
- Emergency first aid procedures
- Cultural guidelines for Iran/Middle East
- Field communication protocols
- Equipment usage instructions
- Safety procedures

**RAG Architecture:**
```
User Query → Embedding → Vector Search → Context Retrieval → LLM + Context → Response
```

### 6. 📊 **Text Embedding Models**

**What they are:** Models that convert text into high-dimensional vectors for semantic search.

**Available Options:**
- **Gecko Embedding Model**: Local TensorFlow Lite model (768 dimensions)
- **Universal Sentence Encoder**: TensorFlow Lite model (512 dimensions)
- **Gemini Embedder**: Cloud-based option (requires API key)

**How they work:**
- Convert text chunks into numerical vectors
- Enable semantic similarity search
- Power the RAG retrieval system
- Support multilingual content

## 🏗️ **Application Architecture**

### UI Layer (Jetpack Compose)
- **ChatScreen**: AI Q&A with image support
- **TranslationScreen**: Voice/text translation interface
- **Material 3 Design**: Modern, accessible UI components

### Core Logic Layer
- **ChatViewModel**: Orchestrates AI operations
- **RagPipeline**: Central AI processing engine
- **Model Management**: Handles multiple AI models efficiently

### Data Layer
- **SQLite Vector Store**: Semantic search database
- **Asset Management**: Local knowledge base
- **Model Loading**: Efficient AI model management

## 🔧 Setup Instructions

### Prerequisites
```bash
# Android SDK 26+ (Android 8.0+)
# NDK for C++ components
# 4GB+ RAM recommended for multimodal operations
```

### Required Model Files
Place these models on your device using ADB:

```bash
# Whisper model for speech-to-text
adb push whisper-small.tflite /data/local/tmp/whisper-small.tflite

# Gemma 3n model for text generation and vision
adb push gemma-3n-E4B-it-int4.task /data/local/tmp/gemma-3n-E4B-it-int4.task

# Optional: Gecko embedding model for local embeddings
adb push gecko-embedding-model.tflite /data/local/tmp/gecko-embedding-model.tflite
```

### Model Sources
- **Whisper**: [TensorFlow Hub](https://tfhub.dev/openai/whisper/1) or [Hugging Face](https://huggingface.co/openai/whisper-small)
- **Gemma**: [Kaggle Models](https://www.kaggle.com/models/google/gemma) or [AI Edge](https://ai.google.dev/edge)
- **Gecko**: [MediaPipe Models](https://developers.google.com/mediapipe/solutions/genai/llm_inference)

### Build and Run
```bash
# Clone the repository
git clone <repository-url>
cd android-rag-sample

# Install dependencies
./gradlew build

# Run on device
./gradlew installDebug
```

## 🎯 Usage Examples

### 1. **AI Chat with Context**
```
User: "What should I do if someone is bleeding severely?"
AI: "If bleeding is severe, elevate the limb and apply direct pressure with a clean cloth. If no clean cloth is available, apply pressure to the wound with the palm of your gloved hand..."
```

### 2. **Visual Question Answering**
```
User: [Shows image of injury] "What type of injury is this?"
AI: "Based on the image, this appears to be a laceration. You should clean the wound, apply direct pressure to control bleeding, and seek medical attention..."
```

### 3. **Voice Translation**
```
User: [Speaks in English] "Hello, how are you?"
AI: [Translates to Arabic] "مرحبا، كيف حالك؟"
[Plays audio pronunciation]
```

### 4. **Emergency Context**
```
User: "How do I purify water?"
AI: "To purify water, add 5 mL of household bleach (5%) to 25 L of clear water, stir, and wait 30 minutes. Alternatively, use solar disinfection (SODIS) which needs six hours of full sunlight in clear PET bottles..."
```

## 🏆 **Technical Highlights**

### Performance Optimizations
- **Memory Management**: Dynamic model loading/unloading
- **Quantization**: 4-bit quantized models for mobile efficiency
- **Parallel Processing**: Concurrent audio/AI operations
- **Resource Pooling**: Efficient tensor buffer management

### Privacy & Security
- **Fully On-Device**: No data leaves the device
- **No Internet Required**: Complete offline operation
- **Local Storage**: Encrypted local databases
- **Permission Management**: Minimal required permissions

### Cross-Platform Compatibility
- **Native Integration**: C++ for performance-critical operations
- **JNI Bridge**: Seamless Kotlin-C++ communication
- **Platform Optimization**: ARM64 and x86_64 support
- **Modern Android**: Targets API 34 with backward compatibility

## 📚 **Knowledge Base Content**

The app includes a comprehensive knowledge base for field operations:

### Emergency Medicine
- First aid procedures
- Injury assessment protocols
- Medical supply usage
- Emergency response procedures

### Cultural Guidelines
- Iranian cultural norms
- Communication etiquette
- Religious considerations
- Social interaction guidelines

### Field Operations
- Equipment usage
- Safety protocols
- Communication procedures
- Emergency signaling

### Technical Procedures
- Water purification methods
- Shelter construction
- Equipment maintenance
- Safety assessments

## 🔬 **Development Notes**

### C++ Components
- **TFLite Engine**: High-performance model inference
- **Audio Processing**: Real-time audio format conversion
- **Memory Management**: Efficient resource handling
- **JNI Interface**: Clean Kotlin-C++ integration

### Kotlin Architecture
- **Coroutines**: Asynchronous operations
- **Compose UI**: Modern reactive UI
- **ViewModel Pattern**: MVVM architecture
- **Dependency Injection**: Clean component separation

### Model Integration
- **MediaPipe**: Google's ML framework
- **TensorFlow Lite**: Mobile-optimized inference
- **Quantization**: 4-bit and 8-bit model compression
- **Dynamic Loading**: Runtime model management


---




