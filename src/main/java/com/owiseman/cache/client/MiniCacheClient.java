package com.owiseman.cache.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class MiniCacheClient {
    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;

    public void connection(String host, int port) throws IOException {
        socket = new Socket(host, port);
        reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream()));
        writer = new PrintWriter(socket.getOutputStream(), true);
    }

    public String set(String key, String value) throws IOException {
        String command = encodeCommand("SET", key, value);
        writer.println(command);
        return readResponse();
    }

    public String get(String key) throws IOException {
        String command = encodeCommand("GET", key);
        writer.println(command);
        return readResponse();
    }

    private String encodeCommand(String... parts) {
        StringBuilder sb = new StringBuilder();
        sb.append("*").append(parts.length).append("\r\n");
        for (String part : parts) {
            sb.append("$").append(part.length()).append("\r\n");
            sb.append(part).append("\r\n");
        }
        return sb.toString();
    }

    private String readResponse() throws IOException {
        char type = (char) reader.read();
        String line;
        
        switch (type) {
            case '+' -> {  // 简单字符串
                return reader.readLine();
            }
            case '-' -> {  // 错误消息
                return "ERROR: " + reader.readLine();
            }
            case ':' -> {  // 整数
                return reader.readLine();
            }
            case '$' -> {  // 批量字符串
                line = reader.readLine();
                int length = Integer.parseInt(line);
                if (length == -1) {
                    return null;
                }
                char[] data = new char[length];
                reader.read(data, 0, length);
                reader.readLine(); // 消耗CRLF
                return new String(data);
            }
            case '*' -> {  // 数组
                line = reader.readLine();
                int count = Integer.parseInt(line);
                if (count == -1) {
                    return null;
                }
                StringBuilder result = new StringBuilder();
                for (int i = 0; i < count; i++) {
                    if (i > 0) result.append(", ");
                    result.append(readResponse());
                }
                return "[" + result + "]";
            }
            default -> throw new IOException("Unknown response type: " + type);
        }
    }

    public void close() throws IOException {
        socket.close();
        reader.close();
        writer.close();
    }

    private static class Example {
        public static void main(String[] args) throws IOException {
            MiniCacheClient client = new MiniCacheClient();
            client.connection("127.0.0.1", 6379);

            // 测试 PING 命令
            System.out.println("\n=== Testing PING ===");
            String pingResp = client.sendCommand("PING");
            System.out.println("PING response: " + pingResp);

            // 测试 SET 和 GET 命令
            System.out.println("\n=== Testing SET/GET ===");
            client.set("test", "Hello World");
            String value = client.get("test");
            System.out.println("GET test: " + value);

            // 测试 EXISTS 命令
            System.out.println("\n=== Testing EXISTS ===");
            String existsResp = client.sendCommand("EXISTS", "test");
            System.out.println("EXISTS test: " + existsResp);

            // 测试 DEL 命令
            System.out.println("\n=== Testing DEL ===");
            String delResp = client.sendCommand("DEL", "test");
            System.out.println("DEL test: " + delResp);
            System.out.println("GET after DEL: " + client.get("test"));

            // 测试 INCR 命令
            System.out.println("\n=== Testing INCR ===");
            client.set("counter", "10");
            String incrResp = client.sendCommand("INCR", "counter");
            System.out.println("INCR counter: " + incrResp);
            System.out.println("GET counter: " + client.get("counter"));

            // 测试 SETNX 和 GETNX 命令（数值数组操作）
            System.out.println("\n=== Testing SETNX/GETNX ===");
            String setnxResp = client.sendCommand("SETNX", "numbers", "1.0", "2.0", "3.0", "4.0");
            System.out.println("SETNX response: " + setnxResp);
            String getnxResp = client.sendCommand("GETNX", "numbers");
            System.out.println("GETNX response: " + getnxResp);

            // 测试不存在的键
            System.out.println("\n=== Testing non-existent key ===");
            String nonExistValue = client.get("nonexistent");
            System.out.println("GET nonexistent: " + nonExistValue);

            // 测试事务命令
            System.out.println("\n=== Testing Transaction ===");
            System.out.println("MULTI: " + client.sendCommand("MULTI"));
            System.out.println("SET in transaction: " + client.sendCommand("SET", "tx_key", "tx_value"));
            System.out.println("EXEC: " + client.sendCommand("EXEC"));
            System.out.println("GET after transaction: " + client.get("tx_key"));

            // 测试 WATCH/UNWATCH 命令
            System.out.println("\n=== Testing WATCH/UNWATCH ===");
            System.out.println("WATCH: " + client.sendCommand("WATCH", "watch_key"));
            System.out.println("UNWATCH: " + client.sendCommand("UNWATCH"));

            // 测试数据库选择
            System.out.println("\n=== Testing SELECT ===");
            System.out.println("SELECT db 1: " + client.sendCommand("SELECT", "1"));
            client.set("db1_key", "db1_value");
            System.out.println("GET from db 1: " + client.get("db1_key"));
            System.out.println("SELECT back to db 0: " + client.sendCommand("SELECT", "0"));

            // 测试过期时间
            System.out.println("\n=== Testing Expiration ===");
            client.set("expire_key", "will_expire");
            System.out.println("PEXPIRE: " + client.sendCommand("PEXPIRE", "expire_key", "5000"));
            System.out.println("PTTL: " + client.sendCommand("PTTL", "expire_key"));

            // 测试键空间操作
            System.out.println("\n=== Testing Key Space Operations ===");
            client.set("key1", "value1");
            client.set("key2", "value2");
            System.out.println("KEYS *: " + client.sendCommand("KEYS", "*"));
            System.out.println("SCAN key*: " + client.sendCommand("SCAN", "key*"));

            // 测试重命名
            System.out.println("\n=== Testing RENAME ===");
            System.out.println("RENAME key1 to new_key1: " + 
                client.sendCommand("RENAME", "key1", "new_key1"));
            System.out.println("GET after rename: " + client.get("new_key1"));

            // 测试服务器信息
            System.out.println("\n=== Testing Server Info ===");
            System.out.println("INFO: " + client.sendCommand("INFO"));

            // 测试持久化
            System.out.println("\n=== Testing Persistence ===");
            System.out.println("SAVE: " + client.sendCommand("SAVE"));
            System.out.println("RDB file location: " + System.getProperty("user.dir") + "/dump.rdb");

            // 测试数据库清理
            System.out.println("\n=== Testing Database Cleaning ===");
            System.out.println("FLUSHDB: " + client.sendCommand("FLUSHDB"));
            System.out.println("KEYS after FLUSHDB: " + client.sendCommand("KEYS", "*"));
            
            // 测试全局清理
            System.out.println("\n=== Testing Global Cleaning ===");
            System.out.println("FLUSHALL: " + client.sendCommand("FLUSHALL"));

            client.close();
        }
    }

    // 添加通用命令发送方法
    public String sendCommand(String... parts) throws IOException {
        String command = encodeCommand(parts);
        writer.println(command);
        return readResponse();
    }
}


