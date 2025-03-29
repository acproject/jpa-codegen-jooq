#include "CommandHandler.hpp"
#include "ConfigParser.hpp"
#include "DataStore.hpp"
#include "TcpServer.hpp"
#include <atomic>
#include <chrono>
#include <iostream>
#include <string>
#include <thread>

#ifdef _WIN32
#include <windows.h>
#else
#include <filesystem>
#include <signal.h>
#endif

// 全局变量
TcpServer *g_server = nullptr;
std::atomic<bool> g_running(true);

#ifdef _WIN32
BOOL WINAPI console_ctrl_handler(DWORD ctrl_type) {
    switch (ctrl_type) {
        case CTRL_C_EVENT:
        case CTRL_BREAK_EVENT:
        case CTRL_CLOSE_EVENT:
        case CTRL_LOGOFF_EVENT:
        case CTRL_SHUTDOWN_EVENT:
            std::cout << "\nReceived shutdown signal, gracefully shutting down..." << std::endl;
            g_running = false;
            if (g_server) {
                g_server->stop();
            }
            return TRUE;
        default:
            return FALSE;
    }
}
#else
void signal_handler(int signum) {
    std::cout << "\nReceived signal " << signum << ", gracefully shutting down..." << std::endl;
    g_running = false;
    if (g_server) {
        g_server->stop();
    }
}
#endif

int main(int argc, char *argv[]) {
#ifdef _WIN32
    // 设置 Windows 控制台处理函数
    if (!SetConsoleCtrlHandler(console_ctrl_handler, TRUE)) {
        std::cerr << "Failed to set control handler" << std::endl;
        return 1;
    }
#else
    // 设置信号处理
    signal(SIGINT, signal_handler);  // Ctrl+C
    signal(SIGTERM, signal_handler); // kill 命令
#endif

    // 解析命令行参数
    std::string configPath = "conf/mcs.conf"; // 默认配置文件路径

    for (int i = 1; i < argc; ++i) {
        std::string arg = argv[i];
        if (arg == "--config" && i + 1 < argc) {
            configPath = argv[i + 1];
            ++i; // 跳过配置文件路径
        }
    }

    // 检查配置文件是否存在
#ifdef _WIN32
    DWORD fileAttributes = GetFileAttributesA(configPath.c_str());
    bool configExists = (fileAttributes != INVALID_FILE_ATTRIBUTES && 
                         !(fileAttributes & FILE_ATTRIBUTE_DIRECTORY));
#else
    bool configExists = std::filesystem::exists(configPath);
#endif

    if (!configExists) {
        std::cerr << "The configuration file does not exist: " << configPath << std::endl;
        std::cerr << "Use the default configuration..." << std::endl;
    }

  // 加载配置文件
  ConfigParser config(configPath);

  // 获取配置项
  std::string host = config.getString("bind", "127.0.0.1");
  int port = config.getInt("port", 6379);
  std::string password = config.getString("requirepass", "");

  // 获取内存限制
  std::string memoryStr = config.getString("maxmemory", "0");
  size_t maxMemory = 0;

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

  std::string maxMemoryPolicy =
      config.getString("maxmemory-policy", "noeviction");

  // 获取保存条件
  auto saveConditions = config.getSaveConditions();

  // 创建数据存储
  DataStore store;
  CommandHandler handler(store);

  // 尝试加载持久化数据
  std::string mcdbFile = "dump.mcdb";
  std::ifstream testFile(mcdbFile);
  if (testFile.good()) {
    std::cout << "Found MCDB file, attempting to load..." << std::endl;
    if (store.loadMCDB(mcdbFile)) {
      std::cout << "Successfully loaded MCDB file" << std::endl;
    } else {
      std::cout << "Failed to load MCDB file, starting with empty database"
                << std::endl;
    }
  } else {
    std::cout << "MCDB file does not exist, starting with empty database"
              << std::endl;
  }

  // 创建服务器
  TcpServer server(configPath);
  g_server = &server; // 设置全局指针

  server.command_handler = [&](const std::vector<std::string> &cmd) {
    // 如果设置了密码，需要验证
    if (!password.empty() && cmd.size() > 0 && cmd[0] != "AUTH") {
      // 检查是否已认证
      // 这里需要添加认证状态的检查逻辑
      // 暂时简化处理
    }
    return handler.handle_command(cmd);
  };

  std::cout << "MiniCache server started on " << host << ":" << port
            << std::endl;
  std::cout << "Press Ctrl+C to gracefully exit" << std::endl;

  // 创建一个后台线程定期清理过期键
  std::thread cleanup_thread([&store]() {
    while (g_running) { // 使用全局变量
      // 每秒清理一次过期键
      std::this_thread::sleep_for(std::chrono::seconds(1));
      store.cleanExpiredKeys();
    }
  });

  // 设置自动保存
  std::thread autosave_thread([&store, &saveConditions]() {
    std::unordered_map<int, std::chrono::steady_clock::time_point>
        lastSaveTimes;
    std::unordered_map<int, int> changesSinceSave;

    for (const auto &condition : saveConditions) {
      lastSaveTimes[condition.first] = std::chrono::steady_clock::now();
      changesSinceSave[condition.first] = 0;
    }

    while (g_running) {
      auto now = std::chrono::steady_clock::now();

      // 检查每个保存条件
      for (const auto &condition : saveConditions) {
        int seconds = condition.first;
        int changes = condition.second;

        // 检查时间条件
        auto elapsed = std::chrono::duration_cast<std::chrono::seconds>(
                           now - lastSaveTimes[seconds])
                           .count();

        // 如果时间已到并且有足够的更改，执行保存
        if (elapsed >= seconds) {
          // 这里简化处理，实际应该检查变更次数
          std::cout << "Auto-save triggered: " << seconds
                    << " seconds have passed" << std::endl;

          if (store.saveMCDB("dump.mcdb")) {
            std::cout << "Auto-save succeeded" << std::endl;
          } else {
            std::cerr << "Auto-save failed" << std::endl;
          }

          // 重置计时器
          lastSaveTimes[seconds] = now;

          // 一旦保存，跳出循环
          break;
        }
      }

      // 每秒检查一次
      std::this_thread::sleep_for(std::chrono::seconds(1));
    }
  });

  // 设置线程为分离状态，让它在后台运行
  cleanup_thread.detach();
  autosave_thread.detach();

  server.start();

  // 在退出前保存数据
  std::cout << "Saving data to MCDB file..." << std::endl;
  store.saveMCDB("dump.mcdb");

  std::cout << "Server has been shut down" << std::endl;
  return 0;
}