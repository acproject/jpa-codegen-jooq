## 测试用例
### 1. 基本连接测试

```sh
MC> PING
MC> SET mykey "Hello World"
MC> GET mykey
MC> EXISTS mykey
MC> DEL mykey
MC> GET mykey
```
### 2.数据库选择测试
```sh
MC> SELECT 1
MC> SET db1key "Database 1 Value"
MC> GET db1key
MC> SELECT 0
MC> GET db1key
MC> SET db0key "Database 0 Value"
MC> SELECT 1
MC> GET db0key
```
### 3.事务测试
```sh
MC> MULTI
MC> SET tx_key1 "Transaction Value 1"
MC> SET tx_key2 "Transaction Value 2"
MC> EXEC
MC> GET tx_key1
MC> GET tx_key2
```

### 4.监视键测试
```sh
MC> SET watch_key "Initial Value"
MC> MULTI
MC> WATCH watch_key
MC> SET watch_key "New Value"
MC> EXEC
MC> GET watch_key
```

### 5.过期时间测试
```sh
MC> SET expire_key "This will expire"
MC> PEXPIRE expire_key 50000
MC> PTTL expire_key
MC> GET expire_key
# 等待5秒后
MC> GET expire_key
```

### 6.键空间操作测试
```sh
MC> FLUSHDB
MC> SET key1 "value1"
MC> SET key2 "value2"
MC> SET key3 "value3"
MC> KEYS *                  
MC> SCAN key*
```

### 7.数值操作测试
```sh
MC> SET counter "10"
MC> INCR counter
MC> GET counter
MC> SETNX numbers 1.0 2.0 3.0 4.0     
MC> GETNX numbers                     
```

### 8.持久化测试
```sh
MC> SET persist_key "This will be saved"
MC> SAVE
# 重启服务器后
MC> GET persist_key
```

### 9.压力测试脚本
```bash
#!/bin/bash

# 并发客户端数量
NUM_CLIENTS=5

# 每个客户端执行的命令数
NUM_COMMANDS=100

for ((i=1; i<=$NUM_CLIENTS; i++)); do
  (
    echo "客户端 $i 开始测试..."
    for ((j=1; j<=$NUM_COMMANDS; j++)); do
      echo "SET key_${i}_${j} value_${i}_${j}" | ./mini_cache_cli -h 127.0.0.1 -p 6379 > /dev/null
      echo "GET key_${i}_${j}" | ./mini_cache_cli -h 127.0.0.1 -p 6379 > /dev/null
    done
    echo "客户端 $i 完成测试"
  ) &
done

wait
echo "所有客户端测试完成"
```
将此脚本保存为 jpa-codegen-jooq/build/bin/stress_test.sh ，然后执行：
```bash
chmod +x jpa-codegen-jooq/build/bin/stress_test.sh
./stress_test.sh
```