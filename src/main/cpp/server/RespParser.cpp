#include "RespParser.hpp"

std::vector<std::string> RespParser::parse(std::string& input) {
    std::vector<std::string> result;
    size_t pos = 0;
    
    if (input.empty()) return result;
    
    if (input[0] == '*') {
        size_t newline = input.find("\r\n", pos);
        if (newline == std::string::npos) return result;
        
        int count = std::stoi(input.substr(1, newline - 1));
        pos = newline + 2;
        
        for (int i = 0; i < count && pos < input.length(); i++) {
            if (input[pos] == '$') {
                size_t lenEnd = input.find("\r\n", pos);
                if (lenEnd == std::string::npos) break;
                
                int len = std::stoi(input.substr(pos + 1, lenEnd - pos - 1));
                pos = lenEnd + 2;
                
                if (pos + len <= input.length()) {
                    result.push_back(input.substr(pos, len));
                    pos += len + 2;
                } else {
                    break;
                }
            } else {
                size_t nextPos = input.find("\r\n", pos);
                if (nextPos == std::string::npos) break;
                pos = nextPos + 2;
            }
        }
    }
    
    return result;
}