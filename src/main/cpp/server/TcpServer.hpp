#pragma once

#ifdef _WIN32
    #include <winsock2.h>
    #include <ws2tcpip.h>
    #include <windows.h>
    #include <mswsock.h>
    #pragma comment(lib, "ws2_32.lib")
    #pragma comment(lib, "mswsock.lib")
#else
    #include <arpa/inet.h>
    #include <netinet/in.h>
    #include <sys/socket.h>
    #include <unistd.h>
    #include <fcntl.h>
#endif

#if defined(__linux__)
    #include <sys/epoll.h>
#elif defined(__APPLE__) || defined(__FreeBSD__) || defined(__OpenBSD__) || defined(__NetBSD__)
    #include <sys/event.h>
#elif defined(_WIN32)
    // Windows 使用 IOCP
#else
    #error "Unsupported platform"
#endif

#include "CommandHandler.hpp"
#include "ConfigParser.hpp"
#include "DataStore.hpp"
#include "RespParser.hpp"
#include <condition_variable>
#include <cstring>
#include <functional>
#include <iostream>
#include <mutex>
#include <string>
#include <thread>
#include <unordered_map>
#include <vector>
#include <atomic>

// 跨平台套接字类型定义
#ifdef _WIN32
    typedef SOCKET socket_t;
    #define INVALID_SOCKET_VALUE INVALID_SOCKET
    #define SOCKET_ERROR_VALUE SOCKET_ERROR
    #define CLOSE_SOCKET(s) closesocket(s)
#else
    typedef int socket_t;
    #define INVALID_SOCKET_VALUE (-1)
    #define SOCKET_ERROR_VALUE (-1)
    #define CLOSE_SOCKET(s) close(s)
#endif

class TcpServer {
public:
    struct ClientState {
        std::string buffer;
#ifdef _WIN32
        WSAOVERLAPPED overlapped;
        WSABUF wsaBuf;
        char recvBuffer[4096];
        bool pendingRead;
#endif
    };

    // 修改为 std::function 类型，以支持 lambda 表达式
    std::function<std::string(const std::vector<std::string>&)> command_handler;

    TcpServer(const std::string& configFile = "conf/mcs.conf");
    ~TcpServer();

    void start();
    void stop();

private:
    // 核心服务器方法
    void setup_server();
    void event_loop();
    void add_to_epoll(int fd);
    void accept_connection();
    void handle_client(int fd);
    void close_client(int fd);
    void process_buffer(int fd, ClientState &state);
    void send_response(int fd, const std::string &resp);
    
    // 配置和管理方法
    void loadConfig();
    void serverLoop();
    void handleClient(int clientSocket);
    void cleanupExpiredKeys();
    void setupAutosave();
    
#ifdef _WIN32
    // Windows 平台特定的方法
    void setup_server_windows();
    void handle_client_windows();
#endif

    // 成员变量
    // 配置相关
    int port;
    std::string host;
    std::string password;
    size_t maxMemory;
    std::string maxMemoryPolicy;
    std::vector<std::pair<int, int>> saveConditions;
    ConfigParser config;
    
    // 核心组件
    DataStore dataStore;
    CommandHandler commandHandler;
    
    // 网络相关
#ifdef _WIN32
    socket_t server_fd;
    HANDLE iocp_handle;
    std::unordered_map<socket_t, ClientState> clients;
    std::mutex clients_mutex;
#else
    int server_fd;
    int event_fd;
    std::unordered_map<int, ClientState> clients;
#endif
    
    // 线程管理
    std::atomic<bool> running;
    std::thread serverThread;
    std::vector<std::thread> clientThreads;
    std::mutex clientsMutex;
};