#include <arpa/inet.h>
#include <cstring>
#include <iostream>
#include <netinet/in.h>
#include <readline/history.h>
#include <readline/readline.h>
#include <signal.h>
#include <sstream>
#include <string>
#include <sys/socket.h>
#include <unistd.h>
#include <vector>

#include "RespParser.hpp"

class MiniCacheClient {
public:
  MiniCacheClient() : socket_fd(-1), connected(false), host(""), port(0) {}

  ~MiniCacheClient() { disconnect(); }

  bool connect(const std::string &host, int port) {
    // 保存连接信息，用于重连
    this->host = host;
    this->port = port;
    socket_fd = socket(AF_INET, SOCK_STREAM, 0);
    if (socket_fd < 0) {
      std::cerr << "Error creating socket" << std::endl;
      return false;
    }

    struct sockaddr_in server_addr;
    memset(&server_addr, 0, sizeof(server_addr));
    server_addr.sin_family = AF_INET;
    server_addr.sin_port = htons(port);

    if (inet_pton(AF_INET, host.c_str(), &server_addr.sin_addr) <= 0) {
      std::cerr << "Invalid address: " << host << std::endl;
      close(socket_fd);
      return false;
    }

    if (::connect(socket_fd, (struct sockaddr *)&server_addr,
                  sizeof(server_addr)) < 0) {
      std::cerr << "Connection failed to " << host << ":" << port << std::endl;
      close(socket_fd);
      return false;
    }

    connected = true;
    std::cout << "Connected to " << host << ":" << port << std::endl;
    return true;
  }

  // 添加重连方法
  bool reconnect() {
    if (host.empty() || port == 0) {
      std::cerr << "No previous connection information available" << std::endl;
      return false;
    }

    std::cout << "Attempting to reconnect to " << host << ":" << port << "..."
              << std::endl;

    // 先确保断开旧连接
    if (socket_fd >= 0) {
      close(socket_fd);
      socket_fd = -1;
    }

    return connect(host, port);
  }

  void disconnect() {
    if (socket_fd >= 0) {
      close(socket_fd);
      socket_fd = -1;
      connected = false;
    }
  }

  std::string sendCommand(const std::vector<std::string> &args) {
    if (!connected) {
      return "Error: Not connected";
    }

    // 构建 RESP 格式命令
    std::string command = "*" + std::to_string(args.size()) + "\r\n";
    for (const auto &arg : args) {
      command += "$" + std::to_string(arg.length()) + "\r\n" + arg + "\r\n";
    }

    // 发送命令
    if (send(socket_fd, command.c_str(), command.length(), 0) < 0) {
      std::cerr << "Error sending command" << std::endl;
      return "Error: Send failed";
    }

    // 接收响应
    char buffer[4096];
    ssize_t bytes_read = recv(socket_fd, buffer, sizeof(buffer) - 1, 0);
    if (bytes_read <= 0) {
      std::cerr << "Error receiving response" << std::endl;
      return "Error: Receive failed";
    }

    buffer[bytes_read] = '\0';
    return formatResponse(buffer);
  }
  bool isConnected() const { return connected; }
  // 获取连接信息
  std::string getConnectionInfo() const {
    return host + ":" + std::to_string(port);
  }

private:
  int socket_fd;
  bool connected;
  std::string host;
  int port;

  std::string formatResponse(const char *resp) {
    if (resp[0] == '+') {
      // 简单字符串
      return std::string(resp + 1);
    } else if (resp[0] == '-') {
      // 错误
      return "ERROR: " + std::string(resp + 1);
    } else if (resp[0] == ':') {
      // 整数
      return std::string(resp + 1);
    } else if (resp[0] == '$') {
      // 批量字符串
      return parseBulkString(resp);
    } else if (resp[0] == '*') {
      // 数组
      return parseArray(resp);
    }
    return std::string(resp);
  }

  std::string parseBulkString(const char *resp) {
    // 简化实现，实际应该更完整地解析
    const char *end = strstr(resp, "\r\n");
    if (!end)
      return std::string(resp);

    int len = std::stoi(std::string(resp + 1, end));
    if (len < 0)
      return "(nil)";

    const char *data = end + 2;
    return std::string(data, len);
  }

  std::string parseArray(const char *resp) {
    // 简化实现，实际应该更完整地解析
    std::string result = "[";
    // 解析数组长度
    const char *end = strstr(resp, "\r\n");
    if (!end)
      return "[解析错误]";

    int count = std::stoi(std::string(resp + 1, end));
    if (count <= 0)
      return "[]"; // 空数组

    const char *pos = end + 2; // 跳过第一个 \r\n

    for (int i = 0; i < count; i++) {
      // 检查是否是批量字符串
      if (pos[0] == '$') {
        const char *lenEnd = strstr(pos, "\r\n");
        if (!lenEnd)
          break;

        int len = std::stoi(std::string(pos + 1, lenEnd));
        pos = lenEnd + 2;

        if (len < 0) {
          result += "nil";
        } else {
          // 添加字符串值到结果
          result += std::string(pos, len);
          pos += len + 2; // 跳过值和\r\n
        }
      } else if (pos[0] == ':') {
        // 整数类型
        const char *numEnd = strstr(pos, "\r\n");
        if (!numEnd)
          break;

        result += std::string(pos + 1, numEnd);
        pos = numEnd + 2;
      } else {
        // 其他类型，简单跳过
        const char *nextPos = strstr(pos, "\r\n");
        if (!nextPos)
          break;
        pos = nextPos + 2;
      }

      // 添加分隔符，除非是最后一个元素
      if (i < count - 1) {
        result += ", ";
      }
    }
    result += "]";
    return result;
  }
};

// 全局变量，用于信号处理
MiniCacheClient *g_client = nullptr;
bool g_running = true;

// 信号处理函数
void signalHandler(int signum) {
  if (signum == SIGINT) {
    std::cout << "\nExiting..." << std::endl;
    g_running = false;
  }
}

// 解析命令行输入
std::vector<std::string> parseCommand(const std::string &line) {
  std::vector<std::string> args;
  std::string current;
  bool inQuotes = false;

  for (char c : line) {
    if (c == ' ' && !inQuotes) {
      if (!current.empty()) {
        args.push_back(current);
        current.clear();
      }
    } else if (c == '"') {
      inQuotes = !inQuotes;
    } else if (c == ';' && !inQuotes) {
      if (!current.empty()) {
        args.push_back(current);
        current.clear();
      }
      break;
    } else {
      current += c;
    }
  }

  if (!current.empty()) {
    args.push_back(current);
  }

  return args;
}

int main(int argc, char *argv[]) {
  // 设置信号处理
  signal(SIGINT, signalHandler);

  // 默认连接参数
  std::string host = "127.0.0.1";
  int port = 6379;

  // 解析命令行参数
  for (int i = 1; i < argc; i++) {
    if (strcmp(argv[i], "-h") == 0 && i + 1 < argc) {
      host = argv[i + 1];
      i++;
    } else if (strcmp(argv[i], "-p") == 0 && i + 1 < argc) {
      port = std::stoi(argv[i + 1]);
      i++;
    }
  }

  MiniCacheClient client;
  g_client = &client;

  // 连接到服务器
  if (!client.connect(host, port)) {
    return 1;
  }

  // 初始化readline
  using_history();

  // 命令行循环
  while (g_running) {

    // 检查连接状态，如果断开则尝试重连
    if (!client.isConnected()) {
      std::cout << "Connection to server lost. Attempting to reconnect..."
                << std::endl;

      // 尝试重连，最多尝试5次，每次间隔1秒
      bool reconnected = false;
      for (int i = 0; i < 5 && g_running; ++i) {
        if (client.reconnect()) {
          std::cout << "Successfully reconnected to server!" << std::endl;
          reconnected = true;
          break;
        }
        std::cout << "Reconnection attempt " << (i + 1)
                  << " failed. Retrying in 1 second..." << std::endl;
        sleep(1);
      }

      if (!reconnected) {
        std::cout << "Failed to reconnect after multiple attempts. Type "
                     "'reconnect' to try again or 'exit' to quit."
                  << std::endl;
      }
    }

    char *line = readline("MC> ");
    if (!line)
      break;

    std::string input(line);
    if (!input.empty()) {
      add_history(line);

      std::vector<std::string> args = parseCommand(input);
      if (!args.empty()) {
        if (args[0] == "exit" || args[0] == "quit") {
          free(line);
          break;
        } else if (args[0] == "reconnect") {
          if (client.reconnect()) {
            std::cout << "Successfully reconnected to "
                      << client.getConnectionInfo() << std::endl;
          } else {
            std::cout << "Failed to reconnect to server" << std::endl;
          }
        } else {
          if (client.isConnected()) {
            std::string response = client.sendCommand(args);
            std::cout << response << std::endl;
          } else {
            std::cout << "Not connected to server. Type 'reconnect' to try "
                         "reconnecting."
                      << std::endl;
          }
        }
      }
    }

    free(line);
  }

  // 清理
  client.disconnect();
  clear_history();

  return 0;
}