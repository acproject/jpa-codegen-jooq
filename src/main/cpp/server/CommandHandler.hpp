// src/server/CommandHandler.hpp
#pragma once
#include "DataStore.hpp"
#include <algorithm>
#include <functional>
#include <unordered_map>
#include <vector>
#include <string>

class CommandHandler {
public:
    using CommandFunction = std::function<std::string(DataStore&, const std::vector<std::string>&)>;
    
    explicit CommandHandler(DataStore& store) : data_store(store) {
        // 注册所有命令
        command_map["MULTI"] = multi_command;
        command_map["EXEC"] = exec_command;
        command_map["DISCARD"] = discard_command;
        command_map["WATCH"] = watch_command;
        command_map["UNWATCH"] = unwatch_command;
        command_map["RENAME"] = rename_command;
        command_map["SCAN"] = scan_command;
        command_map["PING"] = ping_command;
        command_map["SET"] = set_command;
        command_map["GET"] = get_command;
        command_map["DEL"] = del_command;
        command_map["EXISTS"] = exists_command;
        command_map["INCR"] = incr_command;
        command_map["SETNX"] = set_numeric_command;
        command_map["GETNX"] = get_numeric_command;
        command_map["SELECT"] = select_command;
        command_map["PEXPIRE"] = pexpire_command;
        command_map["PTTL"] = pttl_command;
        command_map["SAVE"] = save_command;
        command_map["INFO"] = info_command;
        command_map["KEYS"] = keys_command;
        command_map["FLUSHDB"] = flushdb_command;
        command_map["FLUSHALL"] = flushall_command;
    }

    // 将 handle_command 移到 public 部分
    std::string handle_command(const std::vector<std::string>& args);

private:
    DataStore& data_store;
    std::unordered_map<std::string, CommandFunction> command_map;

    // 工具方法
    static std::string protocol_error(const std::string& msg) {
        return "-ERR " + msg + "\r\n";
    }

    static std::string to_uppercase(std::string str) {
        std::transform(str.begin(), str.end(), str.begin(), ::toupper);
        return str;
    }

    // 声明所有命令处理函数
    static std::string multi_command(DataStore& store, const std::vector<std::string>& args);
    static std::string exec_command(DataStore& store, const std::vector<std::string>& args);
    static std::string discard_command(DataStore& store, const std::vector<std::string>& args);
    static std::string watch_command(DataStore& store, const std::vector<std::string>& args);
    static std::string unwatch_command(DataStore& store, const std::vector<std::string>& args);
    static std::string rename_command(DataStore& store, const std::vector<std::string>& args);
    static std::string scan_command(DataStore& store, const std::vector<std::string>& args);
    static std::string ping_command(DataStore& store, const std::vector<std::string>& args);
    static std::string set_command(DataStore& store, const std::vector<std::string>& args);
    static std::string get_command(DataStore& store, const std::vector<std::string>& args);
    static std::string del_command(DataStore& store, const std::vector<std::string>& args);
    static std::string exists_command(DataStore& store, const std::vector<std::string>& args);
    static std::string incr_command(DataStore& store, const std::vector<std::string>& args);
    static std::string set_numeric_command(DataStore& store, const std::vector<std::string>& args);
    static std::string get_numeric_command(DataStore& store, const std::vector<std::string>& args);
    static std::string select_command(DataStore& store, const std::vector<std::string>& args);
    static std::string pexpire_command(DataStore& store, const std::vector<std::string>& args);
    static std::string pttl_command(DataStore& store, const std::vector<std::string>& args);
    static std::string save_command(DataStore& store, const std::vector<std::string>& args);
    static std::string info_command(DataStore& store, const std::vector<std::string>& args);
    static std::string keys_command(DataStore& store, const std::vector<std::string>& args);
    static std::string flushdb_command(DataStore& store, const std::vector<std::string>& args);
    static std::string flushall_command(DataStore& store, const std::vector<std::string>& args);
};