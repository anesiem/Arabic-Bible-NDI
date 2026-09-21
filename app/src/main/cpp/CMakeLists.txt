cmake_minimum_required(VERSION 3.22.1)
project("bible-ndi")

# Set the NDI library path based on ABI
set(NDI_LIB_DIR ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI})

# Add the NDI shared library
add_library(ndi SHARED IMPORTED)
set_target_properties(ndi PROPERTIES IMPORTED_LOCATION ${NDI_LIB_DIR}/libndi.so)

# Include NDI headers
include_directories(${CMAKE_SOURCE_DIR}/include)

# Build our JNI wrapper
add_library(ndi_wrapper SHARED ndi_wrapper.cpp)

# Link log library and NDI library
find_library(log-lib log)
target_link_libraries(ndi_wrapper ${log-lib} ndi)

# Ensure 16 KB page alignment for Android 15+
set_target_properties(ndi_wrapper PROPERTIES LINK_FLAGS "-Wl,-z,max-page-size=16384")
