## jpa-codegen-jooq 

### 主要功能
这是一个Maven或Gradle插件，旨在从JPA实体生成JOOQ代码，从而避免JOOQ扫描数据库模式的需求。这种方法结合了两种技术的优势：JPA简化了ORM（对象关系映射）和数据迁移，而JOOQ则提供了一个强大的SQL构建器和查询执行框架。
当然，你也可以当做是JOOQ的一个辅助工具类，它可以通过自带的工具类，实现复杂的查询，基本的增删改查都可以完成。

### 本地编译与安装
你需要JDK 21或者以上版本，并且安装了Gradle用于执行build.gradle脚本
```shell
# 先用gradle编译
gradle clean shadowJar
```

### 使用方法
**创建新项目后：**
#### Gradle
如果是Gradle在新项目的build.gradle中添加如下依赖``libs``是项目根目录下的一个文件夹
```gradle
compileOnly  files('libs/jpa-codegen-jooq-0.2.2.jar')
compileJava {
	options.compilerArgs += [
			'-processor', 'com.owiseman.jpa.JpaEntityScannerProcessor'
	]
	options.annotationProcessorPath += configurations.annotationProcessor
}
```
下一步运行：
```shell
gradle compileJava
```

#### Maven
目前因为没有上Maven的中央仓库，所以需要手动安装到本地。
```shell
# Install it locally using Maven
mvn install:install-file \
-Dfile=build/libs/jpa-codegen-jooq-0.2.3-all.jar  \
-DgroupId=com.owiseman \
-DartifactId=jpa-codegen-jooq \
-Dversion=0.2.3 \
-Dpackaging=jar \
-DgeneratePom=true
```

在新项目的pom.xml中添加插件内容如下：
```pom.xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                    <configuration>
                        <annotationProcessorPaths>
                            <path>
                                <groupId>com.owiseman</groupId>
                                <artifactId>jpa-codegen-jooq</artifactId>
                                <version>0.2.2</version>
                            </path>
                        </annotationProcessorPaths>
                        <compilerArgs>
                            <arg>-processor</arg>
                            <arg>com.owiseman.jpa.JpaEntityScannerProcessor</arg>
                        </compilerArgs>
                    </configuration>
        </plugin>
    </plugins>
</build>
```
下一步运行：
```shell
mvn clean compile
```
### 手动测试
下面是通过手动方式去编译例子中的Entity类型，并且生成对应的JOOQ代码
```shell
# 在maven项目中运行下面的命令，获得需要的classpath路径
mvn dependency:build-classpath -Dmdep.outputFile=classpath.txt
# 通过javac命令手动编译测试JpaEntityScannerProcessor是否生效
javac -cp $(cat classpath.txt):target/jpa-codegen-jooq-0.2.2-all.jar
      -processor com.owiseman.jpa.JpaEntityScannerProcessor \
      -d target/classes \
      src/main/java/com/example/entity/Students.java
```

## 使用自带的JOOQ工具类

### 通过examples中的例子了解使用方法
[./examples](./examples)

### 注意事项
* 单一数据源：JPA与JOOQ应当使用同一数据源
* 避免重复工作：由于JPA和JOOQ都是访问数据库的手段，在某些场景下可能会导致重复工作。例如，如果您使用JPA迁移工具（如Flyway或Liquibase）管理数据库模式变更，则必须确保这些变更也同步到JOOQ的代码生成过程中。
* 共享数据源：使用共享的数据源，以确保两个框架连接到同一个数据库实例。

## 新增特性
添加了对Jooq对象的操作工具类，基础用法很简单

### 示例1 ： 在Spring中使用
```java
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.jooq.DSLContext;

@Service
public class MyService {

    @Autowired
    private DSLContext dslContext;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    public void processRequest(String json) throws Exception {
        TableAndDataUtil.processRequest(dslContext, rabbitTemplate, json);
    }
}
```

### 示例2 ： 在非Spring中使用
```java
import org.jooq.impl.DSL;
import org.jooq.DSLContext;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import java.sql.Connection;
import java.sql.DriverManager;

public class Main {
    public static void main(String[] args) throws Exception {
        // 创建 DSLContext
        Connection connection = DriverManager.getConnection("jdbc:postgresql://localhost:5432/main_db", "user", "password");
        DSLContext dslContext = DSL.using(connection);

        // 创建 RabbitTemplate
        RabbitTemplate rabbitTemplate = new RabbitTemplate();
        rabbitTemplate.setConnectionFactory(new ConnectionFactory());

        // 处理请求
        String json = "{ \"operation\": \"insert\", \"table\": \"users\", \"data\": { \"id\": 1, \"name\": \"John Doe\", \"email\": \"john.doe@example.com\" } }";
        TableAndDataUtil.processRequest(dslContext, rabbitTemplate, json);
    }
}
```

### 新增SQL脚本生成
生成路径：
```txt
   target/classes/schema/schemas.sql (Maven)
   build/resources/main/schema/schemas.sql (Gradle)
```
这里以maven项目为例：在build节点插入下面的代码：
```pom.xml
<build>
    <resources>
        <resource>
            <directory>${project.build.outputDirectory}/schema</directory>
            <targetPath>schema</targetPath>
        </resource>
    </resources>
</build>
```
## 新增内置缓存服务
目前该服务还不支持windows，linux和mac os已经可以使用
```shell
mini_cache_server --port 6379
```
### Java客户端使用示例
```java
import com.owiseman.jpa.cache.client;

 private class Example {
        public static void main(String[] args) throws IOException {
            MiniCacheClient client = new MiniCacheClient();
            client.connection("127.0.0.1", 6379);

            // 设置键值对
            client.set("test", "Hello World");

            // 获取刚才设置的键
            String value = client.get("test");  // 使用键名
            System.out.println("Value for test: " + value);

            // 测试不存在的键
            String nonExistValue = client.get("nonexistent");
            System.out.println("Value for nonexistent: " + nonExistValue);

            client.close();
        }
    }
```


### 将要实现的功能
* [ ] 内置消息队列功能
* [ ] 轻量级内置缓存功能 

### 小技巧
如果你是docker部署的Postgres数据库可以这样来备份数据库
```shell
docker exec -it my-postgres-container pg_dumpall -U postgres > backup.sql
```

### 参考
https://github.com/c-rainstorm/blog/blob/master/java/code-generate/javapoet.zh.md

[English](readme.md)