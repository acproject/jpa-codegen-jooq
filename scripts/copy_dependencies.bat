@echo off
setlocal enabledelayedexpansion

REM MiniCache 依赖 DLL 复制脚本 (Windows 批处理版本)
REM 用于在 Windows 环境下复制可执行文件的依赖库
REM 作者: MiniCache Project
REM 版本: 1.0.0

REM 设置代码页为 UTF-8
chcp 65001 >nul

REM 颜色代码 (Windows 10+ 支持 ANSI 转义序列)
set "RED=[31m"
set "GREEN=[32m"
set "YELLOW=[33m"
set "BLUE=[34m"
set "NC=[0m"

REM 默认参数
set "VERBOSE=false"
set "FORCE=false"
set "DRY_RUN=false"
set "DLL_DIR=.\dlls"
set "EXE_PATH="
set "HELP=false"

REM 解析命令行参数
:parse_args
if "%~1"=="" goto :args_done
if "%~1"=="-h" set "HELP=true" & shift & goto :parse_args
if "%~1"=="--help" set "HELP=true" & shift & goto :parse_args
if "%~1"=="-v" set "VERBOSE=true" & shift & goto :parse_args
if "%~1"=="--verbose" set "VERBOSE=true" & shift & goto :parse_args
if "%~1"=="-f" set "FORCE=true" & shift & goto :parse_args
if "%~1"=="--force" set "FORCE=true" & shift & goto :parse_args
if "%~1"=="--dry-run" set "DRY_RUN=true" & shift & goto :parse_args
if "%EXE_PATH%"=="" (
    set "EXE_PATH=%~1"
) else if "%DLL_DIR_ARG%"=="" (
    set "DLL_DIR_ARG=%~1"
    set "DLL_DIR=%~1"
) else (
    call :print_error "过多的参数: %~1"
    goto :show_help
)
shift
goto :parse_args

:args_done

REM 显示帮助
if "%HELP%"=="true" goto :show_help

REM 检查必需参数
if "%EXE_PATH%"=="" (
    call :print_error "缺少可执行文件路径参数"
    goto :show_help
)

REM 检查可执行文件是否存在
if not exist "%EXE_PATH%" (
    call :print_error "可执行文件不存在: %EXE_PATH%"
    exit /b 1
)

REM 检查依赖工具
where dumpbin >nul 2>&1
if errorlevel 1 (
    call :print_warning "dumpbin 未找到，尝试使用 objdump..."
    where objdump >nul 2>&1
    if errorlevel 1 (
        call :print_error "未找到 dumpbin 或 objdump 工具"
        call :print_error "请安装 Visual Studio 或 MinGW 工具链"
        exit /b 1
    )
    set "USE_OBJDUMP=true"
) else (
    set "USE_OBJDUMP=false"
)

REM 创建输出目录
if "%DRY_RUN%"=="false" (
    if not exist "%DLL_DIR%" (
        mkdir "%DLL_DIR%"
        call :print_info "创建输出目录: %DLL_DIR%"
    )
)

REM 主处理逻辑
call :print_info "开始分析可执行文件: %EXE_PATH%"
call :print_info "输出目录: %DLL_DIR%"

if "%VERBOSE%"=="true" (
    call :print_info "详细模式: 开启"
    call :print_info "强制覆盖: %FORCE%"
)

REM 创建临时文件存储依赖列表
set "TEMP_DEPS=%TEMP%\minicache_deps_%RANDOM%.txt"

REM 获取依赖列表
if "%USE_OBJDUMP%"=="true" (
    call :get_deps_objdump
) else (
    call :get_deps_dumpbin
)

if not exist "%TEMP_DEPS%" (
    call :print_warning "未找到依赖文件列表"
    exit /b 0
)

REM 统计和复制
set /a "total_count=0"
set /a "copied_count=0"
set /a "skipped_count=0"

for /f "usebackq delims=" %%i in ("%TEMP_DEPS%") do (
    set /a "total_count+=1"
    call :copy_dll "%%i"
)

REM 清理临时文件
if exist "%TEMP_DEPS%" del "%TEMP_DEPS%"

REM 输出统计信息
echo.
call :print_success "依赖分析完成!"
call :print_info "总计发现: !total_count! 个依赖文件"

if "%DRY_RUN%"=="true" (
    call :print_info "模拟模式: 将复制 !total_count! 个文件"
) else (
    call :print_info "成功复制: !copied_count! 个文件"
    if !skipped_count! gtr 0 (
        call :print_info "跳过文件: !skipped_count! 个"
    )
    call :print_success "所有依赖 DLL 已复制到: %DLL_DIR%"
)

exit /b 0

REM 使用 objdump 获取依赖 (MinGW)
:get_deps_objdump
objdump -p "%EXE_PATH%" 2>nul | findstr /i "DLL Name:" > "%TEMP_DEPS%.raw"
if not exist "%TEMP_DEPS%.raw" (
    call :print_error "无法分析可执行文件依赖"
    exit /b 1
)

REM 处理 objdump 输出
for /f "tokens=3" %%i in (%TEMP_DEPS%.raw) do (
    call :find_dll "%%i"
)

if exist "%TEMP_DEPS%.raw" del "%TEMP_DEPS%.raw"
goto :eof

REM 使用 dumpbin 获取依赖 (MSVC)
:get_deps_dumpbin
dumpbin /dependents "%EXE_PATH%" 2>nul | findstr /i ".dll" > "%TEMP_DEPS%.raw"
if not exist "%TEMP_DEPS%.raw" (
    call :print_error "无法分析可执行文件依赖"
    exit /b 1
)

REM 处理 dumpbin 输出
for /f "tokens=1" %%i in (%TEMP_DEPS%.raw) do (
    set "dll_name=%%i"
    if "!dll_name:~-4!"==".dll" (
        call :find_dll "!dll_name!"
    )
)

if exist "%TEMP_DEPS%.raw" del "%TEMP_DEPS%.raw"
goto :eof

REM 查找 DLL 文件的完整路径
:find_dll
set "dll_name=%~1"
set "dll_found=false"

REM 搜索路径列表
set "search_paths=.;%PATH%"
if defined MINGW_PREFIX set "search_paths=%MINGW_PREFIX%\bin;!search_paths!"
if exist "C:\msys64\mingw64\bin" set "search_paths=C:\msys64\mingw64\bin;!search_paths!"
if exist "C:\mingw64\bin" set "search_paths=C:\mingw64\bin;!search_paths!"

for %%p in ("%search_paths:;=";"%) do (
    if exist "%%~p\%dll_name%" (
        echo %%~p\%dll_name%>> "%TEMP_DEPS%"
        set "dll_found=true"
        goto :find_dll_done
    )
)

:find_dll_done
if "%dll_found%"=="false" (
    if "%VERBOSE%"=="true" (
        call :print_warning "未找到 DLL: %dll_name%"
    )
)
goto :eof

REM 复制 DLL 文件
:copy_dll
set "src_dll=%~1"
for %%f in ("%src_dll%") do set "dll_name=%%~nxf"
set "dst_path=%DLL_DIR%\%dll_name%"

REM 检查是否为系统 DLL
call :is_system_dll "%dll_name%"
if "%IS_SYSTEM_DLL%"=="true" (
    if "%VERBOSE%"=="true" (
        call :print_warning "跳过系统 DLL: %dll_name%"
    )
    set /a "skipped_count+=1"
    goto :eof
)

REM 检查目标文件是否已存在
if exist "%dst_path%" (
    if "%FORCE%"=="false" (
        if "%VERBOSE%"=="true" (
            call :print_warning "文件已存在，跳过: %dll_name%"
        )
        set /a "skipped_count+=1"
        goto :eof
    )
)

if "%DRY_RUN%"=="true" (
    echo [DRY RUN] 将复制: %src_dll% -^> %dst_path%
) else (
    copy "%src_dll%" "%dst_path%" >nul 2>&1
    if errorlevel 1 (
        call :print_error "复制失败: %src_dll%"
        set /a "skipped_count+=1"
    ) else (
        if "%VERBOSE%"=="true" (
            call :print_success "已复制: %dll_name%"
        )
        set /a "copied_count+=1"
    )
)
goto :eof

REM 检查是否为系统 DLL
:is_system_dll
set "dll_name=%~1"
set "IS_SYSTEM_DLL=false"

REM 系统 DLL 列表
set "system_dlls=kernel32.dll user32.dll gdi32.dll winspool.drv comdlg32.dll advapi32.dll shell32.dll ole32.dll oleaut32.dll uuid.dll winmm.dll version.dll ws2_32.dll wsock32.dll netapi32.dll ntdll.dll msvcrt.dll secur32.dll rpcrt4.dll"

for %%s in (%system_dlls%) do (
    if /i "%dll_name%"=="%%s" (
        set "IS_SYSTEM_DLL=true"
        goto :eof
    )
)
goto :eof

REM 打印函数
:print_info
echo %BLUE%ℹ️  %~1%NC%
goto :eof

:print_success
echo %GREEN%✅ %~1%NC%
goto :eof

:print_warning
echo %YELLOW%⚠️  %~1%NC%
goto :eof

:print_error
echo %RED%❌ %~1%NC%
goto :eof

REM 显示帮助
:show_help
echo.
echo 用法: %~nx0 [选项] ^<可执行文件路径^> [输出目录]
echo.
echo 描述:
echo     复制 Windows 环境下可执行文件的依赖 DLL 到指定目录
echo.
echo 参数:
echo     ^<可执行文件路径^>    要分析的可执行文件路径
echo     [输出目录]          DLL 输出目录 (默认: .\dlls)
echo.
echo 选项:
echo     -h, --help         显示此帮助信息
echo     -v, --verbose      详细输出模式
echo     -f, --force        强制覆盖已存在的文件
echo     --dry-run          仅显示将要复制的文件，不实际复制
echo.
echo 示例:
echo     %~nx0 build\bin\mini_cache_server.exe
echo     %~nx0 -v -f build\bin\mini_cache_cli.exe .\output\dlls
echo     %~nx0 --dry-run build\bin\mini_cache_server.exe
echo.
echo 注意:
echo     - 需要安装 Visual Studio (dumpbin) 或 MinGW (objdump)
echo     - 自动排除常见的系统 DLL
echo     - 支持 MSYS2/MinGW64 和 Visual Studio 环境
echo.
exit /b 0