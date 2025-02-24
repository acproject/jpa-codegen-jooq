#include "TcpServer.hpp"
#include "CommandHandler.hpp"
#include "DataStore.hpp"
#include <string>
#include <iostream>

int main(int argc, char* argv[]) {
    DataStore store;
    CommandHandler handler(store);

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

    server.start();
    return 0;
}
