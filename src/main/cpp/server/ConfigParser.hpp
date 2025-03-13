#pragma once
#include <string>
#include <unordered_map>
#include <vector>
#include <utility>

class ConfigParser {
public:
    ConfigParser(const std::string& filename);
    
    // 获取配置值
    std::string getString(const std::string& key, const std::string& defaultValue = "") const;
    int getInt(const std::string& key, int defaultValue = 0) const;
    bool getBool(const std::string& key, bool defaultValue = false) const;
    std::vector<std::string> getStringList(const std::string& key) const;
    
    // 检查配置是否存在
    bool hasKey(const std::string& key) const;
    
    // 获取所有配置
    const std::unordered_map<std::string, std::string>& getAll() const;
    
    // 获取所有保存条件
    std::vector<std::pair<int, int>> getSaveConditions() const;
    
private:
    std::unordered_map<std::string, std::string> config;
    std::vector<std::pair<int, int>> saveConditions; // 保存条件：<seconds, changes>
    
    void parseLine(const std::string& line);
    void trim(std::string& str) const;
};