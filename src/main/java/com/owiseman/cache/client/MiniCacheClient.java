package com.owiseman.cache.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class MiniCacheClient implements AutoCloseable {
    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private volatile boolean isClosed = false;

    @Override
    public void close() throws IOException {
        if (!isClosed) {
            isClosed = true;
            if (socket != null) socket.close();
            if (reader != null) reader.close();
            if (writer != null) writer.close();
        }
    }

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
        int typeInt = reader.read();
        if (typeInt == -1) {
            throw new IOException("Connection closed by server");
        }
        char type = (char) typeInt;
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
                if (line == null) {
                    throw new IOException("Unexpected end of stream");
                }
                int length = Integer.parseInt(line);
                if (length == -1) {
                    return null;
                }
                char[] data = new char[length];
                int read = reader.read(data, 0, length);
                if (read != length) {
                    throw new IOException("Incomplete read: expected " + length + " bytes, got " + read);
                }
                reader.readLine(); // 消耗CRLF
                return new String(data);
            }
            case '*' -> {  // 数组
                line = reader.readLine();
                if (line == null) {
                    throw new IOException("Unexpected end of stream");
                }
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
            default -> throw new IOException("Unknown response type: " + type + " (code: " + typeInt + ")");
        }
    }


    private static class Example {
        public static void main(String[] args) throws IOException {
            MiniCacheClient client = new MiniCacheClient();
            client.connection("127.0.0.1", 6379);

            // 测试持久化
            System.out.println("\n=== Testing Persistence ===");
            System.out.println("SAVE: " + client.sendCommand("SAVE"));
            System.out.println("RDB file location: " + System.getProperty("user.dir") + "/dump.rdb");
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
            client.sendCommand("SET", "tx_key", "tx_value");  // 不打印结果，只发送命令
            String execResp = client.sendCommand("EXEC");     // EXEC 会返回所有命令的结果
            System.out.println("EXEC results: " + execResp);
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

    private static class Example2 {
        private static final int MAX_RETRIES = 5;  // Increased from 3 to 5
        private static final int INITIAL_RETRY_DELAY_MS = 1000;
        private static final int MAX_RETRY_DELAY_MS = 5000;

        private static boolean connectWithRetry(MiniCacheClient client, int clientId) {
            int retryDelay = INITIAL_RETRY_DELAY_MS;

            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                try {
                    client.connection("127.0.0.1", 6379);
                    System.out.println("Client " + clientId + " connected successfully");
                    return true;
                } catch (IOException e) {
                    System.err.println("Client " + clientId + " connection attempt " + attempt +
                        " failed: " + e.getMessage());
                    if (attempt < MAX_RETRIES) {
                        try {
                            Thread.sleep(retryDelay);
                            // Exponential backoff with max delay
                            retryDelay = Math.min(retryDelay * 2, MAX_RETRY_DELAY_MS);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return false;
                        }
                    }
                }
            }
            return false;
        }

        private static void runClientTest(int clientId) {
            try (MiniCacheClient client = new MiniCacheClient()) {
                System.out.println("Client " + clientId + " starting...");

                // 使用重试机制建立连接
                if (!connectWithRetry(client, clientId)) {
                    System.err.println("Client " + clientId + " failed to connect after " +
                        MAX_RETRIES + " attempts");
                    return;
                }

                // 基本操作测试
                String key = "key_" + clientId;
                String value = "value_" + clientId;

                // SET/GET 测试
                client.set(key, value);
                String getValue = client.get(key);
                System.out.println("Client " + clientId + " SET/GET: " + getValue);

                // 事务测试
                System.out.println("Client " + clientId + " starting transaction...");
                client.sendCommand("MULTI");
                client.sendCommand("SET", key + "_tx", "tx_" + value);
                String execResp = client.sendCommand("EXEC");
                System.out.println("Client " + clientId + " transaction result: " + execResp);

                // 模拟随机延迟
                Thread.sleep((long) (Math.random() * 1000));

                System.out.println("Client " + clientId + " completed successfully");
            } catch (Exception e) {
                System.err.println("Client " + clientId + " error: " + e.getMessage());
                e.printStackTrace();
            }
        }

        public static void main(String[] args) {
            int numClients = 10; // 同时运行的客户端数量
            Thread[] threads = new Thread[numClients];

            // 创建并启动多个客户端线程
            for (int i = 0; i < numClients; i++) {
                final int clientId = i;
                threads[i] = new Thread(() -> runClientTest(clientId));
                threads[i].start();
            }

            // 等待所有线程完成
            for (Thread thread : threads) {
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    System.err.println("Thread interrupted: " + e.getMessage());
                }
            }

            // 最后进行清理测试
            try (MiniCacheClient cleanupClient = new MiniCacheClient()) {
                cleanupClient.connection("127.0.0.1", 6379);
                System.out.println("\n=== Cleaning up ===");
                System.out.println("FLUSHALL: " + cleanupClient.sendCommand("FLUSHALL"));
            } catch (IOException e) {
                System.err.println("Cleanup error: " + e.getMessage());
            }
        }
    }

        private static class Example3 {
        private static final int MAX_RETRIES = 5;
        private static final int INITIAL_RETRY_DELAY_MS = 1000;
        private static final int MAX_RETRY_DELAY_MS = 5000;

        private static void runFullTest(int clientId) {
            try (MiniCacheClient client = new MiniCacheClient()) {
                System.out.println("Client " + clientId + " starting...");
                
                // 使用重试机制建立连接
                if (!connectWithRetry(client, clientId)) {
                    System.err.println("Client " + clientId + " failed to connect after " + 
                        MAX_RETRIES + " attempts");
                    return;
                }

                // 测试持久化
                System.out.println("\nClient " + clientId + " === Testing Persistence ===");
                System.out.println("Client " + clientId + " SAVE: " + client.sendCommand("SAVE"));

                // 测试 PING 命令
                System.out.println("\nClient " + clientId + " === Testing PING ===");
                String pingResp = client.sendCommand("PING");
                System.out.println("Client " + clientId + " PING response: " + pingResp);

                // 测试 SET 和 GET 命令
                String key = "test_" + clientId;
                String value = "Hello World " + clientId;
                System.out.println("\nClient " + clientId + " === Testing SET/GET ===");
                client.set(key, value);
                String getValue = client.get(key);
                System.out.println("Client " + clientId + " GET test: " + getValue);

                // 测试事务命令
                System.out.println("\nClient " + clientId + " === Testing Transaction ===");
                System.out.println("Client " + clientId + " MULTI: " + client.sendCommand("MULTI"));
                client.sendCommand("SET", key + "_tx", "tx_" + value);
                String execResp = client.sendCommand("EXEC");
                System.out.println("Client " + clientId + " EXEC results: " + execResp);

                // 测试 WATCH/UNWATCH 命令
                System.out.println("\nClient " + clientId + " === Testing WATCH/UNWATCH ===");
                System.out.println("Client " + clientId + " WATCH: " + 
                    client.sendCommand("WATCH", "watch_key_" + clientId));
                System.out.println("Client " + clientId + " UNWATCH: " + client.sendCommand("UNWATCH"));

                // 测试数据库选择
                System.out.println("\nClient " + clientId + " === Testing SELECT ===");
                System.out.println("Client " + clientId + " SELECT db 1: " + 
                    client.sendCommand("SELECT", "1"));
                client.set("db1_key_" + clientId, "db1_value_" + clientId);
                System.out.println("Client " + clientId + " GET from db 1: " + 
                    client.get("db1_key_" + clientId));

                // 模拟随机延迟
                Thread.sleep((long) (Math.random() * 1000));

                System.out.println("Client " + clientId + " completed successfully");
            } catch (Exception e) {
                System.err.println("Client " + clientId + " error: " + e.getMessage());
                e.printStackTrace();
            }
        }

        private static boolean connectWithRetry(MiniCacheClient client, int clientId) {
            int retryDelay = INITIAL_RETRY_DELAY_MS;
            
            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                try {
                    client.connection("127.0.0.1", 6379);
                    System.out.println("Client " + clientId + " connected successfully");
                    return true;
                } catch (IOException e) {
                    System.err.println("Client " + clientId + " connection attempt " + attempt + 
                        " failed: " + e.getMessage());
                    if (attempt < MAX_RETRIES) {
                        try {
                            Thread.sleep(retryDelay);
                            retryDelay = Math.min(retryDelay * 2, MAX_RETRY_DELAY_MS);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return false;
                        }
                    }
                }
            }
            return false;
        }

        public static void main(String[] args) {
            int numClients = 5; // 同时运行的客户端数量
            Thread[] threads = new Thread[numClients];

            // 创建并启动多个客户端线程
            for (int i = 0; i < numClients; i++) {
                final int clientId = i;
                threads[i] = new Thread(() -> runFullTest(clientId));
                threads[i].start();
            }

            // 等待所有线程完成
            for (Thread thread : threads) {
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    System.err.println("Thread interrupted: " + e.getMessage());
                }
            }

            // 最后进行清理测试
            try (MiniCacheClient cleanupClient = new MiniCacheClient()) {
                cleanupClient.connection("127.0.0.1", 6379);
                System.out.println("\n=== Final Cleanup ===");
                System.out.println("FLUSHALL: " + cleanupClient.sendCommand("FLUSHALL"));
            } catch (IOException e) {
                System.err.println("Cleanup error: " + e.getMessage());
            }
        }
    }

    // 添加通用命令发送方法
    public String sendCommand(String... parts) throws IOException {
        String command = encodeCommand(parts);
        writer.println(command);
        return readResponse();
    }
}


