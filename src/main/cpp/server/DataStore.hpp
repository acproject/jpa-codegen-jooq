#pragma once
#include <string>
#include <unordered_map>
#include <mutex>

class DataStore {  // 修改 DatacaStore 为 DataStore
public:
    void set(const std::string& key, const std::string& value) {
        std::lock_guard<std::mutex> lock(mutex);
        data[key] = value;
    }
    
    std::string get(const std::string& key) {
        std::lock_guard<std::mutex> lock(mutex);
        auto it = data.find(key);
        return it != data.end() ? it->second : "(nil)";
    }

    int del(const std::string& key) {
        std::lock_guard<std::mutex> lock(mutex);
        return data.erase(key);
    }

    int exists(const std::string& key) {
        std::lock_guard<std::mutex> lock(mutex);
        return data.count(key) > 0 ? 1: 0;
    }
    std::unordered_map<std::string, std::string> data;
    std::mutex mutex;
};