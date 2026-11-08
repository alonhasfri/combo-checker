cmake_minimum_required(VERSION 3.22)
project(voicebubble C CXX)

set(CMAKE_CXX_STANDARD 17)

# whisper.cpp (open-source Whisper speech recognition, MIT licence), pinned to a release.
# Only downloaded here; it's built twice below.
set(WHISPER_SRC "" CACHE PATH "Local whisper.cpp checkout (optional)")
if (WHISPER_SRC)
    set(WHISPER_DIR ${WHISPER_SRC})
else()
    include(FetchContent)
    FetchContent_Declare(whisper
        GIT_REPOSITORY https://github.com/ggml-org/whisper.cpp.git
        GIT_TAG        v1.7.6
        GIT_SHALLOW    TRUE)
    FetchContent_GetProperties(whisper)
    if (NOT whisper_POPULATED)
        FetchContent_Populate(whisper)
    endif()
    set(WHISPER_DIR ${whisper_SOURCE_DIR})
endif()

include(ExternalProject)

# Two builds of the speech engine, picked at runtime by the app:
#  - base: ARMv8.2 + half-precision maths + dot-product (every phone since ~2018)
#  - fast: the same plus int8 matrix-multiply instructions (i8mm), found in phones from ~2022
#    (Snapdragon 8 Gen 1 and newer, Exynos 2200 and newer...). Much faster maths for Whisper.
foreach(VARIANT base fast)
    if (VARIANT STREQUAL "fast")
        set(ARCH "armv8.2-a+fp16+dotprod+i8mm")
    else()
        set(ARCH "armv8.2-a+fp16+dotprod")
    endif()
    set(INSTALL ${CMAKE_BINARY_DIR}/whisper_${VARIANT}_install)
    set(LIBS
        ${INSTALL}/lib/libwhisper.a
        ${INSTALL}/lib/libggml.a
        ${INSTALL}/lib/libggml-cpu.a
        ${INSTALL}/lib/libggml-base.a)
    ExternalProject_Add(whisper_${VARIANT}
        SOURCE_DIR ${WHISPER_DIR}
        BINARY_DIR ${CMAKE_BINARY_DIR}/whisper_${VARIANT}_build
        INSTALL_DIR ${INSTALL}
        CMAKE_ARGS
            -DCMAKE_TOOLCHAIN_FILE=${CMAKE_TOOLCHAIN_FILE}
            -DANDROID_ABI=${ANDROID_ABI}
            -DANDROID_PLATFORM=${ANDROID_PLATFORM}
            -DANDROID_STL=c++_static
            -DCMAKE_MAKE_PROGRAM=${CMAKE_MAKE_PROGRAM}
            -DCMAKE_BUILD_TYPE=Release
            -DCMAKE_INSTALL_PREFIX=<INSTALL_DIR>
            -DCMAKE_INSTALL_LIBDIR=lib
            -DBUILD_SHARED_LIBS=OFF
            -DWHISPER_BUILD_TESTS=OFF
            -DWHISPER_BUILD_EXAMPLES=OFF
            -DWHISPER_BUILD_SERVER=OFF
            -DGGML_OPENMP=OFF
            -DGGML_NATIVE=OFF
            -DGGML_CPU_ARM_ARCH=${ARCH}
        BUILD_BYPRODUCTS ${LIBS})

    add_library(voicebubble_${VARIANT} SHARED whisper_jni.cpp)
    add_dependencies(voicebubble_${VARIANT} whisper_${VARIANT})
    target_include_directories(voicebubble_${VARIANT} PRIVATE ${WHISPER_DIR}/include ${WHISPER_DIR}/ggml/include)
    target_link_libraries(voicebubble_${VARIANT} PRIVATE ${LIBS} log)
    target_compile_options(voicebubble_${VARIANT} PRIVATE -O3)
endforeach()
