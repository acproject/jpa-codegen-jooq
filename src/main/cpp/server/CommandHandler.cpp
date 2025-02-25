#include "CommandHandler.hpp"

// 基础命令
std::string CommandHandler::set_command(DataStore& store, const std::vector<std::string>& args) {
    if(args.size() < 3) return protocol_error("Wrong number of arguments");
    store.set(args[1], args[2]);
    return "+OK\r\n";
}

std::string CommandHandler::get_command(DataStore& store, const std::vector<std::string>& args) {
    if(args.size() != 2) return protocol_error("Wrong number of arguments");
    std::string value = store.get(args[1]);
    if(value == "(nil)") return "$-1\r\n";
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
    if (store.exec()) {
        return "+OK\r\n";
    }
    return "-ERR Transaction failed\r\n";
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
    }
    return "-ERR Save failed\r\n";
}

std::string CommandHandler::info_command(DataStore& store, const std::vector<std::string>& args) {
    std::string info = store.info();
    return "$" + std::to_string(info.length()) + "\r\n" + info + "\r\n";
}

// 过期命令
std::string CommandHandler::pexpire_command(DataStore& store, const std::vector<std::string>& args) {
    if(args.size() != 3) return protocol_error("Wrong number of arguments");
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
    if(args.size() < 6) return protocol_error("Wrong number of arguments");
    std::vector<float> values;
    for(int i = 2; i < 6; i++) {
        values.push_back(std::stof(args[i]));
    }
    if (store.set_numeric(args[1], values)) {
        return "+OK\r\n";
    }
    return protocol_error("Failed to set numeric value");
}

std::string CommandHandler::get_numeric_command(DataStore& store, const std::vector<std::string>& args) {
    if(args.size() != 2) return protocol_error("Wrong number of arguments");
    auto values = store.get_numeric(args[1]);
    if(values.empty()) return "$-1\r\n";
    
    std::string result = "*4\r\n";
    for(float value : values) {
        result += ":" + std::to_string(value) + "\r\n";
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