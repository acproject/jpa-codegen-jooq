// src/server/CommandHandler.hpp
#pragma once
#include "DataStore.hpp"
#include <functional>
#include <unordered_map>
#include <vector>
#include <string>

class CommandHandler {
public:
    using CommandFunction = std::function<std::string(DataStore&, const std::vector<std::string>&)>;
    
    CommandHandler(DataStore& store) : data_store(store) {
        // 注册基础命令
        register_command("PING", ping_command);
        register_command("SET", set_command);
        register_command("GET", get_command);
        register_command("DEL", del_command);
        register_command("EXISTS", exists_command);
        register_command("INCR", incr_command);
    }

    std::string handle_command(const std::vector<std::string>& args) {
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

    void register_command(const std::string& name, CommandFunction func) {
        command_map[to_uppercase(name)] = func;
    }

private:
    DataStore& data_store;
    std::unordered_map<std::string, CommandFunction> command_map;

    // 工具方法
    static std::string to_uppercase(std::string str) {
        std::transform(str.begin(), str.end(), str.begin(), ::toupper);
        return str;
    }

    static std::string protocol_error(const std::string& msg) {
        return "-ERR " + msg + "\r\n";
    }

    // 预定义命令处理函数
    static std::string ping_command(DataStore&, const std::vector<std::string>& args) {
        return args.size() > 1 ? 
            "+" + args[1] + "\r\n" : 
            "+PONG\r\n";
    }

    static std::string set_command(DataStore& store, const std::vector<std::string>& args) {
        if(args.size() < 3) throw std::runtime_error("Wrong number of arguments");
        store.set(args[1], args[2]);
        return "+OK\r\n";
    }

    static std::string get_command(DataStore& store, const std::vector<std::string>& args) {
        if(args.size() != 2) throw std::runtime_error("Wrong number of arguments");
        std::string value = store.get(args[1]);
        if(value == "(nil)") return "$-1\r\n";
        return "$" + std::to_string(value.size()) + "\r\n" + value + "\r\n";
    }

    static std::string del_command(DataStore& store, const std::vector<std::string>& args) {
        if(args.size() < 2) throw std::runtime_error("Wrong number of arguments");
        int deleted = 0;
        for(size_t i=1; i<args.size(); ++i) {
            deleted += store.del(args[i]);
        }
        return ":" + std::to_string(deleted) + "\r\n";
    }

    static std::string exists_command(DataStore& store, const std::vector<std::string>& args) {
        if(args.size() < 2) throw std::runtime_error("Wrong number of arguments");
        int exists = 0;
        for(size_t i=1; i<args.size(); ++i) {
            exists += store.exists(args[i]);
        }
        return ":" + std::to_string(exists) + "\r\n";
    }
    static std::string incr_command(DataStore& store, const std::vector<std::string>& args) {
    if(args.size() != 2) throw std::runtime_error("Wrong number of arguments");
    
    std::unique_lock<std::mutex> lock(store.mutex);
    auto& data = store.data;
    auto it = data.find(args[1]);
    
    if(it == data.end()) {
        data[args[1]] = "1";
        return ":1\r\n";
    }
    
    try {
        int value = std::stoi(it->second);
        it->second = std::to_string(value + 1);
        return ":" + it->second + "\r\n";
    } catch(...) {
        throw std::runtime_error("Value is not an integer");
    }
}
};