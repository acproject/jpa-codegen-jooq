# MiniCache 依赖管理指南

本文档详细说明了 MiniCache 项目中的依赖管理工具和脚本的使用方法。

## 概述

MiniCache 项目提供了多种工具来自动化处理可执行文件的依赖库复制，特别是在 Windows 平台上的 DLL 依赖管理。这些工具支持多种环境和使用场景。

## 工具列表

### 1. Shell 脚本 (Msys2-MinGW)

**文件**: `scripts/copy_dependencies.sh`

适用于 Msys2-MinGW 环境，使用 `ldd` 命令分析依赖。

#### 基本用法

```bash
# 基本使用
./scripts/copy_dependencies.sh mini_cache_server.exe

# 指定输出目录
./scripts/copy_dependencies.sh mini_cache_server.exe output_dlls

# 详细输出模式
./scripts/copy_dependencies.sh -v mini_cache_server.exe

# 干运行模式（不实际复制文件）
./scripts/copy_dependencies.sh --dry-run mini_cache_server.exe

# 强制覆盖已存在的文件
./scripts/copy_dependencies.sh -f mini_cache_server.exe

# 添加额外搜索路径
./scripts/copy_dependencies.sh --path /custom/path mini_cache_server.exe
```

#### 选项说明

- `-h, --help`: 显示帮助信息
- `-v, --verbose`: 详细输出模式
- `-n, --dry-run`: 干运行模式，不实际复制文件
- `-f, --force`: 强制覆盖已存在的文件
- `-r, --recursive`: 递归分析依赖（默认启用）
- `--no-recursive`: 禁用递归分析
- `-s, --exclude-system`: 排除系统 DLL（默认启用）
- `--no-exclude-system`: 包含系统 DLL
- `-p, --path <路径>`: 添加额外的搜索路径

### 2. Windows 批处理脚本

**文件**: `scripts/copy_dependencies.bat`

适用于原生 Windows 环境，支持 Visual Studio 和 MinGW 工具链。

#### 基本用法

```cmd
REM 基本使用
scripts\copy_dependencies.bat mini_cache_server.exe

REM 指定输出目录
scripts\copy_dependencies.bat mini_cache_server.exe output_dlls

REM 详细输出模式
scripts\copy_dependencies.bat /v mini_cache_server.exe

REM 干运行模式
scripts\copy_dependencies.bat /n mini_cache_server.exe

REM 强制覆盖
scripts\copy_dependencies.bat /f mini_cache_server.exe
```

#### 选项说明

- `/h, /help, /?`: 显示帮助信息
- `/v, /verbose`: 详细输出模式
- `/n, /dry-run`: 干运行模式
- `/f, /force`: 强制覆盖已存在的文件
- `/s, /exclude-system`: 排除系统 DLL（默认启用）

### 3. CMake 脚本

**文件**: `scripts/copy_dependencies.cmake`

集成到 CMake 构建系统中，支持自动化依赖复制。

#### 在 CMakeLists.txt 中使用

```cmake
# 包含依赖复制脚本
include(${CMAKE_SOURCE_DIR}/scripts/copy_dependencies.cmake)

# 为可执行文件创建依赖复制目标
copy_target_dependencies(mini_cache_server)
copy_target_dependencies(mini_cache_cli)

# 创建总的依赖复制目标
add_custom_target(copy_all_dependencies
    DEPENDS copy_mini_cache_server_dependencies copy_mini_cache_cli_dependencies
    COMMENT "复制所有可执行文件的依赖"
)
```

#### 独立使用

```bash
# 使用 CMake 脚本模式
cmake -DTARGET_EXECUTABLE="path/to/executable.exe" \
      -DOUTPUT_DIRECTORY="dlls" \
      -DVERBOSE_OUTPUT=ON \
      -DFORCE_COPY=OFF \
      -DEXCLUDE_SYSTEM_DLLS=ON \
      -P scripts/copy_dependencies.cmake
```

## 构建系统集成

### 自动依赖复制

在 `CMakeLists.txt` 中已经配置了依赖复制功能：

```cmake
# 构建时自动复制依赖（可选）
if(AUTO_COPY_DEPENDENCIES)
    add_dependencies(mini_cache_server copy_mini_cache_server_dependencies)
    add_dependencies(mini_cache_cli copy_mini_cache_cli_dependencies)
endif()
```

要启用自动复制，在配置时设置：

```bash
cmake -DAUTO_COPY_DEPENDENCIES=ON ..
```

### 手动复制依赖

```bash
# 复制特定目标的依赖
make copy_mini_cache_server_dependencies

# 复制所有依赖
make copy_all_dependencies

# 复制脚本到构建目录
make copy_deps_script
```

## 配置选项

### 环境变量

- `MINGW_PREFIX`: MinGW 安装前缀路径
- `MSYSTEM`: Msys2 系统类型（如 MINGW64、MINGW32）
- `PATH`: 系统路径，用于查找 DLL
- `LD_LIBRARY_PATH`: Unix 系统的库搜索路径

### CMake 变量

- `AUTO_COPY_DEPENDENCIES`: 是否自动复制依赖（默认: OFF）
- `VERBOSE_OUTPUT`: 详细输出模式（默认: OFF）
- `FORCE_COPY`: 强制覆盖已存在文件（默认: OFF）
- `EXCLUDE_SYSTEM_DLLS`: 排除系统 DLL（默认: ON）

## 系统 DLL 排除列表

以下系统 DLL 默认会被排除，不会复制到输出目录：

- `kernel32.dll`, `user32.dll`, `gdi32.dll`
- `advapi32.dll`, `shell32.dll`, `ole32.dll`
- `ws2_32.dll`, `wsock32.dll`, `netapi32.dll`
- `ntdll.dll`, `msvcrt.dll`, `secur32.dll`
- `api-ms-win-*.dll`, `vcruntime*.dll`, `msvcp*.dll`
- 等等...

## 故障排除

### 常见问题

1. **找不到 ldd 命令**
   - 确保在 Msys2-MinGW 环境中运行
   - 检查 PATH 环境变量

2. **找不到 dumpbin 或 objdump**
   - Visual Studio: 确保安装了 MSVC 工具链
   - MinGW: 确保安装了 binutils 包

3. **DLL 未找到**
   - 检查 DLL 是否在系统 PATH 中
   - 使用 `-p` 选项添加额外搜索路径
   - 确保 DLL 文件确实存在

4. **权限错误**
   - 确保对输出目录有写权限
   - 在 Windows 上可能需要管理员权限

### 调试技巧

1. **使用详细输出模式**
   ```bash
   ./scripts/copy_dependencies.sh -v mini_cache_server.exe
   ```

2. **使用干运行模式**
   ```bash
   ./scripts/copy_dependencies.sh --dry-run mini_cache_server.exe
   ```

3. **手动检查依赖**
   ```bash
   # Msys2-MinGW
   ldd mini_cache_server.exe
   
   # Windows (Visual Studio)
   dumpbin /dependents mini_cache_server.exe
   
   # Windows (MinGW)
   objdump -p mini_cache_server.exe
   ```

## 最佳实践

### 1. 开发环境

- 使用 `--dry-run` 模式预览将要复制的文件
- 启用详细输出以了解复制过程
- 定期清理输出目录以避免过时的依赖

### 2. 构建环境

- 在 CI/CD 中使用 CMake 集成的依赖复制
- 设置 `AUTO_COPY_DEPENDENCIES=ON` 自动化流程
- 使用版本控制忽略 `dlls/` 目录

### 3. 分发环境

- 使用 CPack 集成依赖复制到安装包
- 验证所有必需的 DLL 都已包含
- 测试在干净的系统上的运行情况

## 示例工作流

### 开发阶段

```bash
# 1. 构建项目
mkdir build && cd build
cmake ..
make

# 2. 复制依赖（预览）
../scripts/copy_dependencies.sh --dry-run mini_cache_server.exe

# 3. 实际复制依赖
../scripts/copy_dependencies.sh -v mini_cache_server.exe

# 4. 测试运行
./mini_cache_server.exe
```

### 发布阶段

```bash
# 1. 配置发布构建
cmake -DCMAKE_BUILD_TYPE=Release -DAUTO_COPY_DEPENDENCIES=ON ..

# 2. 构建并自动复制依赖
make

# 3. 创建安装包
make package
```

## 扩展和自定义

### 添加自定义搜索路径

```bash
# Shell 脚本
./scripts/copy_dependencies.sh --path /custom/lib --path /another/path mini_cache_server.exe
```

### 修改系统 DLL 排除列表

编辑相应脚本文件中的 `SYSTEM_DLLS` 列表。

### 集成到其他构建系统

可以参考 CMake 脚本的实现，适配到其他构建系统如 Makefile、Ninja 等。

## 许可证

这些依赖管理工具遵循与 MiniCache 项目相同的许可证。