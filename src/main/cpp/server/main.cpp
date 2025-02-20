#include "TcpServer.hpp"
#include "CommandHandler.hpp"
#include "DataStore.hpp"

int main() {
    DataStore store;
    CommandHandler handler(store);
    
    TcpServer server(6379);
    server.command_handler = [&](const std::vector<std::string>& cmd) {
        return handler.handle_command(cmd);
    };
    
    server.start();
    return 0;
}