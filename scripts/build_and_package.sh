#!/bin/bash

# =============================================================================
# MiniCache 构建和打包脚本
# Build and Package Script for MiniCache
# =============================================================================

set -euo pipefail

# 脚本目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 默认配置
BUILD_TYPE="Release"
BUILD_DIR="build"
CLEAN_BUILD=false
COPY_DEPS=true
CREATE_PACKAGE=false
VERBOSE=false
JOBS=$(nproc 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || echo "4")

# 日志函数
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1" >&2
}

log_verbose() {
    if [[ "$VERBOSE" == "true" ]]; then
        echo -e "${BLUE}[VERBOSE]${NC} $1"
    fi
}

# 显示帮助信息
show_help() {
    cat << EOF
用法: $0 [选项]

选项:
  -h, --help              显示此帮助信息
  -v, --verbose           详细输出
  -c, --clean             清理构建（删除构建目录）
  -t, --type <类型>       构建类型 (Debug|Release|RelWithDebInfo|MinSizeRel)
  -d, --dir <目录>        构建目录（默认: build）
  -j, --jobs <数量>       并行构建任务数（默认: 自动检测）
  --no-deps               不复制依赖文件
  -p, --package           创建安装包
  --msys2                 Msys2-MinGW 环境优化

示例:
  $0                      # 默认 Release 构建
  $0 -c -t Debug          # 清理并构建 Debug 版本
  $0 -v -p                # 详细输出并创建安装包
  $0 --msys2 -p          # Msys2 环境构建并打包

EOF
}

# 检测环境
detect_environment() {
    log_info "检测构建环境..."
    
    # 检测操作系统
    if [[ "$OSTYPE" == "msys" ]] || [[ "$OSTYPE" == "cygwin" ]]; then
        log_info "检测到 Windows (Msys2/Cygwin) 环境"
        export PLATFORM="windows"
    elif [[ "$OSTYPE" == "darwin"* ]]; then
        log_info "检测到 macOS 环境"
        export PLATFORM="macos"
    elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
        log_info "检测到 Linux 环境"
        export PLATFORM="linux"
    else
        log_warning "未知操作系统: $OSTYPE"
        export PLATFORM="unknown"
    fi
    
    # 检测 Msys2 环境
    if [[ -n "${MSYSTEM:-}" ]]; then
        log_info "检测到 Msys2 环境: $MSYSTEM"
        export IS_MSYS2=true
    else
        export IS_MSYS2=false
    fi
    
    # 检测编译器
    if command -v gcc >/dev/null 2>&1; then
        local gcc_version
        gcc_version=$(gcc --version | head -n1)
        log_verbose "GCC: $gcc_version"
    fi
    
    if command -v clang >/dev/null 2>&1; then
        local clang_version
        clang_version=$(clang --version | head -n1)
        log_verbose "Clang: $clang_version"
    fi
    
    # 检测 CMake
    if ! command -v cmake >/dev/null 2>&1; then
        log_error "未找到 CMake，请先安装 CMake"
        exit 1
    fi
    
    local cmake_version
    cmake_version=$(cmake --version | head -n1)
    log_verbose "$cmake_version"
}

# 清理构建目录
clean_build() {
    if [[ -d "$BUILD_DIR" ]]; then
        log_info "清理构建目录: $BUILD_DIR"
        rm -rf "$BUILD_DIR"
    fi
}

# 配置构建
configure_build() {
    log_info "配置构建..."
    
    # 创建构建目录
    mkdir -p "$BUILD_DIR"
    cd "$BUILD_DIR"
    
    # CMake 配置选项
    local cmake_args=(
        "-DCMAKE_BUILD_TYPE=$BUILD_TYPE"
        "-DCMAKE_EXPORT_COMPILE_COMMANDS=ON"
    )
    
    # 平台特定配置
    if [[ "$PLATFORM" == "windows" ]] || [[ "$IS_MSYS2" == "true" ]]; then
        cmake_args+=(
            "-DAUTO_COPY_DEPENDENCIES=$COPY_DEPS"
        )
        
        # Msys2-MinGW 特定配置
        if [[ "$IS_MSYS2" == "true" ]]; then
            cmake_args+=(
                "-G" "MSYS Makefiles"
            )
        fi
    fi
    
    # 详细输出
    if [[ "$VERBOSE" == "true" ]]; then
        cmake_args+=("--debug-output")
    fi
    
    log_verbose "CMake 参数: ${cmake_args[*]}"
    
    # 运行 CMake 配置
    cmake "${cmake_args[@]}" "$PROJECT_ROOT"
}

# 构建项目
build_project() {
    log_info "构建项目..."
    
    local build_args=("--build" "." "--config" "$BUILD_TYPE")
    
    # 并行构建
    if [[ $JOBS -gt 1 ]]; then
        build_args+=("--parallel" "$JOBS")
    fi
    
    # 详细输出
    if [[ "$VERBOSE" == "true" ]]; then
        build_args+=("--verbose")
    fi
    
    log_verbose "构建参数: ${build_args[*]}"
    
    # 运行构建
    cmake "${build_args[@]}"
}

# 复制依赖
copy_dependencies() {
    if [[ "$COPY_DEPS" != "true" ]]; then
        return 0
    fi
    
    log_info "复制依赖文件..."
    
    # 查找可执行文件
    local executables=()
    
    # 根据平台查找可执行文件
    if [[ "$PLATFORM" == "windows" ]] || [[ "$IS_MSYS2" == "true" ]]; then
        mapfile -t executables < <(find . -name "*.exe" -type f)
    else
        # Unix 系统，查找可执行文件（没有扩展名）
        mapfile -t executables < <(find . -type f -executable ! -name "*.so" ! -name "*.dylib" ! -name "*.dll")
    fi
    
    if [[ ${#executables[@]} -eq 0 ]]; then
        log_warning "未找到可执行文件"
        return 0
    fi
    
    # 为每个可执行文件复制依赖
    for exe in "${executables[@]}"; do
        local exe_name
        exe_name=$(basename "$exe")
        log_info "处理可执行文件: $exe_name"
        
        # 使用适当的脚本复制依赖
        if [[ "$IS_MSYS2" == "true" ]]; then
            # 使用 Shell 脚本
            local copy_args=()
            [[ "$VERBOSE" == "true" ]] && copy_args+=("-v")
            
            "$SCRIPT_DIR/copy_dependencies.sh" "${copy_args[@]}" "$exe" "dlls"
        elif [[ "$PLATFORM" == "windows" ]]; then
            # 使用批处理脚本
            local copy_args=()
            [[ "$VERBOSE" == "true" ]] && copy_args+=("/v")
            
            "$SCRIPT_DIR/copy_dependencies.bat" "${copy_args[@]}" "$exe" "dlls"
        else
            # Unix 系统，使用 CMake 脚本
            cmake \
                -DTARGET_EXECUTABLE="$exe" \
                -DOUTPUT_DIRECTORY="dlls" \
                -DVERBOSE_OUTPUT="$VERBOSE" \
                -P "$SCRIPT_DIR/copy_dependencies.cmake"
        fi
    done
}

# 创建安装包
create_package() {
    if [[ "$CREATE_PACKAGE" != "true" ]]; then
        return 0
    fi
    
    log_info "创建安装包..."
    
    # 检查是否配置了 CPack
    if [[ ! -f "CPackConfig.cmake" ]]; then
        log_warning "未找到 CPack 配置，跳过打包"
        return 0
    fi
    
    # 创建包
    local cpack_args=()
    
    if [[ "$VERBOSE" == "true" ]]; then
        cpack_args+=("--verbose")
    fi
    
    cpack "${cpack_args[@]}"
    
    # 显示生成的包
    log_success "生成的安装包:"
    find . -name "*.zip" -o -name "*.tar.gz" -o -name "*.deb" -o -name "*.rpm" -o -name "*.dmg" -o -name "*.exe" | while read -r package; do
        log_info "  $(basename "$package")"
    done
}

# 显示构建摘要
show_summary() {
    log_success "构建完成！"
    
    echo
    log_info "构建摘要:"
    log_info "  构建类型: $BUILD_TYPE"
    log_info "  构建目录: $BUILD_DIR"
    log_info "  平台: $PLATFORM"
    [[ "$IS_MSYS2" == "true" ]] && log_info "  Msys2 环境: $MSYSTEM"
    
    # 显示生成的文件
    echo
    log_info "生成的可执行文件:"
    if [[ "$PLATFORM" == "windows" ]] || [[ "$IS_MSYS2" == "true" ]]; then
        find "$BUILD_DIR" -name "*.exe" -type f | while read -r exe; do
            log_info "  $(basename "$exe")"
        done
    else
        find "$BUILD_DIR" -type f -executable ! -name "*.so" ! -name "*.dylib" ! -name "*.dll" | while read -r exe; do
            log_info "  $(basename "$exe")"
        done
    fi
    
    # 显示依赖目录
    if [[ "$COPY_DEPS" == "true" ]] && [[ -d "$BUILD_DIR/dlls" ]]; then
        echo
        log_info "依赖文件目录: $BUILD_DIR/dlls"
        local dll_count
        dll_count=$(find "$BUILD_DIR/dlls" -type f | wc -l)
        log_info "  包含 $dll_count 个依赖文件"
    fi
    
    echo
    log_info "运行测试:"
    log_info "  cd $BUILD_DIR"
    if [[ "$PLATFORM" == "windows" ]] || [[ "$IS_MSYS2" == "true" ]]; then
        log_info "  ./mini_cache_server.exe"
        log_info "  ./mini_cache_cli.exe"
    else
        log_info "  ./mini_cache_server"
        log_info "  ./mini_cache_cli"
    fi
}

# 解析命令行参数
parse_arguments() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            -h|--help)
                show_help
                exit 0
                ;;
            -v|--verbose)
                VERBOSE=true
                shift
                ;;
            -c|--clean)
                CLEAN_BUILD=true
                shift
                ;;
            -t|--type)
                if [[ -n "${2:-}" ]]; then
                    BUILD_TYPE="$2"
                    shift 2
                else
                    log_error "选项 $1 需要一个参数"
                    exit 1
                fi
                ;;
            -d|--dir)
                if [[ -n "${2:-}" ]]; then
                    BUILD_DIR="$2"
                    shift 2
                else
                    log_error "选项 $1 需要一个参数"
                    exit 1
                fi
                ;;
            -j|--jobs)
                if [[ -n "${2:-}" ]] && [[ "$2" =~ ^[0-9]+$ ]]; then
                    JOBS="$2"
                    shift 2
                else
                    log_error "选项 $1 需要一个数字参数"
                    exit 1
                fi
                ;;
            --no-deps)
                COPY_DEPS=false
                shift
                ;;
            -p|--package)
                CREATE_PACKAGE=true
                shift
                ;;
            --msys2)
                # Msys2 优化设置
                export IS_MSYS2=true
                COPY_DEPS=true
                shift
                ;;
            -*)
                log_error "未知选项: $1"
                show_help
                exit 1
                ;;
            *)
                log_error "未知参数: $1"
                show_help
                exit 1
                ;;
        esac
    done
}

# 主函数
main() {
    # 解析参数
    parse_arguments "$@"
    
    # 切换到项目根目录
    cd "$PROJECT_ROOT"
    
    log_info "MiniCache 构建脚本"
    log_info "项目根目录: $PROJECT_ROOT"
    
    # 检测环境
    detect_environment
    
    # 清理构建（如果需要）
    if [[ "$CLEAN_BUILD" == "true" ]]; then
        clean_build
    fi
    
    # 配置构建
    configure_build
    
    # 构建项目
    build_project
    
    # 复制依赖
    copy_dependencies
    
    # 创建安装包
    create_package
    
    # 显示摘要
    show_summary
}

# 脚本入口点
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi