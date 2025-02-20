#include <sys/socket.h>
#include <netinet/in.h>
#include <unistd.h>
#include <fcntl.h>

#if defined(__linux__)
    #include <sys/epoll.h>
#elif defined(__APPLE__) || defined(__FreeBSD__) || defined(__OpenBSD__) || defined(__NetBSD__)
    #include <sys/event.h>
#else
    #error "Unsupported platform"
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