// src/server/CommandHandler.hpp
#pragma once
#include "DataStore.hpp"
#include <algorithm>
#include <functional>
#include <unordered_map>
#include <vector>
#include <string>

class CommandHandler {
private:
    // 工具方法需要在使用之前定义
    static std::string to_uppercase(std::string str) {
        std::transform(str.begin(), str.end(), str.begin(), ::toupper);
        return str;
    }

    static std::string protocol_error(const std::string& msg) {
        return "-ERR " + msg + "\r\n";
    }

    // 添加缺失的 set_command 函数定义
    static std::string set_command(DataStore& store, const std::vector<std::string>& args) {
        if(args.size() < 3) throw std::runtime_error("Wrong number of arguments");
        store.set(args[1], args[2]);
        return "+OK\r\n";
    }

public:
    using CommandFunction = std::function<std::string(DataStore&, const std::vector<std::string>&)>;
    
    CommandHandler(DataStore& store) : data_store(store) {
        // 注册基础命令
        command_map["PING"] = ping_command;
        command_map["SET"] = set_command;
        command_map["GET"] = get_command;
        command_map["DEL"] = del_command;
        command_map["EXISTS"] = exists_command;
        command_map["INCR"] = incr_command;
        command_map["SETNX"] = set_numeric_command;
        command_map["GETNX"] = get_numeric_command;
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

private:
    DataStore& data_store;  // 添加成员变量
    std::unordered_map<std::string, CommandFunction> command_map;  // 添加成员变量

    // 添加 ping_command 函数定义
    static std::string ping_command(DataStore&, const std::vector<std::string>& args) {
        return args.size() > 1 ? 
            "+" + args[1] + "\r\n" : 
            "+PONG\r\n";
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
        auto& data_array = store.data_array;
        
        // 查找key
        for (auto& kv : data_array) {
            if (kv.key == args[1] && kv.valid) {
                try {
                    int value = std::stoi(kv.value);
                    kv.value = std::to_string(value + 1);
                    return ":" + kv.value + "\r\n";
                } catch(...) {
                    throw std::runtime_error("Value is not an integer");
                }
            }
        }
        
        // key不存在，创建新的键值对
        DataStore::KeyValue newKv{args[1], "1", true};
        data_array.push_back(newKv);
        return ":1\r\n";
    }

    // 新增数值类型命令处理函数
    static std::string set_numeric_command(DataStore& store, const std::vector<std::string>& args) {
        if(args.size() < 6) throw std::runtime_error("Wrong number of arguments");
        
        std::unique_lock<std::mutex> lock(store.mutex);
        DataStore::NumericValue value;
        
        try {
            for(int i = 0; i < 4; i++) {
                value.values[i] = std::stof(args[i + 2]);
            }
            
            // 查找并更新或添加新值
            bool found = false;
            for(auto& nv : store.numeric_array) {
                if(nv.valid) {
                    found = true;
                    std::copy(value.values, value.values + 4, nv.values);
                    break;
                }
            }
            
            if(!found) {
                store.numeric_array.push_back(value);
            }
            
            return "+OK\r\n";
        } catch(...) {
            throw std::runtime_error("Invalid numeric value");
        }
    }

    static std::string get_numeric_command(DataStore& store, const std::vector<std::string>& args) {
        if(args.size() != 2) throw std::runtime_error("Wrong number of arguments");
        
        std::unique_lock<std::mutex> lock(store.mutex);
        for(const auto& nv : store.numeric_array) {
            if(nv.valid) {
                std::string result = "*4\r\n";
                for(int i = 0; i < 4; i++) {
                    result += ":" + std::to_string(nv.values[i]) + "\r\n";
                }
                return result;
            }
        }
        return "$-1\r\n";
    }
};