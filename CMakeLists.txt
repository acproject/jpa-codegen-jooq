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
set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/lib)
set(CMAKE_RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/bin)
set(CMAKE_CONF_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/conf)

# 创建 ConfigParser 库
add_library(config_parser SHARED src/main/cpp/server/ConfigParser.cpp)

# 创建 RespParser 库
add_library(resp_parser SHARED src/main/cpp/server/RespParser.cpp)

# 创建 DataStore 库
add_library(data_store SHARED src/main/cpp/server/DataStore.cpp)

FetchContent_Declare(
    nlohmann_json
    GIT_REPOSITORY https://github.com/nlohmann/json.git
    GIT_TAG v3.11.2  # 指定版本号
)
FetchContent_MakeAvailable(nlohmann_json)
# 创建 CommandHandler 库
# 修改 command_handler 库的链接部分
add_library(command_handler SHARED
    src/main/cpp/server/CommandHandler.cpp
)
target_link_libraries(command_handler data_store)
target_link_libraries(data_store PRIVATE nlohmann_json::nlohmann_json)

# 确保 data_store 库在 command_handler 之前构建
add_dependencies(command_handler data_store)
# 创建 TcpServer 库
add_library(tcp_server SHARED src/main/cpp/server/TcpServer.cpp)

target_link_libraries(
    tcp_server 
    resp_parser 
    data_store 
    command_handler
    config_parser
)

# 服务器可执行文件
add_executable(mini_cache_server src/main/cpp/server/main.cpp)

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
else()
    find_library(READLINE_LIBRARY readline)
    find_library(NCURSES_LIBRARY ncurses)
endif()

# 客户端可执行文件
add_executable(mini_cache_cli src/main/cpp/client/cli_main.cpp)
target_link_libraries(mini_cache_cli 
    resp_parser
    ${READLINE_LIBRARY}
    ${NCURSES_LIBRARY}
)


# 如果是MacOS， 需要特别处理
if(APPLE)
    target_compile_definitions(mini_cache_server PRIVATE PLATFORM_MACOS)
    target_compile_definitions(mini_cache_cli PRIVATE PLATFORM_MACOS)
endif()

# 复制配置文件到输出目录
file(COPY ${CMAKE_SOURCE_DIR}/src/main/cpp/conf/mcs.conf DESTINATION ${CMAKE_CONF_OUTPUT_DIRECTORY})