#include <functional>
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

#include "RespParser.hpp"
#include <string>
#include <vector>
#include <unordered_map>

class TcpServer {
public:
    // 修改为 std::function 类型，以支持 lambda 表达式
    std::function<std::string(const std::vector<std::string>&)> command_handler;
    
    TcpServer(int port) : port(port), event_fd(-1), server_fd(-1) {}
    struct ClientState {
        std::string buffer;
    };
    
    void start() {
        setup_server();
        event_loop();
    }


private:
    void setup_server() {
        // 创建socket
        server_fd = socket(AF_INET, SOCK_STREAM, 0);
        
        // 添加 SO_REUSEADDR 选项
        int opt = 1;
        setsockopt(server_fd, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));
        
        // 设置非阻塞模式
        int flags = fcntl(server_fd, F_GETFL, 0);
        fcntl(server_fd, F_SETFL, flags | O_NONBLOCK);
        
        // 绑定端口
        sockaddr_in addr;{}; // 初始化地址结构体
        addr.sin_family = AF_INET;
        addr.sin_port = htons(port);
        addr.sin_addr.s_addr = INADDR_ANY;
        bind(server_fd, (sockaddr*)&addr, sizeof(addr));
        
        // 监听
        listen(server_fd, SOMAXCONN);
        
        #if defined(__linux__)
            // 创建epoll实例
            event_fd = epoll_create1(0);
            epoll_event ev{};
            ev.events = EPOLLIN;
            ev.data.fd = server_fd;
            epoll_ctl(event_fd, EPOLL_CTL_ADD, server_fd, &ev);
        #else
            // 创建kqueue实例
            event_fd = kqueue();
            struct kevent ev;
            EV_SET(&ev, server_fd, EVFILT_READ, EV_ADD, 0, 0, nullptr);
            kevent(event_fd, &ev, 1, nullptr, 0, nullptr);
        #endif
    }

    void event_loop() {
        const int MAX_EVENTS = 64;
        #if defined(__linux__)
            epoll_event events[MAX_EVENTS];
            while(true) {
                int n = epoll_wait(event_fd, events, MAX_EVENTS, -1);
                for(int i = 0; i < n; ++i) {
                    if(events[i].data.fd == server_fd) {
                        accept_connection();
                    } else {
                        handle_client(events[i].data.fd);
                    }
                }
            }
        #else
            struct kevent events[MAX_EVENTS];
            while(true) {
                int n = kevent(event_fd, nullptr, 0, events, MAX_EVENTS, nullptr);
                for(int i = 0; i < n; ++i) {
                    if(events[i].ident == server_fd) {
                        accept_connection();
                    } else {
                        handle_client(events[i].ident);
                    }
                }
            }
        #endif
    }

    void add_to_epoll(int fd) {
        #if defined(__linux__)
            epoll_event ev{};
            ev.events = EPOLLIN;
            ev.data.fd = fd;
            epoll_ctl(event_fd, EPOLL_CTL_ADD, fd, &ev);
        #else
            struct kevent ev;
            EV_SET(&ev, fd, EVFILT_READ, EV_ADD, 0, 0, nullptr);
            kevent(event_fd, &ev, 1, nullptr, 0, nullptr);
        #endif
    }

    void accept_connection() {
        sockaddr_in client_addr = {};  // 使用函数风格的初始化语法
        socklen_t addr_len = sizeof(client_addr);
        int client_fd = accept(server_fd, (sockaddr*)&client_addr, &addr_len);
        
        // 设置非阻塞模式
        if (client_fd > 0) {
            int flags = fcntl(client_fd, F_GETFL, 0);
            fcntl(client_fd, F_SETFL, flags | O_NONBLOCK);
            add_to_epoll(client_fd);
            clients[client_fd] = ClientState();  // 使用函数风格的初始化语法
        }
    }

    void handle_client(int fd) {
        auto& state = clients[fd];  // 获取当前客户端的状态
        char buffer[4096];
        ssize_t count = read(fd, buffer, sizeof(buffer));
        
        if (count <= 0) {
            if (count == 0 || (errno != EAGAIN && errno != EWOULDBLOCK)) {
                // 正常关闭或错误
                close_client(fd);
                return;
            }
            // 非阻塞模式下暂时没有数据可读
            return;
        }
        
        state.buffer.append(buffer, count);
        process_buffer(fd, state);
    }

    void close_client(int fd) {
        #if defined(__linux__)
            epoll_ctl(event_fd, EPOLL_CTL_DEL, fd, nullptr);
        #else
            struct kevent ev;
            EV_SET(&ev, fd, EVFILT_READ, EV_DELETE, 0, 0, nullptr);
            kevent(event_fd, &ev, 1, nullptr, 0, nullptr);
        #endif
        
        clients.erase(fd);
        shutdown(fd, SHUT_RDWR);  // 确保双向关闭
        close(fd);
    }

    void process_buffer(int fd,   ClientState& state) {
        // RESP协议解析逻辑
        auto cmds = RespParser::parse(state.buffer);
        if(!cmds.empty()) {
            auto response = command_handler(cmds);
            send_response(fd, response);
            state.buffer.clear();
        }
    }

    void send_response(int fd, const std::string& resp) {
        write(fd, resp.c_str(), resp.size());
    }


    int port;
    int event_fd;  // 改名为更通用的名字
    int server_fd;
    std::unordered_map<int, struct ClientState> clients;
};