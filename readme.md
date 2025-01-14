## jpa-codegen-jooq

### Main Features
This is a Maven or Gradle plugin designed to generate JOOQ code from JPA entities, eliminating the need for JOOQ to scan the database schema. This approach leverages the strengths of both technologies: JPA simplifies ORM(Object-Relational Mapping) and data migration, while JOOQ provides a powerful SQL builder and query execution framework.

### Usage
#### Maven
[./examples/maven-demo](./examples/maven-demo)
```shell
# Compile with gradle
gradle clean shadowJar

# Install it locally using Maven
mvn install:install-file \
-Dfile=build/libs/jpa-codegen-jooq-0.2.0-all.jar  \
-DgroupId=com.owiseman \
-DartifactId=jpa-codegen-jooq \
-Dversion=0.2.0 \
-Dpackaging=jar \
-DgeneratePom=true
```

#### Gradle 
If it's Gradle, add the following dependency and codes to build.gradle, and ``libs`` is a folder in the root directory of the project
```gradle usage
compileOnly  files('libs/jpa-codegen-jooq-0.2.0.jar')
compileJava {
	options.compilerArgs += [
			'-processor', 'com.owiseman.jpa.JpaEntityScannerProcessor'
	]
	options.annotationProcessorPath += configurations.annotationProcessor
}
```
Next run:
```shell
gradle compileJava

```

#### Maven
Add the plugin to the pom.xml as follows:
```xml
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
Next run:
```shell
mvn clean compile
```

### Learn how to use it with examples in examples
* [./examples/maven-demo](./examples/maven-demo)
* [./examples/gradle-demo](./examples/gradle-demo)

### reference
https://github.com/c-rainstorm/blog/blob/master/java/code-generate/javapoet.zh.md

### Notes
* Single Data Source: Both JPA and JOOQ should use the same DataSource.
* Avoid Redundant Work:Since both JPA and JOOQ are means of accessing the database, they might lead to redundant work in certain scenarios. For example, if you manage database schema changes using JPA migration tools (such as Flyway or Liquibase), you must ensure these changes are also reflected in the JOOQ code generation process.
* Shared DataSource: Use a shared DataSource to ensure that both frameworks connect to the same database instance.

[中文](readme_zh.md)