## jpa-codegen-jooq 

### 主要功能
这是一个Maven或Gradle插件，旨在从JPA实体生成JOOQ代码，从而避免JOOQ扫描数据库模式的需求。这种方法结合了两种技术的优势：JPA简化了ORM（对象关系映射）和数据迁移，而JOOQ则提供了一个强大的SQL构建器和查询执行框架。

### 使用方法
#### 本地安装编译好的jar包
```shell
mvn install:install-file \
> -Dfile=build/libs/jpa-codegen-jooq-0.2.0.jar  \
> -DgroupId=com.owiseman \
> -DartifactId=jpa-codegen-jooq \
> -Dversion=0.2.0 \
> -Dpackaging=jar \
> -DgeneratePom=true

```

### 注意事项
* 单一数据源：JPA与JOOQ应当使用同一数据源
* 避免重复工作：由于JPA和JOOQ都是访问数据库的手段，在某些场景下可能会导致重复工作。例如，如果您使用JPA迁移工具（如Flyway或Liquibase）管理数据库模式变更，则必须确保这些变更也同步到JOOQ的代码生成过程中。
* 共享数据源：使用共享的数据源，以确保两个框架连接到同一个数据库实例。

[English](readme.md)