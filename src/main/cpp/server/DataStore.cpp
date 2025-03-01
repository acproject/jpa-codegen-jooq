#include "DataStore.hpp"
#include <iostream>


DataStore::~DataStore() {
  // 析构函数，可以在这里进行一些清理工作
  // 例如保存数据到磁盘等
  try {
    saveMCDB("dump.mcdb"); // 在析构时尝试保存数据
  } catch (...) {
    // 忽略异常，确保析构函数不抛出异常
    std::cerr << "Error occurred while saving data" << std::endl;
  }
}

DataStore::DataStore(size_t max_dbs) : current_db(0), changeCount(0) {
    max_databases = std::min(max_dbs, ABSOLUTE_MAX_DBS);
    databases.resize(DEFAULT_MAX_DBS);  // 初始只分配默认数量
}

bool DataStore::validateDbCount(uint32_t db_count) const {
   if (db_count == 0) {
            std::cerr << "Invalid database count: 0" << std::endl;
            return false;
        }
        
        if (db_count > max_databases) {
            std::cerr << "Database count " << db_count 
                      << " exceeds maximum allowed (" << max_databases << ")" << std::endl;
            return false;
        }

        // 检查系统资源
        size_t estimated_memory = db_count * sizeof(Database);  // 估算内存使用
        if (estimated_memory > getAvailableMemory() * 0.75) {  // 使用不超过可用内存的75%
            std::cerr << "Insufficient memory for " << db_count << " databases" << std::endl;
            return false;
        }

        return true;
}

size_t DataStore::getAvailableMemory() const {
  // 获取系统可用内存
   #ifdef __APPLE__
            int mib[2] = { CTL_HW, HW_MEMSIZE };
            u_int namelen = sizeof(mib) / sizeof(mib[0]);
            uint64_t size;
            size_t len = sizeof(size);
            
            if (sysctl(mib, namelen, &size, &len, NULL, 0) < 0) {
                return 8ULL * 1024 * 1024 * 1024;  // 默认8GB
            }
            return size;
        #else
            return 8ULL * 1024 * 1024 * 1024;  // 默认8GB
        #endif
}

// 添加 loadRDB 方法实现
bool DataStore::loadMCDB(const std::string &filename) {
  std::lock_guard<std::mutex> lock(mutex);
  std::ifstream file(filename, std::ios::binary);
  if (!file) {
    std::cerr << "Unable to open file: " << filename << std::endl;
    return false;
  }

  try {
    // 读取魔数
    char magic[9] = {0};
    file.read(magic, 8);
    std::string magicStr(magic);
    std::cout << "Magic number read: " << magicStr << std::endl;

    // 检查魔数
    if (magicStr != "MINCACHE") {
      std::cerr << "Invalid MCDB file format, magic number mismatch: "
                << magicStr << std::endl;
      return false;
    }

    // 读取数据库数量
    uint32_t db_count;
    file.read(reinterpret_cast<char *>(&db_count), sizeof(db_count));
    
    // // 确保数据库数量合理
    // if (db_count > 100) { // 设置一个合理的上限
    //   std::cerr << "Abnormal database count: " << db_count << std::endl;
    //   return false;
    // }
     // 使用新的验证方法
    if (!validateDbCount(db_count)) {
        return false;
    }
    
    // 调整数据库数量
    databases.resize(db_count);

    // 读取每个数据库的数据
    for (uint32_t i = 0; i < db_count; ++i) {
      size_t db_index;
      file.read(reinterpret_cast<char *>(&db_index), sizeof(db_index));
      
      if (db_index >= databases.size()) {
        std::cerr << "Database index out of range: " << db_index << std::endl;
        continue;
      }
      
      auto &db = databases[db_index];
      
      // 读取字符串键值对数量
      uint32_t kv_count;
      file.read(reinterpret_cast<char *>(&kv_count), sizeof(kv_count));
      
      // 读取字符串键值对
      for (uint32_t j = 0; j < kv_count; ++j) {
        // 读取键
        uint32_t key_len;
        file.read(reinterpret_cast<char *>(&key_len), sizeof(key_len));
        
        std::string key(key_len, '\0');
        file.read(&key[0], key_len);
        
        // 读取值
        uint32_t value_len;
        file.read(reinterpret_cast<char *>(&value_len), sizeof(value_len));
        
        std::string value(value_len, '\0');
        file.read(&value[0], value_len);
        
        // 读取过期时间
        long long expire_time;
        file.read(reinterpret_cast<char *>(&expire_time), sizeof(expire_time));
        
        // 存储键值对
        KeyValue kv{key, value, true};
        db.data_array.push_back(kv);
        
        // 设置过期时间
        if (expire_time > 0) {
          db.metadata[key].expireTime = expire_time;
        }
      }
      
      // 读取数值数组数量
      uint32_t nv_count;
      file.read(reinterpret_cast<char *>(&nv_count), sizeof(nv_count));
      std::cout << "Reading " << nv_count << " numeric values for database " << db_index << std::endl;
      
      // 读取数值数组
      for (uint32_t j = 0; j < nv_count; ++j) {
        // 读取键
        uint32_t key_len;
        file.read(reinterpret_cast<char *>(&key_len), sizeof(key_len));
        
        std::string key(key_len, '\0');
        file.read(&key[0], key_len);
        
        // 读取数值数组长度
        uint32_t values_len;
        file.read(reinterpret_cast<char *>(&values_len), sizeof(values_len));
        
        // 读取数值数组内容
        NumericValue nv;
        nv.key = key;
        nv.values.resize(values_len);
        if (values_len > 0) {
          file.read(reinterpret_cast<char *>(nv.values.data()),
                    sizeof(float) * values_len);
        }
        
        // 读取过期时间
        long long expire_time;
        file.read(reinterpret_cast<char *>(&expire_time),
                  sizeof(expire_time));
        
        // 存储数值数组
        db.numeric_array.push_back(nv);
        
        // 设置过期时间
        if (expire_time > 0) {
          db.metadata[key].expireTime = expire_time;
        }
      }
    }

    std::cout << "Successfully loaded MCDB file: " << filename << std::endl;
    return true;
  } catch (const std::exception &e) {
    std::cerr << "Error loading MCDB file: " << e.what() << std::endl;
    return false;
  }
}

// 清理过期键
void DataStore::cleanExpiredKeys() {
  std::lock_guard<std::mutex> lock(mutex);

  auto now = std::chrono::steady_clock::now();

  // 遍历所有数据库
  for (auto &db : databases) {
    // 创建一个临时列表存储需要删除的键
    std::vector<std::string> keys_to_delete;

    // 检查所有设置了过期时间的键
    for (auto it = db.expires.begin(); it != db.expires.end(); ++it) {
      if (now >= it->second) {
        keys_to_delete.push_back(it->first);
      }
    }

    // 删除过期的键
    for (const auto &key : keys_to_delete) {
      // 从主数据存储中删除
      db.data.erase(key);

      // 从过期时间映射中删除
      db.expires.erase(key);

      // 从 data_array 中标记为无效
      for (auto &kv : db.data_array) {
        if (kv.key == key && kv.valid) {
          kv.valid = false;
          break;
        }
      }

      // 从元数据中删除
      db.metadata.erase(key);
    }
  }
}

std::string DataStore::get(const std::string &key) {
  std::lock_guard<std::mutex> lock(mutex);
  auto &db = databases[current_db];
  // 检查键是否存在
  bool keyExists = false;
  std::string value;

  // 检查是否过期
  if (isExpired(key)) {
    std::cout << "Key " << key << " is expired, deleting it" << std::endl;

    // 直接在这里实现删除逻辑，而不是调用 del 方法
    for (auto &kv : db.data_array) {
      if (kv.key == key && kv.valid) {
        kv.valid = false;
        break;
      }
    }
    db.metadata.erase(key);

    std::cout << "Key " << key << " not found" << std::endl;
    return "(nil)";
  }

  // 从 data_array 中查找键
  for (const auto &kv : db.data_array) {
    if (kv.key == key && kv.valid) {
      std::cout << "Found key " << key << " with value " << kv.value
                << std::endl;
      return kv.value;
    }
  }
  // 如果没有找到键
  std::cout << "Key " << key << " not found" << std::endl;
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

void DataStore::pexpire(const std::string &key, long long milliseconds) {
  std::lock_guard<std::mutex> lock(mutex);
  auto now = std::chrono::system_clock::now();
  auto expireTime = now + std::chrono::milliseconds(milliseconds);
  auto timestamp = std::chrono::duration_cast<std::chrono::milliseconds>(
                       expireTime.time_since_epoch())
                       .count();
  databases[current_db].metadata[key].expireTime = timestamp;
}

// 事务相关方法实现
void DataStore::multi() {
  std::lock_guard<std::mutex> lock(mutex);
  current_transaction.active = true;
  current_transaction.commands.clear();
  current_transaction.watched_keys.clear();
}

void DataStore::set(const std::string &key, const std::string &value) {
  std::lock_guard<std::mutex> lock(mutex);
  auto &db = databases[current_db];

  // 如果在事务中，将命令添加到队列
  if (current_transaction.active) {
    current_transaction.commands.push_back({key, value});
    return;
  }

  // 正常的 set 操作
  // 检查是否过期
  auto meta_it = db.metadata.find(key);
  if (meta_it != db.metadata.end() && isExpired(key)) {
    del(key);
  }

  // 设置新值
  for (auto &kv : db.data_array) {
    if (kv.key == key && kv.valid) {
      kv.value = value;
      incrementChangeCount();  // 增加变更计数
      return;
    }
  }

  KeyValue newKv{key, value, true};
  db.data_array.push_back(newKv);
  incrementChangeCount();  // 增加变更计数
}

// 私有方法，用于内部调用，不加锁
void DataStore::discardInternal() {
  current_transaction.active = false;
  current_transaction.commands.clear();
  current_transaction.watched_keys.clear();
}

// 公共方法，用于外部调用，需要加锁
void DataStore::discard() {
  std::lock_guard<std::mutex> lock(mutex);
  discardInternal();
}

bool DataStore::exec() {
  std::cout << "Executing transaction..." << std::endl;
  std::lock_guard<std::mutex> lock(mutex);
  if (!current_transaction.active)
    return false;

  std::cout << "Checking watched keys..." << std::endl;
  for (const auto &watched : current_transaction.watched_keys) {
    auto &db = databases[current_db];
    auto it = db.metadata.find(watched.key);
    if (it != db.metadata.end() && it->second.version != watched.version) {
      discardInternal(); // 使用内部方法
      return false;
    }
  }

  std::cout << "Executing commands..." << std::endl;
  auto &db = databases[current_db];

  // 直接执行命令，而不是调用 set 方法
  for (const auto &cmd : current_transaction.commands) {
    // 检查是否过期
    auto meta_it = db.metadata.find(cmd.first);
    if (meta_it != db.metadata.end() && isExpired(cmd.first)) {
      del(cmd.first);
    }

    // 设置新值
    bool found = false;
    for (auto &kv : db.data_array) {
      if (kv.key == cmd.first && kv.valid) {
        kv.value = cmd.second;
        found = true;
        break;
      }
    }

    if (!found) {
      KeyValue newKv{cmd.first, cmd.second, true};
      db.data_array.push_back(newKv);
    }

    db.metadata[cmd.first].version++;
    incrementChangeCount();  // 增加变更计数
  }

  std::cout << "Transaction executed successfully." << std::endl;
  discardInternal(); // 使用内部方法
  return true;
}

// 实现缺失的方法
int DataStore::del(const std::string &key) {
  std::lock_guard<std::mutex> lock(mutex);
  auto &db = databases[current_db];

  for (auto &kv : db.data_array) {
    if (kv.key == key && kv.valid) {
      kv.valid = false;
      db.metadata.erase(key);
      incrementChangeCount();  
      return 1;
    }
  }
  return 0;
}

int DataStore::exists(const std::string &key) {
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

std::vector<std::string> DataStore::keys(const std::string &pattern) {
  std::lock_guard<std::mutex> lock(mutex);
  std::vector<std::string> result;
  auto &db = databases[current_db];

  for (const auto &kv : db.data_array) {
    if (kv.valid && !isExpired(kv.key) && matchPattern(kv.key, pattern)) {
      result.push_back(kv.key);
    }
  }
  return result;
}

std::vector<std::string> DataStore::scan(const std::string &pattern,
                                         size_t count) {
  return keys(pattern); // 简化实现，实际应该使用游标
}

bool DataStore::set_numeric(const std::string &key,
                            const std::vector<float> &values) {
  std::lock_guard<std::mutex> lock(mutex);
  if (values.empty())
    return false; // 至少需要一个值
  auto &db = databases[current_db];

  // 先检查是否已存在该键
  for (auto &nv : db.numeric_array) {
    if (nv.key == key && nv.valid) {
      // 更新现有值 - 需要重新分配空间
      nv.values.clear();
      nv.values.assign(values.begin(), values.end());
      return true;
    }
  }

  // 创建新的数值记录
  NumericValue nv;
  nv.key = key; // 设置键名
  nv.values.assign(values.begin(), values.end());
  db.numeric_array.push_back(nv);

  // std::cout << "Set new numeric values for key: " << key
  //           << " [" << values[0] << ", " << values[1] << ", "
  //           << values[2] << ", " << values[3] << "]" << std::endl;
  return true;
}

std::vector<float> DataStore::get_numeric(const std::string &key) const {
  std::lock_guard<std::mutex> lock(mutex);
  const auto &db = databases[current_db];
  std::vector<float> result;

  // 查找匹配的数值数组
  for (const auto &nv : db.numeric_array) {
    if (nv.key == key && nv.valid) {
      // 找到匹配的键，将值复制到结果向量中
      result = nv.values; // 直接赋值整个向量
      break;
    }
  }

  if (result.empty()) {
    std::cout << "No numeric values found for key: " << key << std::endl;
  }

  return result;
}

bool DataStore::rename(const std::string &oldKey, const std::string &newKey) {
  std::cout << "Renaming key: " << oldKey << " to " << newKey << std::endl;
  std::lock_guard<std::mutex> lock(mutex);
  auto &db = databases[current_db];

  // 直接在数据库中查找和修改，避免调用其他加锁方法
  bool found = false;
  std::string value;

  // 检查并获取旧键的值
  for (auto &kv : db.data_array) {
    if (kv.key == oldKey && kv.valid) {
      if (isExpired(oldKey)) {
        kv.valid = false;
        db.metadata.erase(oldKey);
        return false;
      }
      value = kv.value;
      kv.valid = false;
      found = true;
      break;
    }
  }

  if (!found)
    return false;

  // 设置新键值
  bool newKeyExists = false;
  for (auto &kv : db.data_array) {
    if (kv.key == newKey && kv.valid) {
      kv.value = value;
      newKeyExists = true;
      break;
    }
  }

  if (!newKeyExists) {
    KeyValue newKv{newKey, value, true};
    db.data_array.push_back(newKv);
  }

  // 更新元数据
  db.metadata.erase(oldKey);

  std::cout << "Rename completed successfully" << std::endl;
  return true;
}

std::string DataStore::info() const {
  std::lock_guard<std::mutex> lock(mutex);
  std::string result = "# Server\n";
  result += "databases: " + std::to_string(databases.size()) + "\n";
  result += "current_db: " + std::to_string(current_db) + "\n";
  return result;
}

bool DataStore::isExpired(const std::string &key) const {
  const auto &db = databases[current_db];
  auto it = db.metadata.find(key);
  if (it == db.metadata.end() || it->second.expireTime == 0) {
    return false;
  }

  auto now = std::chrono::system_clock::now();
  auto current = std::chrono::duration_cast<std::chrono::milliseconds>(
                     now.time_since_epoch())
                     .count();
  std::cout << "Checking expiration for key: " << key
            << ", expire time: " << it->second.expireTime
            << ", current time: " << current << std::endl;
  return current >= it->second.expireTime;
}

bool DataStore::matchPattern(const std::string &str,
                             const std::string &pattern) {
  // 简单实现，支持 * 通配符
  if (pattern == "*")
    return true;
  // 处理前缀匹配，如 "key*"
  if (pattern.size() > 0 && pattern.back() == '*') {
    std::string prefix = pattern.substr(0, pattern.size() - 1);
    return str.substr(0, prefix.size()) == prefix;
  }

  // 处理后缀匹配，如 "*key"
  if (pattern.size() > 0 && pattern.front() == '*') {
    std::string suffix = pattern.substr(1);
    return str.size() >= suffix.size() &&
           str.substr(str.size() - suffix.size()) == suffix;
  }

  // 处理中间匹配，如 "k*y"
  size_t pos = pattern.find('*');
  if (pos != std::string::npos) {
    std::string prefix = pattern.substr(0, pos);
    std::string suffix = pattern.substr(pos + 1);

    return str.size() >= prefix.size() + suffix.size() &&
           str.substr(0, prefix.size()) == prefix &&
           str.substr(str.size() - suffix.size()) == suffix;
  }
  return str == pattern;
}

// 添加缺失的方法实现
long long DataStore::pttl(const std::string &key) {
  std::lock_guard<std::mutex> lock(mutex);
  auto &db = databases[current_db];
  auto it = db.metadata.find(key);
  std::cout << "Checking if key exists: " << key << std::endl;
  if (it == db.metadata.end() || it->second.expireTime == 0) {
    return -1;
  }

  auto now = std::chrono::system_clock::now();
  // 将时间点转换为可读格式
  auto now_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
                    now.time_since_epoch())
                    .count();
  std::cout << "Getting current time (ms since epoch): " << now_ms << std::endl;

  auto current = std::chrono::duration_cast<std::chrono::milliseconds>(
                     now.time_since_epoch())
                     .count();
  std::cout << "Calculating remaining time..." << current << std::endl;
  return std::max(0LL, it->second.expireTime - current);
}

bool DataStore::watch(const std::string &key) {
  std::lock_guard<std::mutex> lock(mutex);
  if (!current_transaction.active) {
    // 只有在事务中才能执行 WATCH 命令
    return false;
  }

  auto &db = databases[current_db];
  auto it = db.metadata.find(key);
  long long version = (it != db.metadata.end()) ? it->second.version : 0;

  // 检查键是否已经被监视
  for (const auto &watched : current_transaction.watched_keys) {
    if (watched.key == key) {
      return true; // 已经在监视列表中
    }
  }

  // 直接在这里获取键的值，而不是调用 get 方法
  std::string value = "(nil)";
  for (const auto &kv : db.data_array) {
    if (kv.key == key && kv.valid) {
      if (!isExpired(key)) {
        value = kv.value;
      }
      break;
    }
  }

  // 添加到监视列表
  current_transaction.watched_keys.push_back(
      WatchedKey{key,
                 value, // 使用直接获取的值
                 version});

  return true;
}

void DataStore::unwatch() {
  std::lock_guard<std::mutex> lock(mutex);
  current_transaction.watched_keys.clear();
}

bool DataStore::isKeyExists(const std::string &key) const {
  const auto &db = getCurrentDatabase();
  for (const auto &kv : db.data_array) {
    if (kv.key == key && kv.valid) {
      return true;
    }
  }
  return false;
}

// 删除 CommandHandler::handle_command 的实现，因为它属于 CommandHandler.cpp

// 添加缺失的方法实现
// 修改方法实现，使用正确的类型名
DataStore::Database &DataStore::getCurrentDatabase() {
  return databases[current_db];
}

const DataStore::Database &DataStore::getCurrentDatabase() const {
  return databases[current_db];
}

bool DataStore::saveMCDB(const std::string &filename) {
  std::lock_guard<std::mutex> lock(mutex);
  std::ofstream file(filename, std::ios::binary);
  if (!file) {
    std::cerr << "Failed to open file for writing: " << filename << std::endl;
    return false;
  }

  // 写入魔数和版本号
  const char magic[] = "MINCACHE"; // 确保长度为 8
  std::cout << "Writing magic number: " << magic << std::endl;
  file.write(magic, 8);
  file.flush(); // 确保立即写入

  // 写入数据库数量
  uint32_t db_count = databases.size();
  file.write(reinterpret_cast<char *>(&db_count), sizeof(db_count));

  // 写入每个数据库的数据
  for (size_t i = 0; i < databases.size(); ++i) {
    auto &db = databases[i];
    file.write(reinterpret_cast<char *>(&i), sizeof(i));

    uint32_t kv_count = db.data_array.size();
    file.write(reinterpret_cast<char *>(&kv_count), sizeof(kv_count));

    for (const auto &kv : db.data_array) {
      if (!kv.valid)
        continue;

      uint32_t key_len = kv.key.length();
      file.write(reinterpret_cast<char *>(&key_len), sizeof(key_len));
      file.write(kv.key.c_str(), key_len);

      uint32_t value_len = kv.value.length();
      file.write(reinterpret_cast<char *>(&value_len), sizeof(value_len));
      file.write(kv.value.c_str(), value_len);

      auto it = db.metadata.find(kv.key);
      long long expire_time =
          (it != db.metadata.end()) ? it->second.expireTime : 0;
      file.write(reinterpret_cast<char *>(&expire_time), sizeof(expire_time));
    }
    // 写入数值数组
    uint32_t nv_count = 0;
    for (const auto &nv : db.numeric_array) {
      if (nv.valid)
        nv_count++;
    }
    file.write(reinterpret_cast<char *>(&nv_count), sizeof(nv_count));

    for (const auto &nv : db.numeric_array) {
      if (!nv.valid)
        continue;

      // 写入键
      uint32_t key_len = nv.key.length();
      file.write(reinterpret_cast<char *>(&key_len), sizeof(key_len));
      file.write(nv.key.c_str(), key_len);

      // 写入数值数组长度
      uint32_t values_len = nv.values.size();
      file.write(reinterpret_cast<char *>(&values_len), sizeof(values_len));

      // 写入数值数组内容
      if (!nv.values.empty()) {
        file.write(reinterpret_cast<const char *>(nv.values.data()),
                   sizeof(float) * values_len);
      }

      // 写入过期时间
      auto it = db.metadata.find(nv.key);
      long long expire_time =
          (it != db.metadata.end()) ? it->second.expireTime : 0;
      file.write(reinterpret_cast<char *>(&expire_time), sizeof(expire_time));
    }
  }

  return true;
}
