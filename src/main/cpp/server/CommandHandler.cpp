#include "CommandHandler.hpp"
#include <string>

// 基础命令
std::string CommandHandler::set_command(DataStore& store, const std::vector<std::string>& args) {
    if(args.size() < 3) return protocol_error("Wrong number of arguments");
    store.set(args[1], args[2]);
    return "+OK\r\n";
}

std::string CommandHandler::get_command(DataStore& store, const std::vector<std::string>& args) {
    if (args.size() != 2) {
        return "-ERR wrong number of arguments for 'get' command\r\n";
    }
    std::string value = store.get(args[1]);  // 使用传入的 store 而不是 dataStore
    if (value == "(nil)") {
        return "$-1\r\n";  // 返回 nil 值
    }
    return "$" + std::to_string(value.length()) + "\r\n" + value + "\r\n";
}

std::string CommandHandler::del_command(DataStore& store, const std::vector<std::string>& args) {
    if(args.size() < 2) return protocol_error("Wrong number of arguments");
    int deleted = 0;
    for(size_t i = 1; i < args.size(); ++i) {
        deleted += store.del(args[i]);
    }
    return ":" + std::to_string(deleted) + "\r\n";
}

// 事务命令
std::string CommandHandler::multi_command(DataStore& store, const std::vector<std::string>& args) {
    store.multi();
    return "+OK\r\n";
}

std::string CommandHandler::exec_command(DataStore& store, const std::vector<std::string>& args) {
    auto results = store.exec();
    if (!results) {
        return "-ERR Transaction failed\r\n";
    }else {
        return "+OK\r\n";
    }
}

std::string CommandHandler::discard_command(DataStore& store, const std::vector<std::string>& args) {
    store.discard();
    return "+OK\r\n";
}

std::string CommandHandler::watch_command(DataStore& store, const std::vector<std::string>& args) {
    if(args.size() != 2) return protocol_error("Wrong number of arguments");
    if (store.watch(args[1])) {
        return "+OK\r\n";
    }
    return "-ERR Watch failed\r\n";
}

std::string CommandHandler::unwatch_command(DataStore& store, const std::vector<std::string>& args) {
    store.unwatch();
    return "+OK\r\n";
}

// 数据库管理命令
std::string CommandHandler::select_command(DataStore& store, const std::vector<std::string>& args) {
    if(args.size() != 2) return protocol_error("Wrong number of arguments");
    int index = std::stoi(args[1]);
    if (store.select(index)) {
        return "+OK\r\n";
    }
    return protocol_error("Invalid DB index");
}

std::string CommandHandler::flushdb_command(DataStore& store, const std::vector<std::string>& args) {
    if (args.size() != 1) {
        return protocol_error("Wrong number of arguments");
    }
    
    store.flushdb();
    return "+OK\r\n";
}

std::string CommandHandler::flushall_command(DataStore& store, const std::vector<std::string>& args) {
    store.flushall();
    return "+OK\r\n";
}

// 键空间命令
std::string CommandHandler::exists_command(DataStore& store, const std::vector<std::string>& args) {
    if(args.size() < 2) return protocol_error("Wrong number of arguments");
    int count = 0;
    for(size_t i = 1; i < args.size(); ++i) {
        count += store.exists(args[i]);
    }
    return ":" + std::to_string(count) + "\r\n";
}

std::string CommandHandler::keys_command(DataStore& store, const std::vector<std::string>& args) {
    if(args.size() != 2) return protocol_error("Wrong number of arguments");
    auto keys = store.keys(args[1]);
    std::string result = "*" + std::to_string(keys.size()) + "\r\n";
    for(const auto& key : keys) {
        result += "$" + std::to_string(key.length()) + "\r\n" + key + "\r\n";
    }
    return result;
}

std::string CommandHandler::scan_command(DataStore& store, const std::vector<std::string>& args) {
    if(args.size() < 2) return protocol_error("Wrong number of arguments");
    size_t count = args.size() > 2 ? std::stoul(args[2]) : 10;
    auto keys = store.scan(args[1], count);
    std::string result = "*" + std::to_string(keys.size()) + "\r\n";
    for(const auto& key : keys) {
        result += "$" + std::to_string(key.length()) + "\r\n" + key + "\r\n";
    }
    return result;
}

// 其他命令
std::string CommandHandler::ping_command(DataStore& store, const std::vector<std::string>& args) {
    return "+PONG\r\n";
}

std::string CommandHandler::save_command(DataStore& store, const std::vector<std::string>& args) {
    if(store.saveRDB("dump.rdb")) {
        return "+OK\r\n";
    } else {
        return "-ERR Failed to save RDB\r\n";
    }
}

std::string CommandHandler::info_command(DataStore& store, const std::vector<std::string>& args) {
    std::string info = store.info();
    return "$" + std::to_string(info.length()) + "\r\n" + info + "\r\n";
}

// 过期命令
std::string CommandHandler::pexpire_command(DataStore& store, const std::vector<std::string>& args) {
    if(args.size() != 3) return protocol_error("Wrong number of arguments");
    // 先检查键是否存在
    if (store.exists(args[1]) == 0) {
        return ":0\r\n"; // 键不存在，返回0
    }
    store.pexpire(args[1], std::stoll(args[2]));
    return ":1\r\n";
}

std::string CommandHandler::pttl_command(DataStore& store, const std::vector<std::string>& args) {
    if(args.size() != 2) return protocol_error("Wrong number of arguments");
    return ":" + std::to_string(store.pttl(args[1])) + "\r\n";
}

// 数值操作命令
std::string CommandHandler::incr_command(DataStore& store, const std::vector<std::string>& args) {
    if(args.size() != 2) return protocol_error("Wrong number of arguments");
    std::string value = store.get(args[1]);
    int num = value == "(nil)" ? 0 : std::stoi(value);
    store.set(args[1], std::to_string(num + 1));
    return ":" + std::to_string(num + 1) + "\r\n";
}

std::string CommandHandler::set_numeric_command(DataStore& store, const std::vector<std::string>& args) {
    if(args.size() != 6) return protocol_error("Wrong number of arguments for SETNX");
    
    try {
        std::vector<float> values;
        for(size_t i = 2; i < 6; ++i) {
            values.push_back(std::stof(args[i]));
        }
        
        if(store.set_numeric(args[1], values)) {
            return "+OK\r\n";
        } else {
            return "-ERR Failed to set numeric values\r\n";
        }
    } catch(const std::exception& e) {
        return "-ERR " + std::string(e.what()) + "\r\n";
    }
}

std::string CommandHandler::get_numeric_command(DataStore& store, const std::vector<std::string>& args) {
    if(args.size() != 2) return protocol_error("Wrong number of arguments for GETNX");
    
    std::vector<float> values = store.get_numeric(args[1]);
    if(values.empty()) {
        return "*0\r\n";
    }
    
    std::string result = "*" + std::to_string(values.size()) + "\r\n";
    for(const auto& val : values) {
        std::string str_val = std::to_string(val);
        // 移除尾部多余的0
        str_val.erase(str_val.find_last_not_of('0') + 1, std::string::npos);
        if(str_val.back() == '.') str_val.pop_back();
        
        result += "$" + std::to_string(str_val.length()) + "\r\n" + str_val + "\r\n";
    }
    return result;
}

// 键重命名命令
std::string CommandHandler::rename_command(DataStore& store, const std::vector<std::string>& args) {
    if(args.size() != 3) return protocol_error("Wrong number of arguments");
    if (store.rename(args[1], args[2])) {
        return "+OK\r\n";
    }
    return protocol_error("No such key");
}

std::string CommandHandler::handle_command(const std::vector<std::string>& args) {
    if(args.empty()) return protocol_error("Empty command");
    
    const std::string& cmd = args[0];
    
    // 直接处理一些常用命令，提高性能
    if (cmd == "GET" || cmd == "get") {
        if (args.size() != 2) {
            return "-ERR wrong number of arguments for 'get' command\r\n";
        }
        std::string value = data_store.get(args[1]);
        if (value == "(nil)") {
            return "$-1\r\n";  // 返回 nil 值
        }
        return "$" + std::to_string(value.length()) + "\r\n" + value + "\r\n";
    }
    
    auto it = command_map.find(to_uppercase(cmd));
    
    if(it == command_map.end()) {
        return protocol_error("Unknown command");
    }
    
    try {
        return it->second(data_store, args);
    } catch(const std::exception& e) {
        return protocol_error(e.what());
    }
}

// 删除 to_uppercase 函数的实现，因为它已经在头文件中定义