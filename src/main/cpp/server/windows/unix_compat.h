//#pragma once
//
//#include <winsock2.h>
//#include <ws2tcpip.h>
//#include <windows.h>
//#include <io.h>
//
//// 定义 Unix/Linux 平台上的常量和类型
//#ifndef PLATFORM_WINDOWS
//#define PLATFORM_WINDOWS
//#endif
//
//// 定义 ssize_t 类型
//typedef int ssize_t;
//
//// 定义 fcntl 相关常量
//#define F_GETFL 3
//#define F_SETFL 4
//#define O_NONBLOCK 0x800
//
//// 定义 fcntl 函数
//inline int fcntl(SOCKET fd, int cmd, ...) {
//    if (cmd == F_GETFL) return 0;
//
//    if (cmd == F_SETFL) {
//        va_list args;
//        va_start(args, cmd);
//        int flags = va_arg(args, int);
//        va_end(args);
//
//        // 设置非阻塞模式
//        if (flags & O_NONBLOCK) {
//            u_long mode = 1; // 非阻塞模式
//            return ioctlsocket(fd, FIONBIO, &mode);
//        }
//    }
//
//    return -1;
//}
//
//// 定义 ClientState 结构体
//struct ClientState {
//    SOCKET socket;
//    WSAOVERLAPPED overlapped;
//    char buffer[4096];
//    WSABUF wsabuf;
//    DWORD flags;
//    DWORD bytes_received;
//};