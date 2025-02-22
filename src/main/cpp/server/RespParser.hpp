#ifdef _WIN32
    #include <winsock2.h>
    #include <ws2tcpip.h>
#else
    #include <sys/socket.h>
#endif
#ifdef _WIN32
    #include <WinSock2.h>
#else
    #include <netinet/in.h>
#endif
#ifdef _WIN32
    #include <io.h>
#else
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
    static std::vector<std::string> parse(std::string& buffer) {
        std::vector<std::string> commands;
        size_t pos = 0;
        
        while(pos < buffer.size()) {
            if(buffer[pos] == '*') {
                // 数组类型解析
                size_t end = buffer.find("\r\n", pos);
                int count = stoi(buffer.substr(pos+1, end-pos-1));
                pos = end + 2;
                
                for(int i = 0; i < count; ++i) {
                    if(buffer[pos] == '$') {
                        end = buffer.find("\r\n", pos);
                        int len = stoi(buffer.substr(pos+1, end-pos-1));
                        pos = end + 2;
                        commands.push_back(buffer.substr(pos, len));
                        pos += len + 2;
                    }
                }
                return commands;
            }
            // 其他类型处理...
            // todo
        }
        return std::vector<std::string>();
    }
};