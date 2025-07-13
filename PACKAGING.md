# MiniCache 打包指南

本文档介绍如何使用 CMake 和 CPack 为 MiniCache 项目创建安装包。

## 概述

MiniCache 项目现在支持使用 CPack 创建多种格式的安装包，包括：

- **Windows**: NSIS 安装程序 (.exe) 和 ZIP 压缩包
- **macOS**: DMG 磁盘映像和 TGZ 压缩包
- **Linux**: DEB 包、RPM 包和 TGZ 压缩包
- **源码包**: TGZ 和 ZIP 格式

## 构建和打包步骤

### 1. 配置和构建项目

```bash
# 创建构建目录
mkdir build
cd build

# 配置项目
cmake ..

# 构建项目
cmake --build . --config Release
```

### 2. 创建安装包

#### 创建所有支持的包格式
```bash
cpack
```

#### 创建特定格式的包
```bash
# Windows NSIS 安装程序
cpack -G NSIS

# ZIP 压缩包
cpack -G ZIP

# Linux DEB 包
cpack -G DEB

# Linux RPM 包
cpack -G RPM

# macOS DMG
cpack -G DragNDrop

# TGZ 压缩包
cpack -G TGZ
```

#### 创建源码包
```bash
cpack --config CPackSourceConfig.cmake
```

### 3. 创建特定组件的包

```bash
# 仅运行时组件
cpack -G ZIP -D CPACK_COMPONENTS_ALL="Runtime;Libraries"

# 开发包（包含头文件）
cpack -G ZIP -D CPACK_COMPONENTS_ALL="Runtime;Libraries;Development"

# 完整包（所有组件）
cpack -G ZIP -D CPACK_COMPONENTS_ALL="Runtime;Libraries;Development;Documentation;Examples;Configuration"
```

## 包组件说明

### Runtime（运行时文件）
- `mini_cache_server` - MiniCache 服务器可执行文件
- `mini_cache_cli` - MiniCache 客户端可执行文件
- **必需组件**

### Libraries（运行时库）
- 所有共享库文件 (.so/.dll/.dylib)
- **必需组件**

### Development（开发文件）
- 头文件 (.hpp/.h)
- 静态库文件（如果有）
- 依赖 Libraries 组件

### Documentation（文档）
- README 文件
- 许可证文件
- 其他文档

### Examples（示例）
- 示例配置文件
- 使用案例

### Configuration（配置文件）
- 默认配置文件
- 配置模板

## 平台特定说明

### Windows

- **NSIS 安装程序**: 创建标准的 Windows 安装程序，支持开始菜单快捷方式
- **要求**: 需要安装 NSIS (Nullsoft Scriptable Install System)
- **输出**: `MiniCache-1.0.0-win64.exe`

```bash
# 安装 NSIS (使用 Chocolatey)
choco install nsis

# 或下载并安装 NSIS
# https://nsis.sourceforge.io/Download
```

### macOS

- **DMG**: 创建 macOS 磁盘映像文件
- **输出**: `MiniCache-1.0.0-Darwin.dmg`

### Linux

#### Debian/Ubuntu (DEB 包)
- **输出**: `MiniCache-1.0.0-Linux-x86_64.deb`
- **安装**: `sudo dpkg -i MiniCache-1.0.0-Linux-x86_64.deb`
- **卸载**: `sudo apt remove minicache`

#### Red Hat/CentOS/Fedora (RPM 包)
- **输出**: `MiniCache-1.0.0-Linux-x86_64.rpm`
- **安装**: `sudo rpm -i MiniCache-1.0.0-Linux-x86_64.rpm`
- **卸载**: `sudo rpm -e MiniCache`

#### 系统服务
在 Linux 系统上，包会自动安装 systemd 服务文件：

```bash
# 启用服务
sudo systemctl enable minicache

# 启动服务
sudo systemctl start minicache

# 查看状态
sudo systemctl status minicache

# 查看日志
sudo journalctl -u minicache
```

## 自定义打包

### 修改版本号

在 `CMakeLists.txt` 中修改版本信息：

```cmake
set(CPACK_PACKAGE_VERSION_MAJOR "1")
set(CPACK_PACKAGE_VERSION_MINOR "0")
set(CPACK_PACKAGE_VERSION_PATCH "0")
```

### 修改包信息

```cmake
set(CPACK_PACKAGE_NAME "MiniCache")
set(CPACK_PACKAGE_VENDOR "Your Company")
set(CPACK_PACKAGE_CONTACT "your-email@example.com")
set(CPACK_PACKAGE_HOMEPAGE_URL "https://your-website.com")
```

### 添加自定义文件

```cmake
# 安装额外文件
install(FILES your_file.txt
    DESTINATION share/minicache
    COMPONENT Documentation
)
```

## 故障排除

### 常见问题

1. **NSIS 未找到 (Windows)**
   ```
   CPack Error: Cannot find NSIS registry value
   ```
   **解决方案**: 安装 NSIS 并确保它在 PATH 中

2. **权限错误 (Linux)**
   ```
   CPack Error: Problem running install command
   ```
   **解决方案**: 确保有足够的权限写入目标目录

3. **依赖库缺失**
   ```
   error while loading shared libraries
   ```
   **解决方案**: 确保所有依赖库都包含在包中或系统中已安装

### 调试打包过程

```bash
# 启用详细输出
cpack --verbose

# 查看 CPack 配置
cpack --help

# 列出所有生成器
cpack --help | grep "Generators"
```

## 高级配置

### 创建多架构包

```bash
# 为不同架构创建包
cmake -DCMAKE_SYSTEM_PROCESSOR=x86_64 ..
cpack

cmake -DCMAKE_SYSTEM_PROCESSOR=arm64 ..
cpack
```

### 自定义安装脚本

可以在 `CMakeLists.txt` 中添加安装前后脚本：

```cmake
set(CPACK_PRE_BUILD_SCRIPTS "${CMAKE_SOURCE_DIR}/scripts/pre_build.cmake")
set(CPACK_POST_BUILD_SCRIPTS "${CMAKE_SOURCE_DIR}/scripts/post_build.cmake")
```

## 发布流程建议

1. **测试构建**: 在目标平台上测试构建过程
2. **创建包**: 为所有目标平台创建安装包
3. **测试安装**: 在干净的系统上测试安装包
4. **验证功能**: 确保安装后的软件正常工作
5. **发布**: 将包上传到发布平台

## 相关文件

- `CMakeLists.txt` - 主要的 CMake 配置文件
- `scripts/minicache.service.in` - Linux systemd 服务模板
- `CROSS_PLATFORM_BUILD.md` - 跨平台构建指南

## 参考资源

- [CPack 官方文档](https://cmake.org/cmake/help/latest/module/CPack.html)
- [CMake 安装指南](https://cmake.org/cmake/help/latest/command/install.html)
- [NSIS 文档](https://nsis.sourceforge.io/Docs/)