#include "TcpServer.hpp"
#include <iostream>

TcpServer::TcpServer(const std::string& configFile)
    : port(6379), // 默认端口
      event_fd(-1),
      server_fd(-1),
      running(false),
      config(configFile),
      dataStore(),
      commandHandler(dataStore)
{
    loadConfig();
    // 注意：loadConfig() 会更新 port 变量
    setup_server();
}

TcpServer::~TcpServer() { stop(); }
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