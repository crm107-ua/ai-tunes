# Puente JNI + llama.cpp (fuentes en ./llama). La salida se llama libllama.so (loadLibrary "llama").
cmake_minimum_required(VERSION 3.22)
project(aitunes_llama_jni CXX C)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

if (NOT DEFINED LLAMA_CPP_DIR)
    set(LLAMA_CPP_DIR "${CMAKE_CURRENT_SOURCE_DIR}/llama")
endif()

if (NOT EXISTS "${LLAMA_CPP_DIR}/CMakeLists.txt")
    message(FATAL_ERROR "Falta llama.cpp en ${LLAMA_CPP_DIR}. Clona: git clone --depth 1 https://github.com/ggml-org/llama.cpp.git ${LLAMA_CPP_DIR}")
endif()

# Opción pedida por el equipo (si ggml la ignora en tu toolchain, no rompe la config).
option(GGML_NEON "Habilitar definiciones NEON en ARM" ON)

# Estática dentro del .so JNI (evita colisión de nombre con el target upstream "llama").
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_WEBUI OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)

if (ANDROID)
    set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
    # Evita sgemm llamafile en armv7 sin fp16 SIMD completo (fallo vld1q_f16 en NDK).
    set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
endif()

if (GGML_NEON AND ANDROID)
    if (ANDROID_ABI STREQUAL "armeabi-v7a" OR ANDROID_ABI STREQUAL "arm64-v8a")
        add_compile_definitions(GGML_NEON=1)
    endif()
endif()

if (NOT MSVC AND NOT CMAKE_CXX_FLAGS MATCHES "-O3")
    add_compile_options($<$<COMPILE_LANGUAGE:C,CXX>:-O3>)
endif()

add_subdirectory("${LLAMA_CPP_DIR}" "${CMAKE_CURRENT_BINARY_DIR}/llama_build")

add_library(aitunes_llama_jni SHARED native-lib.cpp)
set_target_properties(aitunes_llama_jni PROPERTIES OUTPUT_NAME llama)

target_include_directories(aitunes_llama_jni PRIVATE "${LLAMA_CPP_DIR}/include")

find_library(android_log log)

target_link_libraries(aitunes_llama_jni
    PRIVATE
    llama
    ${android_log}
)

# Alineación LOAD 16 KB en ELF para arm64 / x86_64 (requisito páginas 16 KB en API recientes).
if (ANDROID)
    if (ANDROID_ABI STREQUAL "arm64-v8a" OR ANDROID_ABI STREQUAL "x86_64")
        target_link_options(aitunes_llama_jni PRIVATE "-Wl,-z,max-page-size=16384")
    endif()
endif()
