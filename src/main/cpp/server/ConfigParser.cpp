#include "ConfigParser.hpp"
#include <fstream>
#include <iostream>
#include <sstream>
#include <algorithm>

ConfigParser::ConfigParser(const std::string& filename) {
    std::ifstream file(filename);
    if (!file.is_open()) {
        std::cerr << "Unable to open profile: " << filename << std::endl;
        return;
    }
    
    std::string line;
    while (std::getline(file, line)) {
        parseLine(line);
    }
}

void ConfigParser::parseLine(const std::string& line) {
    // 跳过空行和注释
    if (line.empty() || line[0] == '#') {
        return;
    }
    
    std::istringstream iss(line);
    std::string key, value;
    
    if (std::getline(iss, key, ' ')) {
        trim(key);
        
        // 处理特殊情况：save 命令
        if (key == "save") {
            std::string seconds, changes;
            if (iss >> seconds >> changes) {
                try {
                    saveConditions.push_back({std::stoi(seconds), std::stoi(changes)});
                } catch (const std::exception& e) {
                    std::cerr << "Failed to parse save condition: " << line << std::endl;
                }
            }
            return;
        }
        
        // 读取剩余部分作为值
        std::getline(iss, value);
        trim(value);
        
        if (!key.empty()) {
            config[key] = value;
        }
    }
}

void ConfigParser::trim(std::string& str) const {
    // 去除前导空格
    str.erase(str.begin(), std::find_if(str.begin(), str.end(), [](unsigned char ch) {
        return !std::isspace(ch);
    }));
    
    // 去除尾部空格
    str.erase(std::find_if(str.rbegin(), str.rend(), [](unsigned char ch) {
        return !std::isspace(ch);
    }).base(), str.end());
}

std::string ConfigParser::getString(const std::string& key, const std::string& defaultValue) const {
    auto it = config.find(key);
    return (it != config.end()) ? it->second : defaultValue;
}

int ConfigParser::getInt(const std::string& key, int defaultValue) const {
    auto it = config.find(key);
    if (it != config.end()) {
        try {
            return std::stoi(it->second);
        } catch (const std::exception& e) {
            std::cerr << "Configuration items " << key << "are not a valid integer: " << it->second << std::endl;
        }
    }
    return defaultValue;
}

bool ConfigParser::getBool(const std::string& key, bool defaultValue) const {
    auto it = config.find(key);
    if (it != config.end()) {
        std::string value = it->second;
        std::transform(value.begin(), value.end(), value.begin(), ::tolower);
        return value == "yes" || value == "true" || value == "1";
    }
    return defaultValue;
}

std::vector<std::string> ConfigParser::getStringList(const std::string& key) const {
    std::vector<std::string> result;
    auto it = config.find(key);
    if (it != config.end()) {
        std::istringstream iss(it->second);
        std::string item;
        while (std::getline(iss, item, ',')) {
            trim(item);
            if (!item.empty()) {
                result.push_back(item);
            }
        }
    }
    return result;
}

bool ConfigParser::hasKey(const std::string& key) const {
    return config.find(key) != config.end();
}

const std::unordered_map<std::string, std::string>& ConfigParser::getAll() const {
    return config;
}

std::vector<std::pair<int, int>> ConfigParser::getSaveConditions() const {
    return saveConditions;
}