#pragma once

// 跨平台兼容性头文件
// 统一处理不同平台间的类型定义和函数差异

#ifdef _WIN32
    #include <winsock2.h>
    #include <ws2tcpip.h>
    #include <windows.h>
    #include <io.h>
    
    // 定义 ssize_t 类型（避免重复定义）
    #ifndef _SSIZE_T_DEFINED
        #ifndef _SSIZE_T_
            #ifndef __MINGW32__
                typedef SSIZE_T ssize_t;
            #elif !defined(_SSIZE_T_DEFINED_)
                typedef int ssize_t;
                #define _SSIZE_T_DEFINED_
            #endif
        #endif
    #endif
    
    // 套接字类型定义
    typedef SOCKET socket_t;
    #define INVALID_SOCKET_VALUE INVALID_SOCKET
    #define SOCKET_ERROR_VALUE SOCKET_ERROR
    #define CLOSE_SOCKET(s) closesocket(s)
    
    // 网络函数兼容性
    #define SOCKET_RECV(fd, buf, len) recv(fd, buf, len, 0)
    #define SOCKET_SEND(fd, buf, len) send(fd, buf, len, 0)
    #define GET_SOCKET_ERROR() WSAGetLastError()
    #define SOCKET_WOULD_BLOCK WSAEWOULDBLOCK
    
    // 文件操作兼容性
    #define PLATFORM_READ(fd, buf, count) _read(fd, buf, count)
    #define PLATFORM_WRITE(fd, buf, count) _write(fd, buf, count)
    #define PLATFORM_CLOSE(fd) _close(fd)
    
#else
    #include <sys/socket.h>
    #include <netinet/in.h>
    #include <arpa/inet.h>
    #include <unistd.h>
    #include <fcntl.h>
    #include <errno.h>
    
    // Unix/Linux 平台的类型定义
    typedef int socket_t;
    #define INVALID_SOCKET_VALUE (-1)
    #define SOCKET_ERROR_VALUE (-1)
    #define CLOSE_SOCKET(s) close(s)
    
    // 网络函数兼容性
    #define SOCKET_RECV(fd, buf, len) recv(fd, buf, len, 0)
    #define SOCKET_SEND(fd, buf, len) send(fd, buf, len, 0)
    #define GET_SOCKET_ERROR() errno
    #define SOCKET_WOULD_BLOCK EWOULDBLOCK
    
    // 文件操作兼容性
    #define PLATFORM_READ(fd, buf, count) read(fd, buf, count)
    #define PLATFORM_WRITE(fd, buf, count) write(fd, buf, count)
    #define PLATFORM_CLOSE(fd) close(fd)
    
#endif

// 通用的错误检查宏
#define IS_SOCKET_ERROR(result) ((result) == SOCKET_ERROR_VALUE)
#define IS_INVALID_SOCKET(sock) ((sock) == INVALID_SOCKET_VALUE)

// 非阻塞检查宏
#ifdef _WIN32
    #define IS_WOULD_BLOCK_ERROR() (GET_SOCKET_ERROR() == SOCKET_WOULD_BLOCK)
#else
    #define IS_WOULD_BLOCK_ERROR() (GET_SOCKET_ERROR() == EAGAIN || GET_SOCKET_ERROR() == EWOULDBLOCK)
#endif

// 平台信息宏
#ifdef _WIN32
    #define PLATFORM_NAME "Windows"
#elif defined(__linux__)
    #define PLATFORM_NAME "Linux"
#elif defined(__APPLE__)
    #define PLATFORM_NAME "macOS"
#elif defined(__FreeBSD__)
    #define PLATFORM_NAME "FreeBSD"
#else
    #define PLATFORM_NAME "Unknown"
#endif