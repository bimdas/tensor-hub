cmake_minimum_required(VERSION 3.22.1)
project("whisper_native")

add_library(whisper_native SHARED
    whisper_jni.cpp
    whisper_decoder.cpp
    whisper_mel.cpp
)

# TFLite C API headers (extracted from AAR, checked into repo)
target_include_directories(whisper_native PRIVATE
    .
    tflite_headers
)

# TFLite native library (extracted from AAR, checked into jniLibs)
# AGP automatically packages jniLibs/ into the APK
# For CMake linking, we reference the .so directly
set(TFLITE_SO "${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libtensorflowlite_jni.so")

if(EXISTS "${TFLITE_SO}")
    message(STATUS "Using pre-built TFLite: ${TFLITE_SO}")
    add_library(tflite_prebuilt SHARED IMPORTED)
    set_target_properties(tflite_prebuilt PROPERTIES
        IMPORTED_LOCATION "${TFLITE_SO}"
    )
    target_link_libraries(whisper_native tflite_prebuilt log android)
else()
    message(FATAL_ERROR "TFLite .so not found at ${TFLITE_SO}. Run the AAR extraction first.")
endif()
