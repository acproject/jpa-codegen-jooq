package com.owiseman.jpa.cache.client;

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
        switch (type) {
            case '+' -> reader.readLine();
            case '$' -> {
                int length = Integer.parseInt(reader.readLine());
                if (length == -1) {
                    return null;
                }
                char[] data = new char[length];
                reader.read(data, 0, length);
                reader.readLine(); // 消耗CRLF
                return new String(data);
            }
            // 其他类型的响应 todo
        }
        return null;
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

            // 设置键值对
            client.set("test", "Hello World");

            // 获取刚才设置的键
            String value = client.get("test");  // 修改这里，使用正确的键名
            System.out.println("Value for test: " + value);

            // 测试不存在的键
            String nonExistValue = client.get("nonexistent");
            System.out.println("Value for nonexistent: " + nonExistValue);

            client.close();
        }
    }
}


