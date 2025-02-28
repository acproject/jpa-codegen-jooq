#include "TcpServer.hpp"
#include "CommandHandler.hpp"
#include "DataStore.hpp"
#include <atomic>
#include <string>
#include <iostream>
#include <thread>
#include <chrono>
#include <fstream>


// 全局变量
TcpServer* g_server = nullptr;
std::atomic<bool> g_running(true);

void signal_handler(int signum) {
    std::cout << "\n接收到信号 " << signum << "，正在优雅关闭..." << std::endl;  

    g_running = false;  // 设置全局运行标志为 false


    // 停止服务器
    if (!g_server) {
        g_server->stop();
    }
}

int main(int argc, char* argv[]) {
    // 设置信号处理 - 使用标准 signal 函数，而不是 sigaction
    signal(SIGINT, signal_handler);  // Ctrl+C
    signal(SIGTERM, signal_handler); // kill 命令

    DataStore store;
    CommandHandler handler(store);


    std::string rdbFile = "dump.rdb";
    std::ifstream testFile(rdbFile);
    if (testFile.good()) {
        std::cout << "找到 RDB 文件，尝试加载..." << std::endl;
        if (store.loadRDB(rdbFile)) {
            std::cout << "成功加载 RDB 文件" << std::endl;
        } else {
            std::cout << "加载 RDB 文件失败，使用空数据库启动" << std::endl;
        }
    } else {
        std::cout << "RDB 文件不存在，使用空数据库启动" << std::endl;
    }


    int port = 6379;  // 默认端口号

    for (int i = 1; i < argc; ++i) {
        std::string arg = argv[i];
        if (arg == "--port" && i + 1 < argc) {
            try {
                port = std::stoi(argv[i + 1]);
            } catch (const std::invalid_argument& e) {
                std::cerr << "Invalid port number: " << argv[i + 1] << std::endl;
                return 1;
            } catch (const std::out_of_range& e) {
                std::cerr << "Port number out of range: " << argv[i + 1] << std::endl;
                return 1;
            }
            ++i;  // 跳过端口号
        }
    }

    TcpServer server(port);
    server.command_handler = [&](const std::vector<std::string>& cmd) {
        return handler.handle_command(cmd);
        };
    

    std::cout << "MiniCache 服务器启动在端口 " << port << std::endl;
    std::cout << "按 Ctrl+C 优雅退出" << std::endl;

    // 创建一个后台线程定期清理过期键
      std::thread cleanup_thread([&store]() {
        while (g_running) {  // 直接使用全局变量，不捕获
            // 每秒清理一次过期键
            std::this_thread::sleep_for(std::chrono::seconds(1));
            store.cleanExpiredKeys();
        }
    });

    // 设置线程为分离状态，让它在后台运行
    cleanup_thread.detach();

    server.start();

    // 在退出前保存数据
    std::cout << "保存数据到 RDB 文件..." << std::endl;
    store.saveRDB("dump.rdb");

    std::cout << "服务器已关闭" << std::endl;
    return 0;
}
