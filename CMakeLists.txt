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

# ============================================================================
# 依赖复制配置 (Dependency Copy Configuration)
# ============================================================================

# 包含依赖复制脚本
include(${CMAKE_SOURCE_DIR}/scripts/copy_dependencies.cmake)

# 为可执行文件创建依赖复制目标
if(WIN32)
    # Windows 平台需要复制 DLL 依赖
    copy_target_dependencies(mini_cache_server)
    copy_target_dependencies(mini_cache_cli)
    
    # 创建一个总的依赖复制目标
    add_custom_target(copy_all_dependencies
        DEPENDS copy_mini_cache_server_dependencies copy_mini_cache_cli_dependencies
        COMMENT "复制所有可执行文件的依赖"
    )
    
    # 添加便捷的复制脚本目标
    add_custom_target(copy_deps_script
        COMMAND ${CMAKE_COMMAND} -E copy_if_different
            "${CMAKE_SOURCE_DIR}/scripts/copy_dependencies.bat"
            "${CMAKE_BINARY_DIR}/copy_dependencies.bat"
        COMMAND ${CMAKE_COMMAND} -E copy_if_different
            "${CMAKE_SOURCE_DIR}/scripts/copy_dependencies.sh"
            "${CMAKE_BINARY_DIR}/copy_dependencies.sh"
        COMMENT "复制依赖管理脚本到构建目录"
    )
    
    # 在构建完成后自动复制依赖（可选）
    if(AUTO_COPY_DEPENDENCIES)
        add_dependencies(mini_cache_server copy_mini_cache_server_dependencies)
        add_dependencies(mini_cache_cli copy_mini_cache_cli_dependencies)
    endif()
endif()

# ============================================================================
# 安装配置 (Install Configuration)
# ============================================================================

# 安装可执行文件
install(TARGETS mini_cache_server mini_cache_cli
    RUNTIME DESTINATION bin
    COMPONENT Runtime
)

# 安装共享库
install(TARGETS config_parser resp_parser data_store command_handler tcp_server
    LIBRARY DESTINATION lib
    ARCHIVE DESTINATION lib
    RUNTIME DESTINATION bin  # Windows DLL
    COMPONENT Libraries
)

# 安装头文件（如果需要开发包）
install(DIRECTORY src/main/cpp/server/
    DESTINATION include/minicache
    COMPONENT Development
    FILES_MATCHING PATTERN "*.hpp" PATTERN "*.h"
)

# 安装文档
install(FILES 
    readme.md 
    readme_zh.md 
    LICENSE.txt
    DESTINATION share/doc/minicache
    COMPONENT Documentation
    OPTIONAL
)

# 安装示例和配置文件
if(EXISTS "${CMAKE_SOURCE_DIR}/examples")
    install(DIRECTORY examples/
        DESTINATION share/minicache/examples
        COMPONENT Examples
    )
endif()

if(EXISTS "${CMAKE_SOURCE_DIR}/src/main/cpp/conf")
    install(DIRECTORY src/main/cpp/conf/
        DESTINATION etc/minicache
        COMPONENT Configuration
    )
endif()

# ============================================================================
# CPack 配置 (Package Configuration)
# ============================================================================

# 包含 CPack 模块
include(CPack)

# 基本包信息
set(CPACK_PACKAGE_NAME "MiniCache")
set(CPACK_PACKAGE_VENDOR "MiniCache Project")
set(CPACK_PACKAGE_DESCRIPTION_SUMMARY "高性能内存缓存服务器")
set(CPACK_PACKAGE_DESCRIPTION "MiniCache 是一个轻量级、高性能的内存缓存服务器，兼容 Redis 协议，支持多平台部署。")
set(CPACK_PACKAGE_VERSION_MAJOR "1")
set(CPACK_PACKAGE_VERSION_MINOR "0")
set(CPACK_PACKAGE_VERSION_PATCH "0")
set(CPACK_PACKAGE_VERSION "${CPACK_PACKAGE_VERSION_MAJOR}.${CPACK_PACKAGE_VERSION_MINOR}.${CPACK_PACKAGE_VERSION_PATCH}")
set(CPACK_PACKAGE_CONTACT "minicache@example.com")
set(CPACK_PACKAGE_HOMEPAGE_URL "https://github.com/minicache/minicache")

# 许可证文件
if(EXISTS "${CMAKE_SOURCE_DIR}/LICENSE.txt")
    set(CPACK_RESOURCE_FILE_LICENSE "${CMAKE_SOURCE_DIR}/LICENSE.txt")
endif()

# README 文件
if(EXISTS "${CMAKE_SOURCE_DIR}/readme.md")
    set(CPACK_RESOURCE_FILE_README "${CMAKE_SOURCE_DIR}/readme.md")
endif()

# 组件配置
set(CPACK_COMPONENTS_ALL Runtime Libraries Development Documentation Examples Configuration)

# 组件描述
set(CPACK_COMPONENT_RUNTIME_DISPLAY_NAME "运行时文件")
set(CPACK_COMPONENT_RUNTIME_DESCRIPTION "MiniCache 服务器和客户端可执行文件")
set(CPACK_COMPONENT_RUNTIME_REQUIRED TRUE)

set(CPACK_COMPONENT_LIBRARIES_DISPLAY_NAME "运行时库")
set(CPACK_COMPONENT_LIBRARIES_DESCRIPTION "MiniCache 运行时共享库")
set(CPACK_COMPONENT_LIBRARIES_REQUIRED TRUE)

set(CPACK_COMPONENT_DEVELOPMENT_DISPLAY_NAME "开发文件")
set(CPACK_COMPONENT_DEVELOPMENT_DESCRIPTION "头文件和开发库")
set(CPACK_COMPONENT_DEVELOPMENT_DEPENDS Libraries)

set(CPACK_COMPONENT_DOCUMENTATION_DISPLAY_NAME "文档")
set(CPACK_COMPONENT_DOCUMENTATION_DESCRIPTION "用户手册和开发文档")

set(CPACK_COMPONENT_EXAMPLES_DISPLAY_NAME "示例")
set(CPACK_COMPONENT_EXAMPLES_DESCRIPTION "示例配置和使用案例")

set(CPACK_COMPONENT_CONFIGURATION_DISPLAY_NAME "配置文件")
set(CPACK_COMPONENT_CONFIGURATION_DESCRIPTION "默认配置文件")

# 平台特定配置
if(WIN32)
    # Windows 配置
    set(CPACK_GENERATOR "NSIS;ZIP")
    set(CPACK_NSIS_DISPLAY_NAME "MiniCache ${CPACK_PACKAGE_VERSION}")
    set(CPACK_NSIS_PACKAGE_NAME "MiniCache")
    set(CPACK_NSIS_CONTACT "${CPACK_PACKAGE_CONTACT}")
    set(CPACK_NSIS_URL_INFO_ABOUT "${CPACK_PACKAGE_HOMEPAGE_URL}")
    set(CPACK_NSIS_HELP_LINK "${CPACK_PACKAGE_HOMEPAGE_URL}")
    set(CPACK_NSIS_MODIFY_PATH ON)
    set(CPACK_NSIS_ENABLE_UNINSTALL_BEFORE_INSTALL ON)
    
    # 创建开始菜单快捷方式
    set(CPACK_NSIS_MENU_LINKS
        "bin/mini_cache_server.exe" "MiniCache Server"
        "bin/mini_cache_cli.exe" "MiniCache Client"
        "share/doc/minicache/readme.md" "Documentation"
    )
    
elseif(APPLE)
    # macOS 配置
    set(CPACK_GENERATOR "DragNDrop;TGZ")
    set(CPACK_DMG_VOLUME_NAME "MiniCache-${CPACK_PACKAGE_VERSION}")
    set(CPACK_DMG_FORMAT "UDZO")
    
elseif(UNIX)
    # Linux 配置
    set(CPACK_GENERATOR "DEB;RPM;TGZ")
    
    # Debian 包配置
    set(CPACK_DEBIAN_PACKAGE_MAINTAINER "${CPACK_PACKAGE_CONTACT}")
    set(CPACK_DEBIAN_PACKAGE_SECTION "database")
    set(CPACK_DEBIAN_PACKAGE_PRIORITY "optional")
    set(CPACK_DEBIAN_PACKAGE_DEPENDS "libc6 (>= 2.17)")
    set(CPACK_DEBIAN_PACKAGE_HOMEPAGE "${CPACK_PACKAGE_HOMEPAGE_URL}")
    
    # RPM 包配置
    set(CPACK_RPM_PACKAGE_GROUP "Applications/Databases")
    set(CPACK_RPM_PACKAGE_LICENSE "MIT")
    set(CPACK_RPM_PACKAGE_URL "${CPACK_PACKAGE_HOMEPAGE_URL}")
    set(CPACK_RPM_PACKAGE_REQUIRES "glibc >= 2.17")
    
    # 设置包文件名
    set(CPACK_PACKAGE_FILE_NAME "${CPACK_PACKAGE_NAME}-${CPACK_PACKAGE_VERSION}-${CMAKE_SYSTEM_NAME}-${CMAKE_SYSTEM_PROCESSOR}")
endif()

# 源码包配置
set(CPACK_SOURCE_GENERATOR "TGZ;ZIP")
set(CPACK_SOURCE_PACKAGE_FILE_NAME "${CPACK_PACKAGE_NAME}-${CPACK_PACKAGE_VERSION}-src")
set(CPACK_SOURCE_IGNORE_FILES
    "/\\.git/"
    "/\\.cache/"
    "/build/"
    "/\\.idea/"
    "/\\.vscode/"
    "/\\.DS_Store"
    "/Thumbs\\.db"
    "~$"
    "\\.swp$"
    "\\.orig$"
    "/CMakeLists\\.txt\\.user$"
    "/\\.cproject$"
    "/\\.project$"
)

# 打包前的脚本（可选）
# set(CPACK_PRE_BUILD_SCRIPTS "${CMAKE_SOURCE_DIR}/scripts/pre_build.cmake")
# set(CPACK_POST_BUILD_SCRIPTS "${CMAKE_SOURCE_DIR}/scripts/post_build.cmake")

# 自定义安装脚本（Linux/Unix）
if(UNIX AND NOT APPLE)
    # 创建系统服务文件（可选）
    configure_file(
        "${CMAKE_SOURCE_DIR}/scripts/minicache.service.in"
        "${CMAKE_BINARY_DIR}/minicache.service"
        @ONLY
    )
    
    install(FILES "${CMAKE_BINARY_DIR}/minicache.service"
        DESTINATION lib/systemd/system
        COMPONENT Runtime
        OPTIONAL
    )
endif()

message(STATUS "CPack generators: ${CPACK_GENERATOR}")
message(STATUS "Package will be created as: ${CPACK_PACKAGE_FILE_NAME}")