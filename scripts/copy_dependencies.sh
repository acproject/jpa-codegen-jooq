#!/bin/bash

# MiniCache 依赖 DLL 复制脚本
# 用于在 Msys2-MinGW 环境下复制可执行文件的依赖库
# 作者: MiniCache Project
# 版本: 1.0.0

set -e  # 遇到错误时退出

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 打印带颜色的消息
print_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

# 显示使用帮助
show_help() {
    cat << EOF
用法: $0 [选项] <可执行文件路径> [输出目录]

描述:
    复制 Msys2-MinGW 环境下可执行文件的依赖 DLL 到指定目录

参数:
    <可执行文件路径>    要分析的可执行文件路径
    [输出目录]          DLL 输出目录 (默认: ./dlls)

选项:
    -h, --help         显示此帮助信息
    -v, --verbose      详细输出模式
    -f, --force        强制覆盖已存在的文件
    -r, --recursive    递归分析依赖的依赖
    --dry-run          仅显示将要复制的文件，不实际复制
    --exclude-system   排除系统 DLL (kernel32.dll, user32.dll 等)
    --include-paths    额外包含的路径 (用逗号分隔)

示例:
    $0 build/bin/mini_cache_server.exe
    $0 -v -f build/bin/mini_cache_cli.exe ./output/dlls
    $0 --dry-run --exclude-system build/bin/mini_cache_server.exe
    $0 --include-paths "/custom/lib,/another/path" app.exe

EOF
}

# 默认参数
VERBOSE=false
FORCE=false
RECURSIVE=false
DRY_RUN=false
EXCLUDE_SYSTEM=false
INCLUDE_PATHS=""
DLL_DIR="./dlls"

# 系统 DLL 列表 (通常不需要分发)
SYSTEM_DLLS=(
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
)

# 解析命令行参数
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
        -f|--force)
            FORCE=true
            shift
            ;;
        -r|--recursive)
            RECURSIVE=true
            shift
            ;;
        --dry-run)
            DRY_RUN=true
            shift
            ;;
        --exclude-system)
            EXCLUDE_SYSTEM=true
            shift
            ;;
        --include-paths)
            INCLUDE_PATHS="$2"
            shift 2
            ;;
        -*)
            print_error "未知选项: $1"
            show_help
            exit 1
            ;;
        *)
            if [[ -z "$EXE_PATH" ]]; then
                EXE_PATH="$1"
            elif [[ -z "$DLL_DIR_ARG" ]]; then
                DLL_DIR_ARG="$1"
            else
                print_error "过多的参数: $1"
                show_help
                exit 1
            fi
            shift
            ;;
    esac
done

# 检查必需参数
if [[ -z "$EXE_PATH" ]]; then
    print_error "缺少可执行文件路径参数"
    show_help
    exit 1
fi

# 使用用户指定的输出目录或默认目录
if [[ -n "$DLL_DIR_ARG" ]]; then
    DLL_DIR="$DLL_DIR_ARG"
fi

# 检查可执行文件是否存在
if [[ ! -f "$EXE_PATH" ]]; then
    print_error "可执行文件不存在: $EXE_PATH"
    exit 1
fi

# 检查 ldd 命令是否可用
if ! command -v ldd &> /dev/null; then
    print_error "ldd 命令未找到，请确保在 Msys2-MinGW 环境中运行"
    exit 1
fi

# 创建输出目录
if [[ "$DRY_RUN" == "false" ]]; then
    if [[ ! -d "$DLL_DIR" ]]; then
        mkdir -p "$DLL_DIR"
        print_info "创建输出目录: $DLL_DIR"
    fi
fi

# 检查 DLL 是否为系统 DLL
is_system_dll() {
    local dll_name=$(basename "$1")
    for sys_dll in "${SYSTEM_DLLS[@]}"; do
        if [[ "${dll_name,,}" == "${sys_dll,,}" ]]; then
            return 0
        fi
    done
    return 1
}

# 获取依赖 DLL 列表
get_dependencies() {
    local exe_file="$1"
    local processed_files=("$exe_file")
    local all_dlls=()
    
    print_info "分析 $exe_file 的依赖关系..."
    
    # 构建搜索路径
    local search_paths="/mingw64|/usr/bin"
    if [[ -n "$INCLUDE_PATHS" ]]; then
        IFS=',' read -ra PATHS <<< "$INCLUDE_PATHS"
        for path in "${PATHS[@]}"; do
            search_paths="$search_paths|$path"
        done
    fi
    
    # 获取直接依赖
    local dlls
    dlls=$(ldd "$exe_file" 2>/dev/null | grep -E "$search_paths" | awk '{print $3}' | sort -u)
    
    if [[ -z "$dlls" ]]; then
        print_warning "未找到匹配的依赖 DLL"
        return
    fi
    
    for dll in $dlls; do
        if [[ -f "$dll" ]]; then
            all_dlls+=("$dll")
            
            # 递归分析依赖的依赖
            if [[ "$RECURSIVE" == "true" ]]; then
                local dll_deps
                dll_deps=$(ldd "$dll" 2>/dev/null | grep -E "$search_paths" | awk '{print $3}' | sort -u)
                for dep_dll in $dll_deps; do
                    if [[ -f "$dep_dll" ]] && [[ ! " ${all_dlls[@]} " =~ " ${dep_dll} " ]]; then
                        all_dlls+=("$dep_dll")
                    fi
                done
            fi
        fi
    done
    
    # 去重并输出
    printf '%s\n' "${all_dlls[@]}" | sort -u
}

# 复制 DLL 文件
copy_dll() {
    local src_dll="$1"
    local dst_dir="$2"
    local dll_name=$(basename "$src_dll")
    local dst_path="$dst_dir/$dll_name"
    
    # 检查是否排除系统 DLL
    if [[ "$EXCLUDE_SYSTEM" == "true" ]] && is_system_dll "$src_dll"; then
        if [[ "$VERBOSE" == "true" ]]; then
            print_warning "跳过系统 DLL: $dll_name"
        fi
        return 0
    fi
    
    # 检查目标文件是否已存在
    if [[ -f "$dst_path" ]] && [[ "$FORCE" == "false" ]]; then
        if [[ "$VERBOSE" == "true" ]]; then
            print_warning "文件已存在，跳过: $dll_name"
        fi
        return 0
    fi
    
    if [[ "$DRY_RUN" == "true" ]]; then
        echo "[DRY RUN] 将复制: $src_dll -> $dst_path"
    else
        if cp "$src_dll" "$dst_path"; then
            if [[ "$VERBOSE" == "true" ]]; then
                print_success "已复制: $dll_name"
            fi
        else
            print_error "复制失败: $src_dll"
            return 1
        fi
    fi
}

# 主函数
main() {
    print_info "开始分析可执行文件: $EXE_PATH"
    print_info "输出目录: $DLL_DIR"
    
    if [[ "$VERBOSE" == "true" ]]; then
        print_info "详细模式: 开启"
        print_info "强制覆盖: $FORCE"
        print_info "递归分析: $RECURSIVE"
        print_info "排除系统DLL: $EXCLUDE_SYSTEM"
        if [[ -n "$INCLUDE_PATHS" ]]; then
            print_info "额外路径: $INCLUDE_PATHS"
        fi
    fi
    
    # 获取依赖列表
    local dependencies
    dependencies=$(get_dependencies "$EXE_PATH")
    
    if [[ -z "$dependencies" ]]; then
        print_warning "未找到需要复制的依赖 DLL"
        exit 0
    fi
    
    # 统计信息
    local total_count=0
    local copied_count=0
    local skipped_count=0
    
    # 复制每个 DLL
    while IFS= read -r dll; do
        if [[ -n "$dll" ]]; then
            ((total_count++))
            if copy_dll "$dll" "$DLL_DIR"; then
                ((copied_count++))
            else
                ((skipped_count++))
            fi
        fi
    done <<< "$dependencies"
    
    # 输出统计信息
    echo
    print_success "依赖分析完成!"
    print_info "总计发现: $total_count 个依赖文件"
    
    if [[ "$DRY_RUN" == "true" ]]; then
        print_info "模拟模式: 将复制 $total_count 个文件"
    else
        print_info "成功复制: $copied_count 个文件"
        if [[ $skipped_count -gt 0 ]]; then
            print_info "跳过文件: $skipped_count 个"
        fi
        print_success "所有依赖 DLL 已复制到: $DLL_DIR"
    fi
    
    # 显示复制的文件列表
    if [[ "$VERBOSE" == "true" ]] || [[ "$DRY_RUN" == "true" ]]; then
        echo
        print_info "依赖文件列表:"
        while IFS= read -r dll; do
            if [[ -n "$dll" ]]; then
                local dll_name=$(basename "$dll")
                if [[ "$EXCLUDE_SYSTEM" == "true" ]] && is_system_dll "$dll"; then
                    echo "  - $dll_name (系统DLL,已跳过)"
                else
                    echo "  - $dll_name"
                fi
            fi
        done <<< "$dependencies"
    fi
}

# 信号处理
trap 'print_error "脚本被中断"; exit 130' INT TERM

# 运行主函数
main

# 退出码
if [[ "$DRY_RUN" == "true" ]]; then
    exit 0
else
    exit $?
fi