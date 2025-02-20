cmake_minimum_required(VERSION 3.15)
project(MiniCacheServer CXX)

# 设置 C++17
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# 源文件
set(SOURCES
    src/main/cpp/server/main.cpp
)

# 头文件目录
include_directories(src/main/cpp/server)

# 可执行文件
add_executable(mini_cache_server ${SOURCES})

# 如果是 MacOS，需要特别处理
if(APPLE)
    target_compile_definitions(mini_cache_server PRIVATE PLATFORM_MACOS)
endif()