cmake_minimum_required(VERSION 3.15)
project(MiniCacheServer CXX)

# 设置 C++17
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

include(FetchContent)

# 头文件目录
include_directories(src/main/cpp/server)
include_directories(src/main/cpp/client)

# 设置库输出目录
if(MSVC)
    # 确保所有库文件（.lib 和 .dll）都输出到同一个目录
    set(CMAKE_RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/bin)
    set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/bin)
    set(CMAKE_ARCHIVE_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/bin)
    
    # 为不同的构建类型设置相同的输出目录
    foreach(OUTPUTCONFIG ${CMAKE_CONFIGURATION_TYPES})
        string(TOUPPER ${OUTPUTCONFIG} OUTPUTCONFIG)
        set(CMAKE_RUNTIME_OUTPUT_DIRECTORY_${OUTPUTCONFIG} ${CMAKE_BINARY_DIR}/bin)
        set(CMAKE_LIBRARY_OUTPUT_DIRECTORY_${OUTPUTCONFIG} ${CMAKE_BINARY_DIR}/bin)
        set(CMAKE_ARCHIVE_OUTPUT_DIRECTORY_${OUTPUTCONFIG} ${CMAKE_BINARY_DIR}/bin)
    endforeach()
else()
    set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/lib)
    set(CMAKE_RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/bin)
    set(CMAKE_ARCHIVE_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/lib)
endif()
set(CMAKE_CONF_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/conf)

# 创建 ConfigParser 库
add_library(config_parser SHARED src/main/cpp/server/ConfigParser.cpp)

# 创建 RespParser 库
add_library(resp_parser SHARED src/main/cpp/server/RespParser.cpp)

# 创建 DataStore 库
add_library(data_store SHARED src/main/cpp/server/DataStore.cpp)

# 先尝试查找本地安装的 nlohmann_json
find_package(nlohmann_json QUIET)

# 如果没有找到本地安装的 nlohmann_json，则使用 FetchContent 下载
if(NOT nlohmann_json_FOUND)
    message(STATUS "Local nlohmann_json not found, fetching from GitHub...")
    FetchContent_Declare(
        nlohmann_json
        GIT_REPOSITORY https://github.com/nlohmann/json.git
        GIT_TAG v3.11.2  # 指定版本号
    )
    FetchContent_MakeAvailable(nlohmann_json)
else()
    message(STATUS "Found local nlohmann_json: ${nlohmann_json_VERSION}")
endif()

# 创建 CommandHandler 库
add_library(command_handler SHARED
    src/main/cpp/server/CommandHandler.cpp
)
# 修改链接方式，使用目标名称而不是文件路径
target_link_libraries(command_handler PRIVATE data_store)
target_link_libraries(data_store PRIVATE nlohmann_json::nlohmann_json)

# 确保 data_store 库在 command_handler 之前构建
add_dependencies(command_handler data_store)
# 创建 TcpServer 库
add_library(tcp_server SHARED src/main/cpp/server/TcpServer.cpp)

# 为 Windows 平台添加特定的编译定义
if(WIN32)
    target_compile_definitions(tcp_server PRIVATE 
        PLATFORM_WINDOWS 
        _WIN32 
        _WINSOCK_DEPRECATED_NO_WARNINGS
        WIN32_LEAN_AND_MEAN  # 添加这个定义以减少 Windows 头文件包含
    )
    
    # 添加 Windows 特定的头文件目录
    target_include_directories(tcp_server PRIVATE 
        "${CMAKE_SOURCE_DIR}/src/main/cpp/server/windows"
    )
    
    # 确保创建 windows 目录
    file(MAKE_DIRECTORY "${CMAKE_SOURCE_DIR}/src/main/cpp/server/windows")
else()
    # 非 Windows 平台的定义
    if(APPLE)
        target_compile_definitions(tcp_server PRIVATE PLATFORM_MACOS)
    else()
        target_compile_definitions(tcp_server PRIVATE PLATFORM_LINUX)
    endif()
endif()

# 使用目标名称进行链接
target_link_libraries(
    tcp_server 
    resp_parser
    data_store
    command_handler
    config_parser
)

# 服务器可执行文件
add_executable(mini_cache_server src/main/cpp/server/main.cpp)

# 修改链接方式，使用目标名称而不是文件路径
target_link_libraries(mini_cache_server 
    resp_parser
    data_store
    command_handler
    tcp_server
    config_parser
)


# 客户端可执行文件部分之前添加 readline 库的查找
if(APPLE)
    # macOS 上 readline 通常由 Homebrew 安装在 /usr/local 或 /opt/homebrew
    find_library(READLINE_LIBRARY readline HINTS /usr/local/lib /opt/homebrew/lib)
    find_library(NCURSES_LIBRARY ncurses HINTS /usr/local/lib /opt/homebrew/lib)
    find_package(nlohmann_json REQUIRED)
    include_directories(/usr/local/include /opt/homebrew/include)
elseif(WIN32)
    # Windows 平台上，readline 和 ncurses 可能不可用，设置为空值
    set(READLINE_LIBRARY "")
    set(NCURSES_LIBRARY "")
else()
    # Linux 和其他平台
    find_library(READLINE_LIBRARY readline)
    find_library(NCURSES_LIBRARY ncurses)
endif()

# 客户端可执行文件
add_executable(mini_cache_cli src/main/cpp/client/cli_main.cpp)
# 修改链接方式，使用目标名称而不是文件路径
target_link_libraries(mini_cache_cli resp_parser)
# 确保 resp_parser 库在 mini_cache_cli 之前构建
add_dependencies(mini_cache_cli resp_parser)

# 仅在非 Windows 平台上链接 readline 和 ncurses
if(NOT WIN32)
    target_link_libraries(mini_cache_cli 
        ${READLINE_LIBRARY}
        ${NCURSES_LIBRARY}
    )
endif()


# 为 Windows 平台添加 Winsock2 库和定义
if(WIN32)
    # 添加 Windows 平台特定的编译定义
    target_compile_definitions(mini_cache_server PRIVATE PLATFORM_WINDOWS _WIN32)
    target_compile_definitions(mini_cache_cli PRIVATE PLATFORM_WINDOWS _WIN32 _WINSOCK_DEPRECATED_NO_WARNINGS)
    target_compile_definitions(data_store PRIVATE PLATFORM_WINDOWS _WIN32)
    target_compile_definitions(tcp_server PRIVATE PLATFORM_WINDOWS _WIN32)
    
    # 链接 Windows 网络库
    target_link_libraries(mini_cache_server wsock32 ws2_32)
    target_link_libraries(mini_cache_cli wsock32 ws2_32)
    target_link_libraries(tcp_server wsock32 ws2_32)
endif()

# 如果是MacOS， 需要特别处理
if(APPLE)
    target_compile_definitions(mini_cache_server PRIVATE PLATFORM_MACOS)
    target_compile_definitions(mini_cache_cli PRIVATE PLATFORM_MACOS)
endif()

# 复制配置文件到输出目录
# file(COPY ${CMAKE_SOURCE_DIR}/src/main/cpp/conf/mcs.conf DESTINATION ${CMAKE_CONF_OUTPUT_DIRECTORY})