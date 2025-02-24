#pragma once
#include <string>
#include <unordered_map>
#include <mutex>
#include <vector>

class DataStore {  // 修改 DatacaStore 为 DataStore
public:
    // 针对数值类型的优化存储结构
    struct alignas(16) NumericValue {
        float values[4];  // 适合SIMD运算的4个float
        bool valid;
        
        NumericValue() : values{0}, valid(true) {}
    };

    struct KeyValue {
        std::string key;
        std::string value;
        bool valid;
    };

    // 添加数值类型的专用存储
    std::vector<NumericValue> numeric_array;
    std::vector<KeyValue> data_array;
    std::mutex mutex;

void set(const std::string& key, const std::string& value) {
    std::lock_guard<std::mutex> lock(mutex); // 设置一个互斥锁，保证线程安全
    // data[key] = value;
    // 使用连续存储的vector存储数据
    for (auto& kv : data_array) {
        if (kv.key == key && kv.valid) {
            kv.value = value;
            return;
        }
    }
    
    // 如果key不存在，添加新的键值对
    KeyValue newKv{key, value, true};
    data_array.push_back(newKv);
}
    
    std::string get(const std::string& key) {
        std::lock_guard<std::mutex> lock(mutex);
        // 遍历vector查找对应的key
        for (const auto& kv : data_array) {
            if (kv.key == key && kv.valid) {
                return kv.value;
            }
        }
        return "(nil)";
    }

    int del(const std::string& key) {
        std::lock_guard<std::mutex> lock(mutex);
        // 遍历vector查找并标记要删除的元素
        for (auto& kv : data_array) {
            if (kv.key == key && kv.valid) {
                kv.valid = false;  // 将元素标记为无效
                return 1;  // 删除成功返回1
            }
        }
        return 0;  // 未找到要删除的key返回0
    }

    int exists(const std::string& key) {
        std::lock_guard<std::mutex> lock(mutex);
        // 遍历vector查找对应的key是否存在且有效
        for (const auto& kv : data_array) {
            if (kv.key == key && kv.valid) {
                return 1;
            }
        }
        return 0;
    }
    // 删除文件末尾重复的声明
    // std::unordered_map<std::string, std::string> data;
    // std::vector<KeyValue> data_array;  // 删除这行，因为在前面已经声明
    // std::mutex mutex;  // 删除这行，因为在前面已经声明
};