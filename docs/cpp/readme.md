# Mini Cache Server (MCS)开发文档指南

## 分析：

## 兼容性
- 保持了与 RESP 协议的兼容性
- 利用C++标准库的线程安全机制，使用C++标准库的线程池机制，使用C++标准库的智能指针机制，使用C++标准库的智能锁机制
- 考虑了GPU或者并行指令集的巨大优势
- 考虑了数值类型的优化存储结构

### 是否使用SDS数据结构和Redis中常见的数据结构？
对于C++版本模仿Redis实现，实际上不需要专门实现SDS数据结构，因为C++已经提供了功能强大且安全的 std::string 类。原因：

1. 为什么Redis需要SDS？
- 字符串长度获取：C语言字符串需要遍历才能获取长度，而SDS可以O(1)获取
- 防止缓冲区溢出：C语言字符串不记录容量信息，容易溢出
- 减少内存重分配：SDS采用预分配策略
- 二进制安全：C字符串以'\0'结尾，不能保存二进制数据
2. 为什么C++不需要SDS？ std::string 已经提供了这些特性：
- O(1)时间复杂度获取长度： string::size()
- 自动内存管理和边界检查
- 内存预分配策略
- 二进制安全：可以存储任意字符，包括'\0'
### 性能优化建议 ：
- 使用 string::reserve() 预分配内存
- 使用 std::string_view 避免不必要的字符串拷贝
- 适当时候使用 shrink_to_fit() 释放多余内存
- 考虑使用移动语义( std::move )优化字符串传递

### Redis数据结构的对应关系 Redis中的数据结构在C++中都有对应的替代方案：
- 链表 ：使用 std::list
- 字典 ：使用 std::unordered_map
- 跳跃表 ：使用 std::set 或 std::map （如果需要排序功能）
- 整数集合 ：使用 std::set<int>
- 压缩列表 ：可以使用 std::vector 配合自定义压缩算法
- 对象系统 ：可以使用C++的类继承体系
### 优化 (todo)
- 添加列表操作（LPUSH/RPUSH/LPOP/RPOP等）
- 添加集合操作（SADD/SREM/SISMEMBER等）

### 考虑到GPU或者并行指令集的巨大优势 （这部分已经做了一些）
1. 连续内存数据结构
- 最适合：数组、向量等连续存储的数据结构
- 原因：GPU的内存访问模式favors coalesced memory access（合并访问） 

2. GPU友好的数据结构特点 ：
- 固定大小的数组
- Structure of Arrays (SoA) 而不是 Array of Structures (AoS)
- 避免指针和动态内存分配
- 避免复杂的树形结构
例如：
```cpp
// 不推荐的AoS方式
struct Record {
    float x, y, z;
    int data;
};
std::vector<Record> records;

// 推荐的SoA方式
struct Records {
    std::vector<float> x;
    std::vector<float> y;
    std::vector<float> z;
    std::vector<int> data;
};
```
3. 内存对齐考虑(已经实现基础版本)
考虑到缓存基本上都是动态字符的处理，所以实现数值类型的更加有效。
参考：
```cpp
 // 针对数值类型的优化存储结构
    struct alignas(16) NumericValue {
        float values[4];  // 适合SIMD运算的4个float
        bool valid;
        
        NumericValue() : values{0}, valid(true) {}
    };
```
这种优化主要适用于需要进行大量数值计算的场景,字符串类型的数据不适合这种对齐优化

4. 避免的数据结构 ：
- 链表
- 树
- 哈希表
- 其他需要大量指针操作的结构
### 由于增加了数值类型，需要对命令增加下面操作：
- 添加了两个新命令：SETNX（设置数值）和 GETNX（获取数值）
- 新命令专门处理 NumericValue 类型的数据
- 保持了与 RESP 协议的兼容性

### RDB的持久化
RDB持久化是Redis中的一个重要功能，它允许Redis将内存中的数据结构持久化到磁盘上，以便在Redis崩溃后恢复数据。
在MCS中，RDB持久化功能已经实现，具体实现步骤如下：
1. 创建一个名为`dump.rdb`的文件，用于存储持久化数据。
2. 