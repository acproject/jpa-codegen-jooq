#pragma once
#include <string>
#include <unordered_map>
#include <mutex>
#include <vector>
#include <fstream>
#include <chrono>
#include <algorithm>
#ifdef __APPLE__
#include <sys/sysctl.h>  // 添加系统头文件，用于 sysctl 函数
#endif

class DataStore {
private:
    static constexpr size_t DEFAULT_MAX_DBS = 16;      // 默认数据库数量
    static constexpr size_t ABSOLUTE_MAX_DBS = 1024;   // 绝对最大数据库数量
    size_t max_databases;  
    std::atomic<int> changeCount;  // 跟踪变更次数
public:
    ~DataStore();

    // 获取并重置变更计数
    int getAndResetChangeCount() {
        return changeCount.exchange(0);
    }

    // 增加变更计数
    void incrementChangeCount() {
        changeCount++;
    }

    DataStore(size_t max_dbs = DEFAULT_MAX_DBS);
    // 清理过期键
    void cleanExpiredKeys();    

    // 保留结构体定义和函数声明
    struct KeyValue {
        std::string key;
        std::string value;
        bool valid;
        KeyValue(const std::string& k, const std::string& v, bool vld = true)
            : key(k), value(v), valid(vld) {}
    };

    struct NumericValue {
        std::string key;  // 添加键名
        std::vector<float> values;  // 改为动态数组
        bool valid = true;
        NumericValue() : valid(true) {}
    };

    struct KeyMetadata {
        long long expireTime;
        long long version;
        KeyMetadata() : expireTime(0), version(0) {}
    };

    struct Database {
        std::unordered_map<std::string, std::string> data;
        std::unordered_map<std::string, std::chrono::steady_clock::time_point> expires;
        std::vector<KeyValue> data_array;
        std::vector<NumericValue> numeric_array;
        std::unordered_map<std::string, KeyMetadata> metadata;
    };

    struct WatchedKey {
        std::string key;
        std::string value;
        long long version;
    };

    struct Transaction {
        std::vector<std::pair<std::string, std::string>> commands;
        std::vector<WatchedKey> watched_keys;
        bool active;
        Transaction() : active(false) {}
    };


    // 公共方法声明
    void set(const std::string& key, const std::string& value);
    std::string get(const std::string& key);
    int del(const std::string& key);
    int exists(const std::string& key);
    void multi();
    bool exec();
    void discard();
    bool watch(const std::string& key);
    void unwatch();
    bool select(int index);
    void flushdb();
    void flushall();
    std::vector<std::string> keys(const std::string& pattern = "*");
    std::vector<std::string> scan(const std::string& pattern, size_t count = 10);
    std::string info() const;
    void pexpire(const std::string& key, long long milliseconds);
    long long pttl(const std::string& key);
    bool saveMCDB(const std::string& filename);
    bool loadMCDB(const std::string& filename);
    bool set_numeric(const std::string& key, const std::vector<float>& values);
    std::vector<float> get_numeric(const std::string& key) const;
    bool rename(const std::string& oldKey, const std::string& newKey);
    bool validateDbCount(uint32_t db_count) const;

private:
    std::vector<Database> databases;
    int current_db;
    mutable std::mutex mutex;
    Transaction current_transaction;

    // 私有辅助方法声明
    Database& getCurrentDatabase();
    const Database& getCurrentDatabase() const;
    bool isValidDBIndex(int index) const;
    bool isKeyExists(const std::string& key) const;
    void removeExpiredKey(const std::string& key);
    bool isInTransaction() const;
    void clearTransaction();
    void queueCommand(const std::string& key, const std::string& value);
    void compact();
    void checkCompaction();
    void discardInternal();
    int getCurrentDB() const;
    size_t getDBCount() const;
    bool isExpired(const std::string& key) const;
    bool matchPattern(const std::string& str, const std::string& pattern);
    int delMultiple(const std::vector<std::string>& keys);
    size_t getAvailableMemory() const;                            // 当前配置
};