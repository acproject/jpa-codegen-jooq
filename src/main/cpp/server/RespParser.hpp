#ifdef _WIN32
    #include <winsock2.h>
    #include <ws2tcpip.h>
    #include <io.h>
#else
    #include <sys/socket.h>
    #include <netinet/in.h>
    #include <unistd.h>
#endif
#include <fcntl.h>

#if defined(__linux__)
    #include <sys/epoll.h>
#elif defined(__APPLE__) || defined(__FreeBSD__) || defined(__OpenBSD__) || defined(__NetBSD__)
    #include <sys/event.h>
#else
    #ifdef _WIN32
        #include <windows.h>
        // Windows平台使用IOCP实现
    #else
        #error "当前平台不支持 - 需要Linux/BSD(支持epoll/kqueue)或Windows(支持IOCP)系统"
    #endif
#endif

#pragma once

#include <string>
#include <vector>

class RespParser {
public:
    static std::vector<std::string> parse(std::string& buffer);
};