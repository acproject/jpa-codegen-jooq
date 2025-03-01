#include <arpa/inet.h> // 添加这个头文件，用于 inet_pton 函数
#include <cstring>
#include <fcntl.h>
#include <functional>
#include <iostream>
#include <netinet/in.h>
#include <sys/socket.h>
#include <unistd.h>

#if defined(__linux__)
#include <sys/epoll.h>
#elif defined(__APPLE__) || defined(__FreeBSD__) || defined(__OpenBSD__) ||    \
    defined(__NetBSD__)
#include <sys/event.h>
#else
#error "Unsupported platform"
#endif

#include "RespParser.hpp"
#include <string>
#include <unordered_map>
#include <vector>

class TcpServer {
public:
  struct ClientState {
    std::string buffer;
  };

  // 修改为 std::function 类型，以支持 lambda 表达式
  std::function<std::string(const std::vector<std::string> &)> command_handler;

  TcpServer(int port);
  ~TcpServer();

  void start();
  void stop();

private:
 void setup_server() {
    std::cout << "Setting up server..." << std::endl;
    // 创建socket
    server_fd = socket(AF_INET, SOCK_STREAM, 0);
    if (server_fd < 0) {
        std::cerr << "Failed to create socket: " << strerror(errno) << std::endl;
        return;
    }
    
    // 添加 SO_REUSEADDR 选项
    int opt = 1;
    if (setsockopt(server_fd, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt)) < 0) {
        std::cerr << "Failed to set socket options: " << strerror(errno) << std::endl;
        close(server_fd);
        return;
    }
    
    // 设置非阻塞模式
    int flags = fcntl(server_fd, F_GETFL, 0);
    if (flags < 0) {
        std::cerr << "Failed to get socket flags: " << strerror(errno) << std::endl;
        close(server_fd);
        return;
    }
    
    if (fcntl(server_fd, F_SETFL, flags | O_NONBLOCK) < 0) {
        std::cerr << "Failed to set non-blocking mode: " << strerror(errno) << std::endl;
        close(server_fd);
        return;
    }
    
    // 绑定端口
    sockaddr_in addr = {}; // 初始化地址结构体
    addr.sin_family = AF_INET;
    addr.sin_port = htons(port);
    addr.sin_addr.s_addr = INADDR_ANY;
    
    if (bind(server_fd, (sockaddr*)&addr, sizeof(addr)) < 0) {
        std::cerr << "Failed to bind port: " << strerror(errno) << std::endl;
        close(server_fd);
        return;
    }
    
    // 监听
    if (listen(server_fd, SOMAXCONN) < 0) {
        std::cerr << "Failed to listen: " << strerror(errno) << std::endl;
        close(server_fd);
        return;
    }
    
    std::cout << "Server socket setup complete, listening on port " << port << std::endl;
    
    #if defined(__linux__)
        // 创建epoll实例
        event_fd = epoll_create1(0);
        if (event_fd < 0) {
            std::cerr << "Failed to create epoll instance: " << strerror(errno) << std::endl;
            close(server_fd);
            return;
        }
        
        epoll_event ev{};
        ev.events = EPOLLIN;
        ev.data.fd = server_fd;
        if (epoll_ctl(event_fd, EPOLL_CTL_ADD, server_fd, &ev) < 0) {
            std::cerr << "Failed to add server socket to epoll: " << strerror(errno) << std::endl;
            close(event_fd);
            close(server_fd);
            return;
        }
    #else
        // 创建kqueue实例
        event_fd = kqueue();
        if (event_fd < 0) {
            std::cerr << "Failed to create kqueue instance: " << strerror(errno) << std::endl;
            close(server_fd);
            return;
        }
        
        struct kevent ev;
        EV_SET(&ev, server_fd, EVFILT_READ, EV_ADD, 0, 0, nullptr);
        if (kevent(event_fd, &ev, 1, nullptr, 0, nullptr) < 0) {
            std::cerr << "Failed to add server socket to kqueue: " << strerror(errno) << std::endl;
            close(event_fd);
            close(server_fd);
            return;
        }
    #endif
    
    std::cout << "Event handler setup complete" << std::endl;
}

// 修改 event_loop 方法，减少不必要的日志输出，并确保正确处理事件
void event_loop() {
    const int MAX_EVENTS = 64;
    std::cout << "Starting event loop..." << std::endl;
    
    #if defined(__linux__)
        // ... Linux 代码保持不变 ...
    #else
        struct kevent events[MAX_EVENTS];
        
        while(running) {
            struct timespec timeout;
            timeout.tv_sec = 1;  // 1秒超时
            timeout.tv_nsec = 0;
            
            int n = kevent(event_fd, nullptr, 0, events, MAX_EVENTS, &timeout);
            
            // 检查是否收到停止信号
            if (!running) {
                std::cout << "Received stop signal, exiting event loop" << std::endl;
                break;
            }
            
            if (n < 0) {
                if (errno == EINTR) {
                    std::cout << "kevent interrupted by signal, checking running state" << std::endl;
                    continue;  // 被信号中断，继续检查运行状态
                }
                std::cerr << "kevent error: " << strerror(errno) << std::endl;
                break;
            }
            
            // 处理实际事件
            if (n > 0) {
                std::cout << "Event loop: received " << n << " events" << std::endl;
                for(int i = 0; i < n; ++i) {
                    if(events[i].ident == server_fd) {
                        accept_connection();
                    } else {
                        handle_client(events[i].ident);
                    }
                }
            }
        }
    #endif
    
    std::cout << "Closing all connections..." << std::endl;
    
    // 关闭所有客户端连接
    for (const auto& client : clients) {
        close_client(client.first);
    }
    
    // 关闭服务器套接字
    if (server_fd >= 0) {
        std::cout << "Closing server socket..." << std::endl;
        close(server_fd);
        server_fd = -1;
    }
    
    // 关闭事件处理器
    if (event_fd >= 0) {
        std::cout << "Closing event handler..." << std::endl;
        close(event_fd);
        event_fd = -1;
    }
    
    std::cout << "Event loop has exited" << std::endl;
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
    sockaddr_in client_addr = {}; // 使用函数风格的初始化语法
    socklen_t addr_len = sizeof(client_addr);
    int client_fd = accept(server_fd, (sockaddr *)&client_addr, &addr_len);

    // 设置非阻塞模式
    if (client_fd > 0) {
      int flags = fcntl(client_fd, F_GETFL, 0);
      fcntl(client_fd, F_SETFL, flags | O_NONBLOCK);
      add_to_epoll(client_fd);
      clients[client_fd] = ClientState(); // 使用函数风格的初始化语法
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
    shutdown(fd, SHUT_RDWR); // 确保双向关闭
    close(fd);
  }

  void process_buffer(int fd, ClientState &state) {
    // RESP协议解析逻辑
    auto cmds = RespParser::parse(state.buffer);
    if (!cmds.empty()) {
      auto response = command_handler(cmds);
      send_response(fd, response);
      state.buffer.clear();
    }
  }

  void send_response(int fd, const std::string &resp) {
    write(fd, resp.c_str(), resp.size());
  }

  int port;
  int event_fd; // 改名为更通用的名字
  int server_fd;
  bool running;
  std::unordered_map<int, struct ClientState> clients;
};