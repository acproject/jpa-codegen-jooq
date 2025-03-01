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