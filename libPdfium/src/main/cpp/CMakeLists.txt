# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.
cmake_minimum_required(VERSION 3.22.1)
project("AhmerPdfium")

# Configure imported PDFium SDK shared library
add_library(pdfium SHARED IMPORTED)
set_target_properties(pdfium PROPERTIES IMPORTED_LOCATION "${CMAKE_SOURCE_DIR}/libs/${ANDROID_ABI}/libpdfium.so")

# Set C++ standard and compiler requirements
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Set output directory for built libraries
set(CMAKE_LIBRARY_OUTPUT_DIRECTORY "${CMAKE_SOURCE_DIR}/libs/${ANDROID_ABI}")

# Creates and names a library, sets it as either STATIC or SHARED,
# and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.
add_library(pdfium_jni SHARED mainJNILib.cpp)

# Searches for a specified prebuilt library and stores the path as a variable.
# Because CMake includes system libraries in the search path by default,
# you only need to specify the name of the public NDK library you want
# to add. CMake verifies that the library exists before completing its build.
# Find essential Android NDK components
find_package(Threads REQUIRED)
find_library(android-lib android)
find_library(jnigraphics-lib jnigraphics)
find_library(log-lib log)

# Set linker options for the JNI library
target_link_options(pdfium_jni PRIVATE
        -Wl,--no-warn-mismatch  # Suppress mismatch warnings
)

# Include necessary directories
target_include_directories(pdfium_jni PRIVATE
        ${CMAKE_SOURCE_DIR}/utils         # Utility classes
        ${CMAKE_SOURCE_DIR}/include       # Main include directory
        ${CMAKE_SOURCE_DIR}/include/cpp   # C++ specific headers
)

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.
# Link dependencies to the JNI library
target_link_libraries(pdfium_jni
        pdfium             # Pdfium SDK
        Threads::Threads   # Threading support
        GLESv3             # OpenGL ES 3.0
        ${android-lib}     # Android platform library
        ${jnigraphics-lib} # Android bitmap operations
        ${log-lib}         # Android logging
)