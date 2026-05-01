# Motor nativo / JNI (llama.cpp, libllama, etc.)
-keep class com.aitunes.app.engine.LlamaNativeBridge { *; }
-keep class com.aitunes.app.engine.LlamaRuntimeConfig { *; }
-keep class com.aitunes.app.engine.LlamaEngine { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
