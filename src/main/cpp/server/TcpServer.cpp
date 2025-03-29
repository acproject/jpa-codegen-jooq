#include "TcpServer.hpp"
#include <iostream>
#include <sstream>

TcpServer::TcpServer(const std::string& configFile)
    : port(6379), // 默认端口
#ifndef _WIN32
      event_fd(-1),
#endif
      server_fd(INVALID_SOCKET_VALUE),
      running(false),
      config(configFile),
      dataStore(),
      commandHandler(dataStore)
{
    loadConfig();
    
#ifdef _WIN32
    // 初始化 Winsock
    WSADATA wsaData;
    if (WSAStartup(MAKEWORD(2, 2), &wsaData) != 0) {
        std::cerr << "Failed to initialize Winsock" << std::endl;
        return;
    }
    setup_server_windows();
#else
    setup_server();
#endif
}

TcpServer::~TcpServer() { 
    stop(); 
#ifdef _WIN32
    WSACleanup();
#endif
}
void TcpServer::loadConfig() {
  // 加载基本配置
  host = config.getString("bind", "127.0.0.1");
  port = config.getInt("port", 6379);
  password = config.getString("requirepass", "");


  std::cout << " The configuration is loaded:" << std::endl;
  std::cout << "  Bind address: " << host << std::endl;
  std::cout << "  Port: " << port << std::endl;

  // 加载内存管理配置
  std::string memoryStr = config.getString("maxmemory", "0");
  maxMemory = 0;

  if (memoryStr.find("gb") != std::string::npos) {
    maxMemory = std::stoull(memoryStr.substr(0, memoryStr.find("gb"))) * 1024 *
                1024 * 1024;
  } else if (memoryStr.find("mb") != std::string::npos) {
    maxMemory =
        std::stoull(memoryStr.substr(0, memoryStr.find("mb"))) * 1024 * 1024;
  } else if (memoryStr.find("kb") != std::string::npos) {
    maxMemory = std::stoull(memoryStr.substr(0, memoryStr.find("kb"))) * 1024;
  } else {
    try {
      maxMemory = std::stoull(memoryStr);
    } catch (...) {
      std::cerr << "Invalid maxmemory configuration: " << memoryStr << std::endl;
    }
  }

  maxMemoryPolicy = config.getString("maxmemory-policy", "noeviction");

  // 加载保存条件
  saveConditions = config.getSaveConditions();

  // 初始化DataStore
  // 这里可以根据配置创建DataStore，例如设置数据库数量等

  std::cout << "  Max memory: "
            << (maxMemory ? std::to_string(maxMemory) : "don't limit ") << std::endl;
  std::cout << "  Memory policy: " << maxMemoryPolicy << std::endl;
  std::cout << "  Save the condition: " << saveConditions.size() << "pieces" << std::endl;
}

void TcpServer::start() {
    if (running) {
        std::cout << "Server is already running" << std::endl;
        return;
    }
    
    std::cout << "Starting server..." << std::endl;
    running = true;
    // 启动过期键清理线程
    std::thread([this]() {
        while (running) {
            cleanupExpiredKeys();
            std::this_thread::sleep_for(std::chrono::seconds(1));
        }
    }).detach();
    

    setupAutosave();
    
    // 移除这里的 setup_server() 调用，因为已经在构造函数中调用过了
    
    // 检查服务器是否设置成功
    if (server_fd < 0 || event_fd < 0) {
        std::cerr << "Server setup failed, unable to start" << std::endl;
        running = false;
        return;
    }
    
    std::cout << "Server setup successful, starting event loop..." << std::endl;
    
    // 运行事件循环
    event_loop();
    
    std::cout << "Event loop has exited" << std::endl;
}
void TcpServer::stop() {
  if (!running) {
    std::cout << "Server is already stopped" << std::endl;
    return;
  }

  std::cout << "Stopping server..." << std::endl;
  running = false;

  if (serverThread.joinable()) {
    serverThread.join();
  }
  std::lock_guard<std::mutex> lock(clientsMutex);
  for (auto &thread : clientThreads) {
    if (thread.joinable()) {
      thread.join();
    }
  }
  clientThreads.clear();
  // 创建一个临时连接来打破 accept 阻塞
  // int temp_socket = socket(AF_INET, SOCK_STREAM, 0);
  // if (temp_socket >= 0) {
  //     struct sockaddr_in server_addr;
  //     memset(&server_addr, 0, sizeof(server_addr));
  //     server_addr.sin_family = AF_INET;
  //     server_addr.sin_port = htons(port);
  //     inet_pton(AF_INET, host, &server_addr.sin_addr);

  //     if (connect(temp_socket, (struct sockaddr*)&server_addr,
  //     sizeof(server_addr)) == 0) {
  //         std::cout << "Stop signal sent successfully" << std::endl;
  //     }
  //     close(temp_socket);
  // }
  std::cout << "Server stopped" << std::endl;
}

void TcpServer::serverLoop() {
  // 使用已经在 setup_server() 中创建的 server_fd，而不是重新创建
  if (server_fd < 0) {
    std::cerr << "Server socket not initialized" << std::endl;
    return;
  }

  // 接受连接
  while (running) {
    struct sockaddr_in clientAddr;
    socklen_t clientAddrLen = sizeof(clientAddr);

    int clientSocket =
        accept(server_fd, (struct sockaddr *)&clientAddr, &clientAddrLen);
    if (clientSocket < 0) {
      if (running) {
        std::cerr << "Failed to accept the connection: " << strerror(errno) << std::endl;
      }
      continue;
    }

    char clientIP[INET_ADDRSTRLEN];
    inet_ntop(AF_INET, &clientAddr.sin_addr, clientIP, INET_ADDRSTRLEN);
    std::cout << "new conntect: " << clientIP << ":" << ntohs(clientAddr.sin_port)
              << std::endl;

    // 创建新线程处理客户端
    std::lock_guard<std::mutex> lock(clientsMutex);
    clientThreads.push_back(
        std::thread(&TcpServer::handleClient, this, clientSocket));
  }

//   close(serverSocket);
}

void TcpServer::handleClient(int clientSocket) {
  // 设置非阻塞模式
  int flags = fcntl(clientSocket, F_GETFL, 0);
  fcntl(clientSocket, F_SETFL, flags | O_NONBLOCK);
  
  // 创建客户端状态
  ClientState state;
  
  // 处理客户端请求
  char buffer[4096];
  bool authenticated = password.empty(); // 如果没有设置密码，则默认已认证
  
  while (running) {
    // 读取客户端数据
    ssize_t bytesRead = read(clientSocket, buffer, sizeof(buffer));
    
    if (bytesRead > 0) {
      // 将读取的数据添加到缓冲区
      state.buffer.append(buffer, bytesRead);
      
      // 尝试解析RESP协议
      auto commands = RespParser::parse(state.buffer);
      
      if (!commands.empty()) {
        // 处理认证
        if (!authenticated && !commands.empty() && commands[0] == "AUTH") {
          if (commands.size() >= 2 && commands[1] == password) {
            authenticated = true;
            send(clientSocket, "+OK\r\n", 5, 0);
          } else {
            send(clientSocket, "-ERR invalid password\r\n", 22, 0);
          }
        } else if (!authenticated) {
          // 未认证，拒绝命令
          send(clientSocket, "-NOAUTH Authentication required.\r\n", 32, 0);
        } else {
          // 已认证，处理命令
          std::string response = command_handler(commands);
          send(clientSocket, response.c_str(), response.length(), 0);
        }
        
        // 清空缓冲区
        state.buffer.clear();
      }
    } else if (bytesRead == 0 || (bytesRead < 0 && errno != EAGAIN && errno != EWOULDBLOCK)) {
      // 连接关闭或出错
      break;
    }
    
    // 避免CPU占用过高
    std::this_thread::sleep_for(std::chrono::milliseconds(10));
  }

  // 关闭客户端连接
  close(clientSocket);
}

void TcpServer::cleanupExpiredKeys() { dataStore.cleanExpiredKeys(); }

void TcpServer::setupAutosave() {
  std::cout << "setup auto save..." << std::endl;
  if (saveConditions.empty()) {
    return;
  }

  // 启动自动保存线程
  std::thread([this]() {
    std::unordered_map<int, std::chrono::steady_clock::time_point>
        lastSaveTimes;

    for (const auto &condition : saveConditions) {
      lastSaveTimes[condition.first] = std::chrono::steady_clock::now();
    }

    while (running) {
      auto now = std::chrono::steady_clock::now();
      int changes = dataStore.getAndResetChangeCount();

      // 检查每个保存条件
      for (const auto &condition : saveConditions) {
        int seconds = condition.first;
        int requiredChanges = condition.second;

        // 检查时间条件
        auto elapsed = std::chrono::duration_cast<std::chrono::seconds>(
                           now - lastSaveTimes[seconds])
                           .count();

        // 如果时间已到并且有足够的更改，执行保存
        if (elapsed >= seconds && changes >= requiredChanges) {
          std::cout << "Auto-save triggered: " << seconds
                    << " seconds have passed with " << changes << " changes"
                    << std::endl;

          if (dataStore.saveMCDB("dump.mcdb")) {
            std::cout << "Auto-save succeeded" << std::endl;
          } else {
            std::cerr << "Auto-save failed" << std::endl;
          }

          // 重置所有计时器
          for (auto &pair : lastSaveTimes) {
            pair.second = now;
          }

          // 一旦保存，跳出循环
          break;
        }
      }

      // 每秒检查一次
      std::this_thread::sleep_for(std::chrono::seconds(1));
    }
  }).detach();
}

#ifdef _WIN32
void TcpServer::setup_server_windows() {
    // 创建 IOCP
    iocp_handle = CreateIoCompletionPort(INVALID_HANDLE_VALUE, NULL, 0, 0);
    if (iocp_handle == NULL) {
        std::cerr << "Failed to create IOCP: " << GetLastError() << std::endl;
        return;
    }

    // 创建套接字
    server_fd = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (server_fd == INVALID_SOCKET) {
        std::cerr << "Failed to create socket: " << WSAGetLastError() << std::endl;
        return;
    }

    // 设置地址重用
    int opt = 1;
    if (setsockopt(server_fd, SOL_SOCKET, SO_REUSEADDR, (const char*)&opt, sizeof(opt)) == SOCKET_ERROR) {
        std::cerr << "Failed to set socket options: " << WSAGetLastError() << std::endl;
        CLOSE_SOCKET(server_fd);
        server_fd = INVALID_SOCKET_VALUE;
        return;
    }

    // 绑定地址
    struct sockaddr_in address;
    address.sin_family = AF_INET;
    address.sin_port = htons(port);
    
    if (host == "0.0.0.0") {
        address.sin_addr.s_addr = INADDR_ANY;
    } else {
        inet_pton(AF_INET, host.c_str(), &(address.sin_addr));
    }

    if (bind(server_fd, (struct sockaddr*)&address, sizeof(address)) == SOCKET_ERROR) {
        std::cerr << "Failed to bind: " << WSAGetLastError() << std::endl;
        CLOSE_SOCKET(server_fd);
        server_fd = INVALID_SOCKET_VALUE;
        return;
    }

    // 监听连接
    if (listen(server_fd, SOMAXCONN) == SOCKET_ERROR) {
        std::cerr << "Failed to listen: " << WSAGetLastError() << std::endl;
        CLOSE_SOCKET(server_fd);
        server_fd = INVALID_SOCKET_VALUE;
        return;
    }

    // 将服务器套接字关联到 IOCP
    if (CreateIoCompletionPort((HANDLE)server_fd, iocp_handle, (ULONG_PTR)server_fd, 0) == NULL) {
        std::cerr << "Failed to associate server socket with IOCP: " << GetLastError() << std::endl;
        CLOSE_SOCKET(server_fd);
        server_fd = INVALID_SOCKET_VALUE;
        return;
    }

    std::cout << "Server is listening on " << host << ":" << port << std::endl;
}

void TcpServer::handle_client_windows() {
    while (running) {
        DWORD bytes_transferred = 0;
        ULONG_PTR completion_key = 0;
        LPOVERLAPPED overlapped = NULL;

        // 等待 I/O 完成
        BOOL result = GetQueuedCompletionStatus(
            iocp_handle,
            &bytes_transferred,
            &completion_key,
            &overlapped,
            1000  // 超时时间（毫秒）
        );

        if (!result) {
            if (overlapped == NULL) {
                // 超时，继续循环
                continue;
            }
            
            // 处理错误
            DWORD error = GetLastError();
            if (error != ERROR_OPERATION_ABORTED) {
                std::cerr << "I/O operation failed: " << error << std::endl;
            }
            
            // 关闭客户端连接
            socket_t client_fd = (socket_t)completion_key;
            std::lock_guard<std::mutex> lock(clients_mutex);
            if (clients.find(client_fd) != clients.end()) {
                CLOSE_SOCKET(client_fd);
                clients.erase(client_fd);
            }
            continue;
        }

        socket_t client_fd = (socket_t)completion_key;
        
        // 检查是否是接受新连接
        if (client_fd == server_fd) {
            // 接受新连接
            struct sockaddr_in client_addr;
            int addr_len = sizeof(client_addr);
            socket_t new_client = accept(server_fd, (struct sockaddr*)&client_addr, &addr_len);
            
            if (new_client == INVALID_SOCKET) {
                std::cerr << "Failed to accept: " << WSAGetLastError() << std::endl;
                continue;
            }
            
            // 将新客户端套接字关联到 IOCP
            if (CreateIoCompletionPort((HANDLE)new_client, iocp_handle, (ULONG_PTR)new_client, 0) == NULL) {
                std::cerr << "Failed to associate client socket with IOCP: " << GetLastError() << std::endl;
                CLOSE_SOCKET(new_client);
                continue;
            }
            
            // 初始化客户端状态
            ClientState client_state;
            ZeroMemory(&client_state.overlapped, sizeof(WSAOVERLAPPED));
            client_state.wsaBuf.buf = client_state.recvBuffer;
            client_state.wsaBuf.len = sizeof(client_state.recvBuffer);
            client_state.pendingRead = false;
            
            {
                std::lock_guard<std::mutex> lock(clients_mutex);
                clients[new_client] = client_state;
            }
            
            // 开始异步读取
            DWORD flags = 0;
            DWORD bytes_received = 0;
            
            int recv_result = WSARecv(
                new_client,
                &clients[new_client].wsaBuf,
                1,
                &bytes_received,
                &flags,
                &clients[new_client].overlapped,
                NULL
            );
            
            if (recv_result == SOCKET_ERROR && WSAGetLastError() != WSA_IO_PENDING) {
                std::cerr << "Failed to start async read: " << WSAGetLastError() << std::endl;
                CLOSE_SOCKET(new_client);
                clients.erase(new_client);
            } else {
                clients[new_client].pendingRead = true;
            }
        } else {
            // 处理客户端数据
            std::lock_guard<std::mutex> lock(clients_mutex);
            auto it = clients.find(client_fd);
            if (it == clients.end()) {
                continue;
            }
            
            ClientState& client_state = it->second;
            
            if (bytes_transferred == 0) {
                // 客户端断开连接
                CLOSE_SOCKET(client_fd);
                clients.erase(client_fd);
                continue;
            }
            
            // 处理接收到的数据
            client_state.buffer.append(client_state.recvBuffer, bytes_transferred);
            
            // 解析并处理命令
            std::string command = client_state.buffer;
            process_command(client_fd, command);
            
            // 清空缓冲区，准备下一次读取
            client_state.buffer.clear();
            ZeroMemory(&client_state.overlapped, sizeof(WSAOVERLAPPED));
            
            // 开始下一次异步读取
            DWORD flags = 0;
            DWORD bytes_received = 0;
            
            int recv_result = WSARecv(
                client_fd,
                &client_state.wsaBuf,
                1,
                &bytes_received,
                &flags,
                &client_state.overlapped,
                NULL
            );
            
            if (recv_result == SOCKET_ERROR && WSAGetLastError() != WSA_IO_PENDING) {
                std::cerr << "Failed to start async read: " << WSAGetLastError() << std::endl;
                CLOSE_SOCKET(client_fd);
                clients.erase(client_fd);
            }
        }
    }
}
#else
// 保留现有的 setup_server 和 handle_client 方法
#endif


