cmake_minimum_required(VERSION 3.22.1)
project("whisper_native")

# Enable Prefab packages for TensorFlow Lite
find_package(tensorflowlite REQUIRED CONFIG)

# Add the shared library
add_library(whisper_native SHARED
    whisper_jni.cpp
    whisper_decoder.cpp
    whisper_mel.cpp
)

# Specify include directories (such as the current directory)
target_include_directories(whisper_native PRIVATE
    .
)

# Link native dependencies
target_link_libraries(whisper_native
    tensorflowlite::tensorflowlite
    log
    android
)
