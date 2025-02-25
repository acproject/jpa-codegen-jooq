#include "DataStore.hpp"

// 构造函数实现
DataStore::DataStore(int db_count) : current_db(0) {
    databases.resize(db_count);
}

// 基本操作方法实现
void DataStore::set(const std::string& key, const std::string& value) {
    std::lock_guard<std::mutex> lock(mutex);
    auto& db = databases[current_db];
    
    // 检查是否过期
    auto meta_it = db.metadata.find(key);
    if (meta_it != db.metadata.end() && isExpired(key)) {
        del(key);
    }

    // 设置新值
    for (auto& kv : db.data_array) {
        if (kv.key == key && kv.valid) {
            kv.value = value;
            return;
        }
    }
    
    KeyValue newKv{key, value, true};
    db.data_array.push_back(newKv);
}

std::string DataStore::get(const std::string& key) {
    std::lock_guard<std::mutex> lock(mutex);
    auto& db = databases[current_db];
    
    if (isExpired(key)) {
        del(key);
        return "(nil)";
    }

    for (const auto& kv : db.data_array) {
        if (kv.key == key && kv.valid) {
            return kv.value;
        }
    }
    return "(nil)";
}

bool DataStore::select(int index) {
    std::lock_guard<std::mutex> lock(mutex);
    if (index >= 0 && index < databases.size()) {
        current_db = index;
        return true;
    }
    return false;
}

void DataStore::pexpire(const std::string& key, long long milliseconds) {
    std::lock_guard<std::mutex> lock(mutex);
    auto now = std::chrono::system_clock::now();
    auto expireTime = now + std::chrono::milliseconds(milliseconds);
    auto timestamp = std::chrono::duration_cast<std::chrono::milliseconds>(
        expireTime.time_since_epoch()).count();
    databases[current_db].metadata[key].expireTime = timestamp;
}

// 事务相关方法实现
void DataStore::multi() {
    std::lock_guard<std::mutex> lock(mutex);
    if (!current_transaction.active) {
        current_transaction.active = true;
        current_transaction.commands.clear();
    }
}

bool DataStore::exec() {
    std::lock_guard<std::mutex> lock(mutex);
    if (!current_transaction.active) return false;

    for (const auto& watched : current_transaction.watched_keys) {
        auto& db = databases[current_db];
        auto it = db.metadata.find(watched.key);
        if (it != db.metadata.end() && it->second.version != watched.version) {
            discard();
            return false;
        }
    }

    for (const auto& cmd : current_transaction.commands) {
        set(cmd.first, cmd.second);
        databases[current_db].metadata[cmd.first].version++;
    }

    discard();
    return true;
}

// 实现缺失的方法
int DataStore::del(const std::string& key) {
    std::lock_guard<std::mutex> lock(mutex);
    auto& db = databases[current_db];
    
    for (auto& kv : db.data_array) {
        if (kv.key == key && kv.valid) {
            kv.valid = false;
            db.metadata.erase(key);
            return 1;
        }
    }
    return 0;
}

int DataStore::exists(const std::string& key) {
    std::lock_guard<std::mutex> lock(mutex);
    if (isExpired(key)) {
        del(key);
        return 0;
    }
    return isKeyExists(key) ? 1 : 0;
}

void DataStore::flushdb() {
    std::lock_guard<std::mutex> lock(mutex);
    databases[current_db] = Database();
}

void DataStore::flushall() {
    std::lock_guard<std::mutex> lock(mutex);
    databases.clear();
    databases.resize(16);
}

std::vector<std::string> DataStore::keys(const std::string& pattern) {
    std::lock_guard<std::mutex> lock(mutex);
    std::vector<std::string> result;
    auto& db = databases[current_db];
    
    for (const auto& kv : db.data_array) {
        if (kv.valid && !isExpired(kv.key) && matchPattern(kv.key, pattern)) {
            result.push_back(kv.key);
        }
    }
    return result;
}

std::vector<std::string> DataStore::scan(const std::string& pattern, size_t count) {
    return keys(pattern); // 简化实现，实际应该使用游标
}

bool DataStore::set_numeric(const std::string& key, const std::vector<float>& values) {
    std::lock_guard<std::mutex> lock(mutex);
    if (values.size() != 4) return false;
    
    auto& db = databases[current_db];
    NumericValue nv;
    std::copy(values.begin(), values.end(), nv.values);
    db.numeric_array.push_back(nv);
    return true;
}

std::vector<float> DataStore::get_numeric(const std::string& key) const {
    std::lock_guard<std::mutex> lock(mutex);
    const auto& db = databases[current_db];
    std::vector<float> result;
    // 实现获取数值数组的逻辑
    return result;
}

bool DataStore::rename(const std::string& oldKey, const std::string& newKey) {
    std::lock_guard<std::mutex> lock(mutex);
    auto& db = databases[current_db];
    
    std::string value = get(oldKey);
    if (value == "(nil)") return false;
    
    set(newKey, value);
    del(oldKey);
    return true;
}

std::string DataStore::info() const {
    std::lock_guard<std::mutex> lock(mutex);
    std::string result = "# Server\n";
    result += "databases: " + std::to_string(databases.size()) + "\n";
    result += "current_db: " + std::to_string(current_db) + "\n";
    return result;
}

bool DataStore::isExpired(const std::string& key) const {
    const auto& db = databases[current_db];
    auto it = db.metadata.find(key);
    if (it == db.metadata.end() || it->second.expireTime == 0) {
        return false;
    }
    
    auto now = std::chrono::system_clock::now();
    auto current = std::chrono::duration_cast<std::chrono::milliseconds>(
        now.time_since_epoch()).count();
    return it->second.expireTime <= current;
}

bool DataStore::matchPattern(const std::string& str, const std::string& pattern) {
    // 简单实现，支持 * 通配符
    if (pattern == "*") return true;
    return str == pattern;
}

// 添加缺失的方法实现
long long DataStore::pttl(const std::string& key) {
    std::lock_guard<std::mutex> lock(mutex);
    auto& db = databases[current_db];
    auto it = db.metadata.find(key);
    if (it == db.metadata.end() || it->second.expireTime == 0) {
        return -1;
    }
    auto now = std::chrono::system_clock::now();
    auto current = std::chrono::duration_cast<std::chrono::milliseconds>(
        now.time_since_epoch()).count();
    return std::max(0LL, it->second.expireTime - current);
}

bool DataStore::watch(const std::string& key) {
    std::lock_guard<std::mutex> lock(mutex);
    if (!current_transaction.active) return false;
    
    auto& db = databases[current_db];
    std::string current_value = get(key);
    current_transaction.watched_keys.emplace_back(WatchedKey{
        key,
        current_value,
        db.metadata[key].version
    });
    return true;
}

void DataStore::discard() {
    std::lock_guard<std::mutex> lock(mutex);
    current_transaction.active = false;
    current_transaction.commands.clear();
}

void DataStore::unwatch() {
    std::lock_guard<std::mutex> lock(mutex);
    current_transaction.watched_keys.clear();
}

bool DataStore::isKeyExists(const std::string& key) const {
    const auto& db = getCurrentDatabase();
    for (const auto& kv : db.data_array) {
        if (kv.key == key && kv.valid) {
            return true;
        }
    }
    return false;
}

// 删除 CommandHandler::handle_command 的实现，因为它属于 CommandHandler.cpp

// 添加缺失的方法实现
// 修改方法实现，使用正确的类型名
DataStore::Database& DataStore::getCurrentDatabase() {
    return databases[current_db];
}

const DataStore::Database& DataStore::getCurrentDatabase() const {
    return databases[current_db];
}

bool DataStore::saveRDB(const std::string& filename) {
    std::lock_guard<std::mutex> lock(mutex);
    std::ofstream file(filename, std::ios::binary);
    if (!file) return false;

    // 写入魔数和版本号
    const char magic[] = "REDIS0001";
    file.write(magic, 8);

    // 写入数据库数量
    uint32_t db_count = databases.size();
    file.write(reinterpret_cast<char*>(&db_count), sizeof(db_count));

    // 写入每个数据库的数据
    for (size_t i = 0; i < databases.size(); ++i) {
        auto& db = databases[i];
        file.write(reinterpret_cast<char*>(&i), sizeof(i));
        
        uint32_t kv_count = db.data_array.size();
        file.write(reinterpret_cast<char*>(&kv_count), sizeof(kv_count));

        for (const auto& kv : db.data_array) {
            if (!kv.valid) continue;
            
            uint32_t key_len = kv.key.length();
            file.write(reinterpret_cast<char*>(&key_len), sizeof(key_len));
            file.write(kv.key.c_str(), key_len);

            uint32_t value_len = kv.value.length();
            file.write(reinterpret_cast<char*>(&value_len), sizeof(value_len));
            file.write(kv.value.c_str(), value_len);

            auto it = db.metadata.find(kv.key);
            long long expire_time = (it != db.metadata.end()) ? 
                it->second.expireTime : 0;
            file.write(reinterpret_cast<char*>(&expire_time), sizeof(expire_time));
        }
    }

    return true;
}