cmake_minimum_required(VERSION 3.22)
project(voicebubble C CXX)

set(CMAKE_CXX_STANDARD 17)

# whisper.cpp (open-source Whisper speech recognition, MIT licence), pinned to a release.
set(WHISPER_SRC "" CACHE PATH "Local whisper.cpp checkout (optional)")
include(FetchContent)
if (WHISPER_SRC)
    FetchContent_Declare(whisper SOURCE_DIR ${WHISPER_SRC})
else()
    FetchContent_Declare(whisper
        GIT_REPOSITORY https://github.com/ggml-org/whisper.cpp.git
        GIT_TAG        v1.7.6
        GIT_SHALLOW    TRUE)
endif()

set(BUILD_SHARED_LIBS      OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP            OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE            OFF CACHE BOOL "" FORCE)
# ARMv8.2 with half-precision maths and dot-product instructions: every phone CPU since ~2018
# (Cortex-A55/A75 and newer). Roughly doubles the speed over plain ARMv8.
set(GGML_CPU_ARM_ARCH "armv8.2-a+fp16+dotprod" CACHE STRING "" FORCE)
FetchContent_MakeAvailable(whisper)

add_library(voicebubble SHARED whisper_jni.cpp)
target_link_libraries(voicebubble PRIVATE whisper log)
target_compile_options(voicebubble PRIVATE -O3)
