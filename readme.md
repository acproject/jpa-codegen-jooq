## jpa-codegen-jooq

### Main Features
This is a Maven or Gradle plugin designed to generate JOOQ code from JPA entities, eliminating the need for JOOQ to scan the database schema. This approach leverages the strengths of both technologies: JPA simplifies ORM(Object-Relational Mapping) and data migration, while JOOQ provides a powerful SQL builder and query execution framework.


### Compile and Install
You need JDK 21 or later and Gradle installed to execute build.gradle scripts
```shell
# Compile with gradle
gradle clean shadowJar
``` 
### Usage
#### Gradle
If it's Gradle, add the following dependency and codes to build.gradle, and ``libs`` is a folder in the root directory of the project
```gradle
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
At present, because there is no central repository for Maven, you need to manually install it locally.
```shell
# Install it locally using Maven
mvn install:install-file \
-Dfile=build/libs/jpa-codegen-jooq-0.2.0-all.jar  \
-DgroupId=com.owiseman \
-DartifactId=jpa-codegen-jooq \
-Dversion=0.2.0 \
-Dpackaging=jar \
-DgeneratePom=true
```
Add the plugin to the pom.xml as follows:
```xml
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
### Manual testing
Here's how to manually compile the Entity type in the example and generate the corresponding JOOQ code.
```shell
# Run the following command in the maven project to get the required classpath path
mvn dependency:build-classpath -Dmdep.outputFile=classpath.txt
# Manually compile and test whether the JpaEntityScannerProcessor takes effect by running the javac command
javac -cp $(cat classpath.txt):target/jpa-codegen-jooq-0.2.0-all.jar
      -processor com.owiseman.jpa.JpaEntityScannerProcessor \
      -d target/classes \
      src/main/java/com/example/entity/Students.java
```

### Learn how to use it with examples in examples
* [./examples](./examples)

### reference
https://github.com/c-rainstorm/blog/blob/master/java/code-generate/javapoet.zh.md

### Notes
* Single Data Source: Both JPA and JOOQ should use the same DataSource.
* Avoid Redundant Work:Since both JPA and JOOQ are means of accessing the database, they might lead to redundant work in certain scenarios. For example, if you manage database schema changes using JPA migration tools (such as Flyway or Liquibase), you must ensure these changes are also reflected in the JOOQ code generation process.
* Shared DataSource: Use a shared DataSource to ensure that both frameworks connect to the same database instance.

[中文](readme_zh.md)