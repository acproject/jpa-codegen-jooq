# MiniCache 依赖复制 CMake 脚本
# 用于自动复制可执行文件的依赖库
# 作者: MiniCache Project
# 版本: 1.0.0

cmake_minimum_required(VERSION 3.15)

# 设置默认参数（仅在脚本模式下）
if(CMAKE_SCRIPT_MODE_FILE)
    if(NOT DEFINED TARGET_EXECUTABLE)
        message(FATAL_ERROR "TARGET_EXECUTABLE 未定义")
    endif()
    
    if(NOT DEFINED OUTPUT_DIRECTORY)
        set(OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/dlls")
    endif()
    
    if(NOT DEFINED VERBOSE_OUTPUT)
        set(VERBOSE_OUTPUT OFF)
    endif()
    
    if(NOT DEFINED FORCE_COPY)
        set(FORCE_COPY OFF)
    endif()
    
    if(NOT DEFINED EXCLUDE_SYSTEM_DLLS)
        set(EXCLUDE_SYSTEM_DLLS ON)
    endif()
endif()

# 打印信息函数
function(print_info message)
    if(VERBOSE_OUTPUT)
        message(STATUS "[INFO] ${message}")
    endif()
endfunction()

function(print_warning message)
    message(WARNING "[WARNING] ${message}")
endfunction()

function(print_error message)
    message(SEND_ERROR "[ERROR] ${message}")
endfunction()

# 系统 DLL 列表
set(SYSTEM_DLLS
    "kernel32.dll"
    "user32.dll"
    "gdi32.dll"
    "winspool.drv"
    "comdlg32.dll"
    "advapi32.dll"
    "shell32.dll"
    "ole32.dll"
    "oleaut32.dll"
    "uuid.dll"
    "winmm.dll"
    "version.dll"
    "ws2_32.dll"
    "wsock32.dll"
    "netapi32.dll"
    "ntdll.dll"
    "msvcrt.dll"
    "secur32.dll"
    "rpcrt4.dll"
    "api-ms-win-*.dll"
    "vcruntime*.dll"
    "msvcp*.dll"
)

# 检查是否为系统 DLL
function(is_system_dll dll_path result_var)
    get_filename_component(dll_name "${dll_path}" NAME)
    string(TOLOWER "${dll_name}" dll_name_lower)
    
    set(is_system FALSE)
    foreach(sys_dll ${SYSTEM_DLLS})
        string(TOLOWER "${sys_dll}" sys_dll_lower)
        if(dll_name_lower MATCHES "^${sys_dll_lower}$")
            set(is_system TRUE)
            break()
        endif()
    endforeach()
    
    set(${result_var} ${is_system} PARENT_SCOPE)
endfunction()

# 查找 DLL 的完整路径
function(find_dll_path dll_name result_var)
    set(dll_path "")
    
    # 搜索路径列表
    set(search_paths)
    
    # 添加系统路径
    if(WIN32)
        list(APPEND search_paths
            "$ENV{WINDIR}/System32"
            "$ENV{WINDIR}/SysWOW64"
            "$ENV{WINDIR}"
        )
        
        # 添加 MinGW 路径
        if(MINGW)
            if(DEFINED ENV{MINGW_PREFIX})
                list(APPEND search_paths "$ENV{MINGW_PREFIX}/bin")
            endif()
            
            # 常见的 MSYS2/MinGW 路径
            list(APPEND search_paths
                "C:/msys64/mingw64/bin"
                "C:/msys64/mingw32/bin"
                "C:/mingw64/bin"
                "C:/mingw32/bin"
            )
        endif()
        
        # 添加 PATH 环境变量中的路径
        string(REPLACE ";" ";" path_list "$ENV{PATH}")
        list(APPEND search_paths ${path_list})
    else()
        # Unix 系统
        # 获取可执行文件所在目录的父目录作为构建根目录
        get_filename_component(exe_dir "${TARGET_EXECUTABLE}" DIRECTORY)
        get_filename_component(build_root "${exe_dir}" DIRECTORY)
        
        list(APPEND search_paths
            "${build_root}/lib"  # 构建目录中的库
            "${CMAKE_BINARY_DIR}/lib"  # CMake二进制目录中的库
            "/usr/lib"
            "/usr/local/lib"
            "/lib"
            "/lib64"
            "/usr/lib64"
            "/usr/local/lib64"
        )
        
        # macOS 特定路径
        if(APPLE)
            list(APPEND search_paths
                "/usr/local/lib"
                "/opt/homebrew/lib"
                "/opt/local/lib"
            )
        endif()
        
        # 添加 LD_LIBRARY_PATH (Linux) 或 DYLD_LIBRARY_PATH (macOS)
        if(APPLE AND DEFINED ENV{DYLD_LIBRARY_PATH})
            string(REPLACE ":" ";" dyld_path_list "$ENV{DYLD_LIBRARY_PATH}")
            list(APPEND search_paths ${dyld_path_list})
        elseif(DEFINED ENV{LD_LIBRARY_PATH})
            string(REPLACE ":" ";" ld_path_list "$ENV{LD_LIBRARY_PATH}")
            list(APPEND search_paths ${ld_path_list})
        endif()
    endif()
    
    # 在搜索路径中查找 DLL
    foreach(search_path ${search_paths})
        if(EXISTS "${search_path}")
            set(full_path "${search_path}/${dll_name}")
            if(EXISTS "${full_path}")
                set(dll_path "${full_path}")
                break()
            endif()
        endif()
    endforeach()
    
    set(${result_var} "${dll_path}" PARENT_SCOPE)
endfunction()

# 获取可执行文件的依赖列表
function(get_dependencies executable_path dependencies_var)
    set(deps_list)
    
    if(WIN32)
        # Windows 平台使用 dumpbin 或 objdump
        find_program(DUMPBIN_EXECUTABLE dumpbin)
        find_program(OBJDUMP_EXECUTABLE objdump)
        
        if(DUMPBIN_EXECUTABLE)
            # 使用 MSVC dumpbin
            execute_process(
                COMMAND "${DUMPBIN_EXECUTABLE}" /dependents "${executable_path}"
                OUTPUT_VARIABLE dumpbin_output
                ERROR_QUIET
                OUTPUT_STRIP_TRAILING_WHITESPACE
            )
            
            # 解析 dumpbin 输出
            string(REGEX MATCHALL "[^\r\n]+\.dll" dll_matches "${dumpbin_output}")
            foreach(dll_match ${dll_matches})
                string(STRIP "${dll_match}" dll_name)
                if(dll_name)
                    list(APPEND deps_list "${dll_name}")
                endif()
            endforeach()
            
        elseif(OBJDUMP_EXECUTABLE)
            # 使用 MinGW objdump
            execute_process(
                COMMAND "${OBJDUMP_EXECUTABLE}" -p "${executable_path}"
                OUTPUT_VARIABLE objdump_output
                ERROR_QUIET
                OUTPUT_STRIP_TRAILING_WHITESPACE
            )
            
            # 解析 objdump 输出
            string(REGEX MATCHALL "DLL Name: ([^\r\n]+)" dll_matches "${objdump_output}")
            foreach(dll_match ${dll_matches})
                string(REGEX REPLACE "DLL Name: (.+)" "\\1" dll_name "${dll_match}")
                string(STRIP "${dll_name}" dll_name)
                if(dll_name)
                    list(APPEND deps_list "${dll_name}")
                endif()
            endforeach()
        else()
            print_error("未找到 dumpbin 或 objdump 工具")
            return()
        endif()
        
    else()
        # Unix 平台使用 ldd 或 otool
        if(APPLE)
            # macOS 使用 otool
            find_program(OTOOL_EXECUTABLE otool)
            if(OTOOL_EXECUTABLE)
                execute_process(
                    COMMAND "${OTOOL_EXECUTABLE}" -L "${executable_path}"
                    OUTPUT_VARIABLE otool_output
                    ERROR_QUIET
                    OUTPUT_STRIP_TRAILING_WHITESPACE
                )
                
                # 解析 otool 输出
                  string(REGEX MATCHALL "[^\r\n]+" otool_lines "${otool_output}")
                  foreach(otool_line ${otool_lines})
                       if(otool_line MATCHES "^[ \t]+([^ \t]+)[ \t]+\\(")
                           string(REGEX REPLACE "^[ \t]+([^ \t]+)[ \t]+\\(.*" "\\1" lib_path "${otool_line}")
                           
                           # 处理 @rpath 引用
                           if(lib_path MATCHES "^@rpath/(.+)")
                               string(REGEX REPLACE "^@rpath/(.+)" "\\1" lib_name "${lib_path}")
                               # 在构建目录中查找库文件
                               get_filename_component(exe_dir "${executable_path}" DIRECTORY)
                               set(rpath_lib "${exe_dir}/../lib/${lib_name}")
                               if(EXISTS "${rpath_lib}")
                                   list(APPEND deps_list "${lib_name}")
                               endif()
                           # 跳过系统库，处理实际的库文件
                           elseif(NOT lib_path MATCHES "^/usr/lib/" AND NOT lib_path MATCHES "^/System/")
                               if(EXISTS "${lib_path}")
                                   get_filename_component(lib_name "${lib_path}" NAME)
                                   list(APPEND deps_list "${lib_name}")
                               endif()
                           endif()
                       endif()
                   endforeach()
            else()
                print_error("未找到 otool 工具")
                return()
            endif()
        else()
            # Linux 使用 ldd
            find_program(LDD_EXECUTABLE ldd)
            if(LDD_EXECUTABLE)
                execute_process(
                    COMMAND "${LDD_EXECUTABLE}" "${executable_path}"
                    OUTPUT_VARIABLE ldd_output
                    ERROR_QUIET
                    OUTPUT_STRIP_TRAILING_WHITESPACE
                )
                
                # 解析 ldd 输出
                string(REGEX MATCHALL "[^\r\n]+" ldd_lines "${ldd_output}")
                foreach(ldd_line ${ldd_lines})
                    if(ldd_line MATCHES "=> ([^ ]+)")
                        string(REGEX REPLACE ".* => ([^ ]+) .*" "\\1" lib_path "${ldd_line}")
                        if(EXISTS "${lib_path}")
                            get_filename_component(lib_name "${lib_path}" NAME)
                            list(APPEND deps_list "${lib_name}")
                        endif()
                    endif()
                endforeach()
            else()
                print_error("未找到 ldd 工具")
                return()
            endif()
        endif()
    endif()
    
    # 去重
    list(REMOVE_DUPLICATES deps_list)
    set(${dependencies_var} "${deps_list}" PARENT_SCOPE)
endfunction()

# 复制依赖文件
function(copy_dependencies)
    print_info("开始分析可执行文件: ${TARGET_EXECUTABLE}")
    print_info("输出目录: ${OUTPUT_DIRECTORY}")
    
    # 检查可执行文件是否存在
    if(NOT EXISTS "${TARGET_EXECUTABLE}")
        print_error("可执行文件不存在: ${TARGET_EXECUTABLE}")
        return()
    endif()
    
    # 创建输出目录
    file(MAKE_DIRECTORY "${OUTPUT_DIRECTORY}")
    
    # 获取依赖列表
    get_dependencies("${TARGET_EXECUTABLE}" dependencies)
    
    if(NOT dependencies)
        print_warning("未找到依赖文件")
        return()
    endif()
    
    # 统计变量
    set(total_count 0)
    set(copied_count 0)
    set(skipped_count 0)
    
    # 复制每个依赖
    foreach(dll_name ${dependencies})
        math(EXPR total_count "${total_count} + 1")
        
        # 检查是否为系统 DLL
        if(EXCLUDE_SYSTEM_DLLS)
            is_system_dll("${dll_name}" is_sys_dll)
            if(is_sys_dll)
                print_info("跳过系统 DLL: ${dll_name}")
                math(EXPR skipped_count "${skipped_count} + 1")
                continue()
            endif()
        endif()
        
        # 查找 DLL 的完整路径
        find_dll_path("${dll_name}" dll_full_path)
        
        if(NOT dll_full_path)
            print_warning("未找到 DLL: ${dll_name}")
            math(EXPR skipped_count "${skipped_count} + 1")
            continue()
        endif()
        
        # 目标路径
        set(dst_path "${OUTPUT_DIRECTORY}/${dll_name}")
        
        # 检查是否需要复制
        set(should_copy TRUE)
        if(EXISTS "${dst_path}" AND NOT FORCE_COPY)
            print_info("文件已存在，跳过: ${dll_name}")
            set(should_copy FALSE)
            math(EXPR skipped_count "${skipped_count} + 1")
        endif()
        
        # 复制文件
        if(should_copy)
            file(COPY "${dll_full_path}" DESTINATION "${OUTPUT_DIRECTORY}")
            if(EXISTS "${dst_path}")
                print_info("已复制: ${dll_name}")
                math(EXPR copied_count "${copied_count} + 1")
            else()
                print_error("复制失败: ${dll_full_path}")
                math(EXPR skipped_count "${skipped_count} + 1")
            endif()
        endif()
    endforeach()
    
    # 输出统计信息
    message(STATUS "")
    message(STATUS "依赖复制完成!")
    message(STATUS "总计发现: ${total_count} 个依赖文件")
    message(STATUS "成功复制: ${copied_count} 个文件")
    if(skipped_count GREATER 0)
        message(STATUS "跳过文件: ${skipped_count} 个")
    endif()
    message(STATUS "所有依赖已复制到: ${OUTPUT_DIRECTORY}")
endfunction()

# 主函数
if(CMAKE_SCRIPT_MODE_FILE)
    # 脚本模式运行
    copy_dependencies()
else()
    # 作为模块包含时，定义函数供外部调用
    function(copy_target_dependencies target)
        get_target_property(target_type ${target} TYPE)
        if(target_type STREQUAL "EXECUTABLE")
            get_target_property(target_location ${target} LOCATION)
            if(NOT target_location)
                set(target_location "$<TARGET_FILE:${target}>")
            endif()
            
            # 创建自定义目标
            add_custom_target(copy_${target}_dependencies
                COMMAND ${CMAKE_COMMAND}
                    -DTARGET_EXECUTABLE="${target_location}"
                    -DOUTPUT_DIRECTORY="${CMAKE_BINARY_DIR}/dlls"
                    -DVERBOSE_OUTPUT=ON
                    -DFORCE_COPY=OFF
                    -DEXCLUDE_SYSTEM_DLLS=ON
                    -P "${CMAKE_CURRENT_LIST_FILE}"
                DEPENDS ${target}
                COMMENT "复制 ${target} 的依赖文件"
                VERBATIM
            )
        else()
            message(WARNING "${target} 不是可执行文件目标")
        endif()
    endfunction()
endif()