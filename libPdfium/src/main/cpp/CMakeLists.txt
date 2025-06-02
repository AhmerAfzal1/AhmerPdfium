# Minimum CMake version and project name
cmake_minimum_required(VERSION 3.22.1)
project("AhmerPdfium")

# Import PDFium shared library
add_library(pdfium SHARED IMPORTED)
set_target_properties(pdfium PROPERTIES IMPORTED_LOCATION "${CMAKE_SOURCE_DIR}/libs/${ANDROID_ABI}/libpdfium.so")

# Creates and names a library, sets it as either STATIC or SHARED,
# and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.
add_library(pdfium_jni SHARED mainJNILib.cpp)

# Linker optimizations
target_link_options(pdfium_jni PRIVATE
        "-Wl,-z,max-page-size=16384"
        "-Wl,--no-warn-mismatch"
)

# Set C++ standard and compiler requirements
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Find essential Android NDK libraries
find_package(Threads REQUIRED)
find_library(android-lib android)
find_library(jnigraphics-lib jnigraphics)
find_library(log-lib log)

# Include necessary directories
target_include_directories(pdfium_jni PRIVATE
        ${CMAKE_SOURCE_DIR}/utils       # Utility classes
        ${CMAKE_SOURCE_DIR}/include     # Main include directory
        ${CMAKE_SOURCE_DIR}/include/cpp # C++ specific headers
)

target_link_libraries(pdfium_jni
        pdfium             # Pdfium SDK
        Threads::Threads   # Threading support
        GLESv3             # OpenGL ES 3.0
        ${android-lib}     # Android platform library
        ${jnigraphics-lib} # Android bitmap operations
        ${log-lib}         # Android logging
)

# Set output directory for built libraries
set(CMAKE_LIBRARY_OUTPUT_DIRECTORY "${CMAKE_SOURCE_DIR}/libs/${ANDROID_ABI}")