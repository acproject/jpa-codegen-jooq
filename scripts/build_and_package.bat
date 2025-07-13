@echo off
setlocal enabledelayedexpansion

REM =============================================================================
REM MiniCache 构建和打包脚本 (Windows)
REM Build and Package Script for MiniCache (Windows)
REM =============================================================================

REM 脚本目录
set "SCRIPT_DIR=%~dp0"
set "PROJECT_ROOT=%SCRIPT_DIR%.."

REM 默认配置
set "BUILD_TYPE=Release"
set "BUILD_DIR=build"
set "CLEAN_BUILD=false"
set "COPY_DEPS=true"
set "CREATE_PACKAGE=false"
set "VERBOSE=false"
set "JOBS=4"

REM 检测 CPU 核心数
if defined NUMBER_OF_PROCESSORS (
    set "JOBS=%NUMBER_OF_PROCESSORS%"
)

REM 颜色定义（Windows 10+ 支持 ANSI 转义序列）
set "RED=[31m"
set "GREEN=[32m"
set "YELLOW=[33m"
set "BLUE=[34m"
set "NC=[0m"

REM 日志函数
:log_info
echo %BLUE%[INFO]%NC% %~1
goto :eof

:log_success
echo %GREEN%[SUCCESS]%NC% %~1
goto :eof

:log_warning
echo %YELLOW%[WARNING]%NC% %~1
goto :eof

:log_error
echo %RED%[ERROR]%NC% %~1 1>&2
goto :eof

:log_verbose
if "%VERBOSE%"=="true" (
    echo %BLUE%[VERBOSE]%NC% %~1
)
goto :eof

REM 显示帮助信息
:show_help
echo 用法: %~nx0 [选项]
echo.
echo 选项:
echo   /h, /help, /?           显示此帮助信息
echo   /v, /verbose            详细输出
echo   /c, /clean              清理构建（删除构建目录）
echo   /t, /type ^<类型^>        构建类型 (Debug^|Release^|RelWithDebInfo^|MinSizeRel)
echo   /d, /dir ^<目录^>         构建目录（默认: build）
echo   /j, /jobs ^<数量^>        并行构建任务数（默认: 自动检测）
echo   /no-deps                不复制依赖文件
echo   /p, /package            创建安装包
echo   /msvc                   使用 MSVC 编译器
echo   /mingw                  使用 MinGW 编译器
echo.
echo 示例:
echo   %~nx0                   # 默认 Release 构建
echo   %~nx0 /c /t Debug       # 清理并构建 Debug 版本
echo   %~nx0 /v /p             # 详细输出并创建安装包
echo   %~nx0 /mingw /p         # MinGW 构建并打包
echo.
goto :eof

REM 检测环境
:detect_environment
call :log_info "检测构建环境..."

REM 检测操作系统
call :log_info "检测到 Windows 环境"
set "PLATFORM=windows"

REM 检测编译器
set "COMPILER_TYPE=auto"

REM 检测 MSVC
where cl.exe >nul 2>&1
if %errorlevel% equ 0 (
    for /f "tokens=*" %%i in ('cl 2^>^&1 ^| findstr "Version"') do (
        call :log_verbose "MSVC: %%i"
    )
    if "%COMPILER_TYPE%"=="auto" set "COMPILER_TYPE=msvc"
)

REM 检测 MinGW
where gcc.exe >nul 2>&1
if %errorlevel% equ 0 (
    for /f "tokens=*" %%i in ('gcc --version 2^>^&1 ^| findstr "gcc"') do (
        call :log_verbose "GCC: %%i"
    )
    if "%COMPILER_TYPE%"=="auto" set "COMPILER_TYPE=mingw"
)

REM 检测 CMake
where cmake.exe >nul 2>&1
if %errorlevel% neq 0 (
    call :log_error "未找到 CMake，请先安装 CMake"
    exit /b 1
)

for /f "tokens=*" %%i in ('cmake --version 2^>^&1 ^| findstr "cmake"') do (
    call :log_verbose "%%i"
)

call :log_info "编译器类型: %COMPILER_TYPE%"
goto :eof

REM 清理构建目录
:clean_build
if exist "%BUILD_DIR%" (
    call :log_info "清理构建目录: %BUILD_DIR%"
    rmdir /s /q "%BUILD_DIR%"
)
goto :eof

REM 配置构建
:configure_build
call :log_info "配置构建..."

REM 创建构建目录
if not exist "%BUILD_DIR%" mkdir "%BUILD_DIR%"
cd /d "%BUILD_DIR%"

REM CMake 配置选项
set "CMAKE_ARGS=-DCMAKE_BUILD_TYPE=%BUILD_TYPE% -DCMAKE_EXPORT_COMPILE_COMMANDS=ON"

REM 编译器特定配置
if "%COMPILER_TYPE%"=="msvc" (
    set "CMAKE_ARGS=%CMAKE_ARGS% -G "Visual Studio 16 2019""
) else if "%COMPILER_TYPE%"=="mingw" (
    set "CMAKE_ARGS=%CMAKE_ARGS% -G "MinGW Makefiles""
)

REM Windows 特定配置
set "CMAKE_ARGS=%CMAKE_ARGS% -DAUTO_COPY_DEPENDENCIES=%COPY_DEPS%"

REM 详细输出
if "%VERBOSE%"=="true" (
    set "CMAKE_ARGS=%CMAKE_ARGS% --debug-output"
)

call :log_verbose "CMake 参数: %CMAKE_ARGS%"

REM 运行 CMake 配置
cmake %CMAKE_ARGS% "%PROJECT_ROOT%"
if %errorlevel% neq 0 (
    call :log_error "CMake 配置失败"
    exit /b 1
)
goto :eof

REM 构建项目
:build_project
call :log_info "构建项目..."

set "BUILD_ARGS=--build . --config %BUILD_TYPE%"

REM 并行构建
if %JOBS% gtr 1 (
    set "BUILD_ARGS=%BUILD_ARGS% --parallel %JOBS%"
)

REM 详细输出
if "%VERBOSE%"=="true" (
    set "BUILD_ARGS=%BUILD_ARGS% --verbose"
)

call :log_verbose "构建参数: %BUILD_ARGS%"

REM 运行构建
cmake %BUILD_ARGS%
if %errorlevel% neq 0 (
    call :log_error "构建失败"
    exit /b 1
)
goto :eof

REM 复制依赖
:copy_dependencies
if "%COPY_DEPS%"=="false" goto :eof

call :log_info "复制依赖文件..."

REM 查找可执行文件
set "EXECUTABLES="
for /r . %%f in (*.exe) do (
    set "EXECUTABLES=!EXECUTABLES! %%f"
)

if "%EXECUTABLES%"=="" (
    call :log_warning "未找到可执行文件"
    goto :eof
)

REM 为每个可执行文件复制依赖
for %%e in (%EXECUTABLES%) do (
    call :log_info "处理可执行文件: %%~nxe"
    
    REM 使用适当的脚本复制依赖
    set "COPY_ARGS="
    if "%VERBOSE%"=="true" set "COPY_ARGS=/v"
    
    "%SCRIPT_DIR%copy_dependencies.bat" !COPY_ARGS! "%%e" "dlls"
)
goto :eof

REM 创建安装包
:create_package
if "%CREATE_PACKAGE%"=="false" goto :eof

call :log_info "创建安装包..."

REM 检查是否配置了 CPack
if not exist "CPackConfig.cmake" (
    call :log_warning "未找到 CPack 配置，跳过打包"
    goto :eof
)

REM 创建包
set "CPACK_ARGS="
if "%VERBOSE%"=="true" set "CPACK_ARGS=--verbose"

cpack %CPACK_ARGS%
if %errorlevel% neq 0 (
    call :log_error "打包失败"
    exit /b 1
)

REM 显示生成的包
call :log_success "生成的安装包:"
for %%f in (*.zip *.exe *.msi) do (
    if exist "%%f" call :log_info "  %%f"
)
goto :eof

REM 显示构建摘要
:show_summary
call :log_success "构建完成！"
echo.
call :log_info "构建摘要:"
call :log_info "  构建类型: %BUILD_TYPE%"
call :log_info "  构建目录: %BUILD_DIR%"
call :log_info "  平台: %PLATFORM%"
call :log_info "  编译器: %COMPILER_TYPE%"

REM 显示生成的文件
echo.
call :log_info "生成的可执行文件:"
for /r "%BUILD_DIR%" %%f in (*.exe) do (
    call :log_info "  %%~nxf"
)

REM 显示依赖目录
if "%COPY_DEPS%"=="true" (
    if exist "%BUILD_DIR%\dlls" (
        echo.
        call :log_info "依赖文件目录: %BUILD_DIR%\dlls"
        for /f %%i in ('dir /b "%BUILD_DIR%\dlls" 2^>nul ^| find /c /v ""') do (
            call :log_info "  包含 %%i 个依赖文件"
        )
    )
)

echo.
call :log_info "运行测试:"
call :log_info "  cd %BUILD_DIR%"
call :log_info "  mini_cache_server.exe"
call :log_info "  mini_cache_cli.exe"
goto :eof

REM 解析命令行参数
:parse_arguments
:parse_loop
if "%~1"=="" goto :parse_done

if /i "%~1"=="/h" goto :show_help_and_exit
if /i "%~1"=="/help" goto :show_help_and_exit
if /i "%~1"=="/?" goto :show_help_and_exit

if /i "%~1"=="/v" (
    set "VERBOSE=true"
    shift
    goto :parse_loop
)

if /i "%~1"=="/verbose" (
    set "VERBOSE=true"
    shift
    goto :parse_loop
)

if /i "%~1"=="/c" (
    set "CLEAN_BUILD=true"
    shift
    goto :parse_loop
)

if /i "%~1"=="/clean" (
    set "CLEAN_BUILD=true"
    shift
    goto :parse_loop
)

if /i "%~1"=="/t" (
    if "%~2"=="" (
        call :log_error "选项 %~1 需要一个参数"
        exit /b 1
    )
    set "BUILD_TYPE=%~2"
    shift
    shift
    goto :parse_loop
)

if /i "%~1"=="/type" (
    if "%~2"=="" (
        call :log_error "选项 %~1 需要一个参数"
        exit /b 1
    )
    set "BUILD_TYPE=%~2"
    shift
    shift
    goto :parse_loop
)

if /i "%~1"=="/d" (
    if "%~2"=="" (
        call :log_error "选项 %~1 需要一个参数"
        exit /b 1
    )
    set "BUILD_DIR=%~2"
    shift
    shift
    goto :parse_loop
)

if /i "%~1"=="/dir" (
    if "%~2"=="" (
        call :log_error "选项 %~1 需要一个参数"
        exit /b 1
    )
    set "BUILD_DIR=%~2"
    shift
    shift
    goto :parse_loop
)

if /i "%~1"=="/j" (
    if "%~2"=="" (
        call :log_error "选项 %~1 需要一个参数"
        exit /b 1
    )
    set "JOBS=%~2"
    shift
    shift
    goto :parse_loop
)

if /i "%~1"=="/jobs" (
    if "%~2"=="" (
        call :log_error "选项 %~1 需要一个参数"
        exit /b 1
    )
    set "JOBS=%~2"
    shift
    shift
    goto :parse_loop
)

if /i "%~1"=="/no-deps" (
    set "COPY_DEPS=false"
    shift
    goto :parse_loop
)

if /i "%~1"=="/p" (
    set "CREATE_PACKAGE=true"
    shift
    goto :parse_loop
)

if /i "%~1"=="/package" (
    set "CREATE_PACKAGE=true"
    shift
    goto :parse_loop
)

if /i "%~1"=="/msvc" (
    set "COMPILER_TYPE=msvc"
    shift
    goto :parse_loop
)

if /i "%~1"=="/mingw" (
    set "COMPILER_TYPE=mingw"
    set "COPY_DEPS=true"
    shift
    goto :parse_loop
)

call :log_error "未知选项: %~1"
call :show_help
exit /b 1

:show_help_and_exit
call :show_help
exit /b 0

:parse_done
goto :eof

REM 主函数
:main
REM 解析参数
call :parse_arguments %*

REM 切换到项目根目录
cd /d "%PROJECT_ROOT%"

call :log_info "MiniCache 构建脚本 (Windows)"
call :log_info "项目根目录: %PROJECT_ROOT%"

REM 检测环境
call :detect_environment

REM 清理构建（如果需要）
if "%CLEAN_BUILD%"=="true" (
    call :clean_build
)

REM 配置构建
call :configure_build
if %errorlevel% neq 0 exit /b %errorlevel%

REM 构建项目
call :build_project
if %errorlevel% neq 0 exit /b %errorlevel%

REM 复制依赖
call :copy_dependencies

REM 创建安装包
call :create_package

REM 显示摘要
call :show_summary

exit /b 0

REM 脚本入口点
call :main %*