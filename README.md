# AI-Powered Field Communication App

A comprehensive Android application demonstrating **Retrieval-Augmented Generation (RAG)** with multimodal AI capabilities, featuring speech-to-text, text generation, translation, and image understanding - all running locally on-device.

**C++ code for integrating whisper.tflite on Android is from [vilassn/whisper_android](https://github.com/vilassn/whisper_android)! Amazing work!** (MIT License)

## 🚀 Key Features

- **🎙️ Speech-to-Text**: Real-time audio transcription using Whisper
- **🤖 AI Chat**: Intelligent responses powered by Gemma 3n language model with image analysis
- **🌐 Translation**: Multi-language translation with text-to-speech
- **📸 Multimodal AI**: Image understanding with visual question answering
- **🔍 RAG Pipeline**: Context-aware responses using semantic search
- **📱 Fully On-Device**: No internet required, complete privacy
- **🎨 Modern UI**: Material 3 design 

## 📱 User Interface

### **Three-Screen Navigation**

#### **1. Main Screen (Entry Point)**
- **Purpose**: Central hub for mode selection
- **Features**: Two prominent mode buttons with clear descriptions
- **Design**: Clean, minimal interface with centered navigation cards

#### **2. Chat Mode (AI Q&A)**
- **Purpose**: AI-powered chat with multimodal capabilities
- **Empty State**: Interactive instruction card showing how to use voice, image, and text inputs
- **Features**: 
  - Voice input with press-and-hold microphone
  - Image analysis via camera or gallery
  - Text input with rich markdown responses
  - Real-time status indicators

#### **3. Translation Mode**
- **Purpose**: Real-time translation between 7 languages
- **Empty State**: Centered instruction card with usage guidelines
- **Features**:
  - Voice-to-voice translation
  - Text-to-text translation
  - Automatic text-to-speech output
  - Language selector dropdown

### **User Experience Enhancements**

- **Instruction Cards**: Contextual guidance appears when screens are empty
- **Centered Empty States**: Instructions are vertically centered for better UX
- **Visual Feedback**: Real-time indicators for recording, processing, and initialization
- **Smooth Transitions**: Cards appear/disappear naturally as users interact

## 📁 Project Architecture

```
┌────────────────────────────────────────────────────────────┐
│                  Field-Comm Android App                    │
├────────────────────────────────────────────────────────────┤
│  UI Layer (Jetpack Compose + Material 3)                   │
│  ├── MainScreen (Mode Selection)                           │
│  ├── ChatScreen (AI Q&A + Image Analysis)                  │
│  ├── TranslationScreen (Voice/Text Translation)            │
│  └── Instruction Cards (Empty State Guidance)              │
├────────────────────────────────────────────────────────────┤
│  Core AI Pipeline (RagPipeline.kt)                         │
│  ├── RAG Engine (Retrieval + Generation)                   │
│  ├── Translation Engine (Direct Translation)               │
│  ├── Multimodal Engine (Text + Vision)                     │
│  └── Custom Multilingual Embedder                          │
├────────────────────────────────────────────────────────────┤
│  Audio Pipeline (Whisper Integration)                      │
│  ├── AudioRecorder (16kHz capture)                         │
│  ├── WhisperModel (Speech-to-Text)                         │
│  └── WhisperEngineNative (C++ JNI)                         │
├──────────────────────────────────────────────-─────────────┤
│  AI Models & Storage                                       │
│  ├── Gemma 3n (Text + Vision)                              │
│  ├── Whisper (Speech Recognition)                          │
│  ├── Custom Multilingual Embedder (512D)                   │
│  └── SQLite Vector Store                                   │
└────────────────────────────────────────────────────────────┘
```

## 🔧 Setup Instructions

### **Prerequisites**
```bash
# Android SDK 26+ (Android 8.0+)
# NDK for C++ components
# 8GB+ RAM recommended for multimodal operations
# ADB for model installation
```

### **Required Model Files**

**⚠️ IMPORTANT**: Models must be placed in `/data/local/tmp/` directory on your Android device using ADB.

**Required Files Summary:**
- `whisper-small.tflite` (~242 MB) - Speech-to-text model
- `gemma-3n-E4B-it-int4.task` (~4.4 GB) - Multimodal language model 
- `vocab.txt` (~996 kB) - Multilingual embedder vocabulary
- `model_int8.tflite` (~134 MB) - Multilingual embedder model

```bash
# 1. Whisper model for speech-to-text
adb push whisper-small.tflite /data/local/tmp/whisper-small.tflite

# 2. Gemma 3n model for text generation and vision
adb push gemma-3n-E4B-it-int4.task /data/local/tmp/gemma-3n-E4B-it-int4.task

# 3. Custom multilingual embedder files (for RAG semantic search)
adb push vocab.txt /data/local/tmp/vocab.txt
adb push model_int8.tflite /data/local/tmp/model_int8.tflite
```
### APK Download Link:
https://drive.google.com/uc?export=download&id=1QdaS14KkO_Z6uYKQ8Bp9ZwIXBzafmcFR

### **Model Download Sources**

#### **1. Whisper Model (Speech-to-Text)**
- **Recommended**: [Hugging Face - Whisper Small](https://huggingface.co/openai/whisper-small)
- **Alternative**: [TensorFlow Hub - Whisper](https://tfhub.dev/openai/whisper/1)
- **File needed**: `whisper-small.tflite` (convert from PyTorch to TFLite), or download directly from https://huggingface.co/DocWolle/whisper_tflite_models 

#### **2. Gemma 3n Model (Multimodal LLM)**
- **Primary**: [Kaggle - Gemma Models](https://www.kaggle.com/models/google/gemma-3n/tfLite)
- **Alternative**: [AI Edge - Gemma](https://ai.google.dev/edge/models/gemma)
- **File needed**: `gemma-3n-E4B-it-int4.task` (4-bit quantized for mobile)

#### **3. Custom Multilingual Embedder (RAG Semantic Search)**
- **Source**: [Hugging Face - DistilUSE Base Multilingual Cased v2](https://huggingface.co/sentence-transformers/distiluse-base-multilingual-cased-v2/tree/main)
- **Files needed**: 
  - `vocab.txt` (996 kB) - DistilBERT vocabulary file
  - `model_int8.tflite` (convert from `tf_model.h5/model.onnx` to quantized TFLite)
- **Note**: The TensorFlow model needs to be converted to TFLite format and quantized to int8 for mobile deployment (Or download directly from https://huggingface.co/xuying7/distiluse-base-multilingual-cased-v2-tflite-version/tree/main)

### **Installation Steps**

```bash
# 1. Clone the repository
git clone <repository-url>
cd android-rag-sample

# 2. Download required models from huggingface. 

# 3. Push models to device using ADB commands above

# 4. Sync, Build and install in Android Studio. 
```

## 🎯 Usage Guide

### **Getting Started**

1. **Launch App**: Open Field-Comm app
2. **Choose Mode**: Select "Translation Mode" or "Chatbot Mode"
3. **Follow Instructions**: Each mode shows helpful guidance when empty
4. **Start Interacting**: Use voice, text, or image inputs as indicated

### **Chat Mode Examples**

#### **Voice Query**
```
User: [Press and hold microphone] "What should I do for a severe cut?"
AI: "For a severe cut, follow these steps:
1. Apply direct pressure with a clean cloth
2. Elevate the injured area above heart level
3. If bleeding doesn't stop, apply additional pressure
4. Seek immediate medical attention..."
```

#### **Image Analysis**
```
User: [Takes photo of medical equipment] "What is this used for?"
AI: "This appears to be a tourniquet. It's used to control severe bleeding by stopping blood flow to an injured limb. To use it:
1. Place 2-3 inches above the wound
2. Tighten until bleeding stops
3. Note the time applied
4. Seek medical help immediately..."
```

### **Translation Mode Examples**

#### **Voice Translation**
```
User: [Press and hold microphone] "Hello, how are you?"
AI: [Translates to Arabic] "مرحبا، كيف حالك؟"
[Automatically plays audio pronunciation]
```

#### **Text Translation**
```
User: [Tap keyboard icon, type] "I need medical help"
AI: [Translates to Farsi] "من به کمک پزشکی نیاز دارم"
[Tap speaker icon to hear pronunciation]
```

## 🌐 Supported Languages

The translation system supports **7 languages** with native text-to-speech:

- **English** (en-US)
- **Chinese** (zh-CN) - Mandarin Simplified
- **Arabic** (ar-SA) - Modern Standard Arabic
- **Farsi** (fa-IR) - Persian
- **Kurdish** (ku) - Kurmanji
- **Turkish** (tr-TR)
- **Urdu** (ur-PK)

## 🔍 RAG Knowledge Base

The app includes a comprehensive emergency/field operations knowledge base:

### **Emergency Medicine**
- First aid procedures and protocols
- Injury assessment and treatment
- Medical supply usage instructions
- Emergency response procedures

### **Cultural Guidelines**
- Iranian cultural norms and etiquette
- Religious considerations
- Communication best practices
- Social interaction guidelines

### **Field Operations**
- Equipment usage and maintenance
- Safety protocols and procedures
- Communication systems
- Emergency signaling methods

### **Technical Procedures**
- Water purification techniques
- Shelter construction methods
- Equipment troubleshooting
- Safety assessments

## 🏆 Technical Highlights

### **Performance Optimizations**
- **Memory Management**: Dynamic model loading/unloading for multimodal operations
- **Quantization**: 4-bit quantized models for mobile efficiency
- **Custom Multilingual Embedder**: DistilUSE Base Multilingual Cased v2 (512D) for semantic search
- **Parallel Processing**: Concurrent audio/AI operations

### **Privacy & Security**
- **Fully On-Device**: No data transmission to external servers
- **No Internet Required**: Complete offline operation
- **Local Storage**: Encrypted SQLite vector database
- **Minimal Permissions**: Only camera and microphone access

### **Modern Architecture**
- **Jetpack Compose**: Modern reactive UI with Material 3 design
- **Kotlin Coroutines**: Asynchronous operations with proper error handling
- **MVVM Pattern**: Clean separation of concerns
- **JNI Integration**: High-performance C++ components

## 🔬 Development Notes

### **C++ Components**
- **TFLite Engine**: High-performance model inference
- **Audio Processing**: Real-time format conversion (16kHz PCM)
- **Memory Management**: Efficient resource handling
- **JNI Interface**: Clean Kotlin-C++ bridge

### **Kotlin Architecture**
- **ChatViewModel**: Orchestrates AI operations and state management
- **RagPipeline**: Central AI processing with multimodal support
- **Custom Embedder**: Multilingual text embedding for RAG
- **Audio Integration**: Whisper model wrapper with native processing

### **Model Integration**
- **MediaPipe**: Google's ML framework for LLM inference
- **TensorFlow Lite**: Mobile-optimized inference engine
- **Dynamic Loading**: Runtime model management with memory optimization
- **Multimodal Processing**: Text + image fusion for Gemma 3n

## 📚 File Structure

```
android/
├── app/
│   ├── src/main/
│   │   ├── java/com/google/ai/edge/samples/rag/
│   │   │   ├── MainActivity.kt          # App entry point
│   │   │   ├── MainScreen.kt           # Mode selection screen
│   │   │   ├── ChatScreen.kt           # AI chat interface
│   │   │   ├── TranslationScreen.kt    # Translation interface
│   │   │   ├── ChatViewModel.kt        # Chat state management
│   │   │   ├── RagPipeline.kt          # Core AI pipeline
│   │   │   ├── WhisperModel.kt         # Speech-to-text wrapper
│   │   │   └── AudioRecorder.kt        # Audio capture
│   │   ├── cpp/                        # C++ native components
│   │   ├── assets/                     # Knowledge base content
│   │   └── res/                        # UI resources
│   └── build.gradle.kts               # Dependencies
├── README.md                          # This file
└── gradlew                           # Gradle wrapper
```

---

**Note**: This is a demonstration app showcasing on-device AI capabilities. Ensure you have the proper model files installed before running the application.




