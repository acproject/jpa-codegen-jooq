// 跨平台兼容性测试文件
// 用于验证TcpServer在不同平台下的编译兼容性

#include "TcpServer.hpp"
#include <iostream>

int main() {
    std::cout << "Testing cross-platform compatibility..." << std::endl;
    
    // 测试基本的跨平台宏定义
#ifdef _WIN32
    std::cout << "Windows platform detected" << std::endl;
    std::cout << "INVALID_SOCKET_VALUE: " << INVALID_SOCKET_VALUE << std::endl;
#else
    std::cout << "Unix/Linux platform detected" << std::endl;
    std::cout << "INVALID_SOCKET_VALUE: " << INVALID_SOCKET_VALUE << std::endl;
#endif
    
    // 测试TcpServer实例化
    try {
        TcpServer server("test.conf");
        std::cout << "TcpServer instance created successfully" << std::endl;
    } catch (const std::exception& e) {
        std::cerr << "Error creating TcpServer: " << e.what() << std::endl;
    }
    
    std::cout << "Compatibility test completed" << std::endl;
    return 0;
}