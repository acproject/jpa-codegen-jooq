#include "CommandHandler.hpp"
#include "DataStore.hpp"
#include "TcpServer.hpp"
#ifdef _WIN32
WSADATA wsaData;
if (WSAStartup(MAKEWORD(2, 2), &wsaData) != 0) {
  std::cerr << "WSAStartup failed" << std::endl;
  return 1;
}
#endif
int main() {
  DataStore store;
  CommandHandler handler(store);

  TcpServer server(6379);
  server.command_handler = [&](const std::vector<std::string> &cmd) {
    return handler.handle_command(cmd);
  };

  server.start();
  return 0;
}

#ifdef _WIN32
WSACleanup();
#endif