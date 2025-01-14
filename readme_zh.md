## jpa-codegen-jooq 

### 主要功能
这是一个Maven或Gradle插件，旨在从JPA实体生成JOOQ代码，从而避免JOOQ扫描数据库模式的需求。这种方法结合了两种技术的优势：JPA简化了ORM（对象关系映射）和数据迁移，而JOOQ则提供了一个强大的SQL构建器和查询执行框架。

### 使用方法

#### 本地编译与安装
```shell
# 先用gradle编译
gradle clean shadowJar

# 通过maven安装到本地
mvn install:install-file \
-Dfile=build/libs/jpa-codegen-jooq-0.2.0-all.jar  \
-DgroupId=com.owiseman \
-DartifactId=jpa-codegen-jooq \
-Dversion=0.2.0 \
-Dpackaging=jar \
-DgeneratePom=true

```

### 使用方法
#### Gradle
如果是Gradle在build.gradle中添加如下依赖``libs``是项目根目录下的一个文件夹
```gradle
compileOnly  files('libs/jpa-codegen-jooq-0.2.0.jar')
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
在pom.xml中添加插件内容如下：
```pom.xml
<build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <dependencies>
                    <dependency>
                        <groupId>javax.persistence</groupId>
                        <artifactId>javax.persistence-api</artifactId>
                        <version>2.2</version>
                    </dependency>
                </dependencies>
                <configuration>
                    <annotationProcessorPaths>
                        <path>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </path>
                        <path>
                            <groupId>com.owiseman</groupId>
                            <artifactId>jpa-codegen-jooq</artifactId>
                            <version>0.2.0</version>
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

下面是通过手动方式去编译例子中的Entity类型，并且生成对应的JOOQ代码
```shell
# 在maven项目中运行下面的命令，获得需要的classpath路径
mvn dependency:build-classpath -Dmdep.outputFile=classpath.txt
# 通过javac命令手动编译测试JpaEntityScannerProcessor是否生效
javac -cp $(cat classpath.txt):target/jpa-codegen-jooq-0.2.0-all.jar
      -processor com.owiseman.jpa.JpaEntityScannerProcessor \
      -d target/classes \
      src/main/java/com/example/entity/Students.java
```
#### 通过examples中的例子了解使用方法
[./examples](./examples)

### 注意事项
* 单一数据源：JPA与JOOQ应当使用同一数据源
* 避免重复工作：由于JPA和JOOQ都是访问数据库的手段，在某些场景下可能会导致重复工作。例如，如果您使用JPA迁移工具（如Flyway或Liquibase）管理数据库模式变更，则必须确保这些变更也同步到JOOQ的代码生成过程中。
* 共享数据源：使用共享的数据源，以确保两个框架连接到同一个数据库实例。


### 参考
https://github.com/c-rainstorm/blog/blob/master/java/code-generate/javapoet.zh.md

[English](readme.md)