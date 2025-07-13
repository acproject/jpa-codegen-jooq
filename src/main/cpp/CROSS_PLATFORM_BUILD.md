# 跨平台编译指南

本文档提供了在不同平台上编译 MiniCache C++ 项目的详细指南。

## 支持的平台

- **Windows**: MSVC 2019+, MinGW-w64, Msys2-MinGW64
- **Linux**: GCC 7+, Clang 6+
- **macOS**: Clang (Xcode Command Line Tools)
- **FreeBSD**: GCC/Clang

## 编译要求

### 通用要求
- C++17 或更高版本
- CMake 3.15+

### Windows 特定要求
- Winsock2 库 (通常已包含在系统中)
- 对于 MinGW: 确保使用 MinGW-w64 而不是旧版本的 MinGW

### Unix/Linux 特定要求
- POSIX 兼容系统
- pthread 库
- 网络库 (通常已包含在系统中)

## 编译步骤

### Windows (MSVC)
```cmd
mkdir build
cd build
cmake .. -G "Visual Studio 16 2019" -A x64
cmake --build . --config Release
```

### Windows (MinGW/Msys2)
```bash
mkdir build
cd build
cmake .. -G "MinGW Makefiles"
cmake --build .
```

### Linux/macOS
```bash
mkdir build
cd build
cmake ..
make -j$(nproc)
```

## 常见问题解决

### 1. ssize_t 重复定义错误
**问题**: 在 Msys2-MinGW64 环境中出现 `conflicting declaration` 错误

**解决方案**: 项目已经包含了条件编译保护，确保使用最新版本的代码。

### 2. fcntl 函数未声明
**问题**: 在某些 MinGW 环境中 `fcntl` 函数未声明

**解决方案**: 项目已经添加了平台特定的头文件包含和条件编译。

### 3. 套接字函数兼容性
**问题**: Windows 和 Unix 系统的套接字 API 差异

**解决方案**: 使用项目提供的跨平台宏定义。

## 最佳实践

### 1. 使用统一的头文件
```cpp
#include "common/platform_compat.h"
```

### 2. 使用跨平台宏
```cpp
// 套接字操作
socket_t sock = socket(AF_INET, SOCK_STREAM, 0);
if (IS_INVALID_SOCKET(sock)) {
    // 错误处理
}

// 关闭套接字
CLOSE_SOCKET(sock);

// 网络 I/O
int result = SOCKET_RECV(sock, buffer, sizeof(buffer));
if (IS_SOCKET_ERROR(result)) {
    if (IS_WOULD_BLOCK_ERROR()) {
        // 非阻塞操作，稍后重试
    } else {
        // 真正的错误
    }
}
```

### 3. 错误处理
```cpp
// 获取错误码
int error = GET_SOCKET_ERROR();

// 检查是否为 "would block" 错误
if (IS_WOULD_BLOCK_ERROR()) {
    // 处理非阻塞操作
}
```

## 调试技巧

### 1. 平台检测
```cpp
#include "common/platform_compat.h"
std::cout << "Running on: " << PLATFORM_NAME << std::endl;
```

### 2. 编译时检查
```cpp
#ifdef _WIN32
    std::cout << "Windows specific code" << std::endl;
#else
    std::cout << "Unix/Linux specific code" << std::endl;
#endif
```

### 3. 运行时测试
使用提供的 `compatibility_test.cpp` 来验证平台兼容性：
```bash
g++ -o test compatibility_test.cpp -I../common
./test
```

## 贡献指南

在添加新的平台特定代码时，请遵循以下原则：

1. **优先使用跨平台解决方案**
2. **必要时使用条件编译**
3. **更新相应的测试用例**
4. **更新文档**

## 支持的编译器版本

| 平台 | 编译器 | 最低版本 | 推荐版本 |
|------|--------|----------|----------|
| Windows | MSVC | 2019 | 2022 |
| Windows | MinGW-w64 | 8.0 | 最新 |
| Linux | GCC | 7.0 | 11+ |
| Linux | Clang | 6.0 | 13+ |
| macOS | Clang | 10.0 | 最新 |

## 联系方式

如果遇到平台特定的编译问题，请提供以下信息：
- 操作系统版本
- 编译器版本
- CMake 版本
- 完整的错误信息
- 编译命令