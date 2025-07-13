# MiniCache Project

## Overview

MiniCache is a high-performance, cross-platform caching solution that includes both Java-based code generation tools and a C++ caching server implementation.

### Components

1. **JPA-JOOQ Code Generator**: A Maven/Gradle plugin for generating JOOQ code from JPA entities
2. **MiniCache C++ Server**: A high-performance caching server with Redis-compatible protocol
3. **Cross-Platform Build System**: Comprehensive build and dependency management tools

---

## JPA-JOOQ Code Generator

### Main Features
This is a Maven or Gradle plugin designed to generate JOOQ code from JPA entities, eliminating the need for JOOQ to scan the database schema. This approach leverages the strengths of both technologies: JPA simplifies ORM(Object-Relational Mapping) and data migration, while JOOQ provides a powerful SQL builder and query execution framework.


### Compile and Install
You need JDK 21 or later and Gradle installed to execute build.gradle scripts
```shell
# Compile with gradle
gradle clean shadowJar
``` 
### Usage
**Created new project：**
#### Gradle
If it's Gradle, add the following dependency and codes to build.gradle in the new project, and ``libs`` is a folder in the root directory of the project
```gradle
compileOnly  files('libs/jpa-codegen-jooq-0.2.2.jar')
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
-Dfile=build/libs/jpa-codegen-jooq-0.3.3-all.jar  \
-DgroupId=com.owiseman \
-DartifactId=jpa-codegen-jooq \
-Dversion=0.3.3 \
-Dpackaging=jar \
-DgeneratePom=true
```

add the plugin to the pom.xml of the new project, as follows:
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

### Notes
* Single Data Source: Both JPA and JOOQ should use the same DataSource.
* Avoid Redundant Work:Since both JPA and JOOQ are means of accessing the database, they might lead to redundant work in certain scenarios. For example, if you manage database schema changes using JPA migration tools (such as Flyway or Liquibase), you must ensure these changes are also reflected in the JOOQ code generation process.
* Shared DataSource: Use a shared DataSource to ensure that both frameworks connect to the same database instance.

## Use the built-in JOOQ utility
### Learn how to use it in examples
* [./examples](./examples)

## New features
Added an operation tool class for Jooq objects, the basic usage is very simple

### example1 ： Use in Spring
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

### example2 ： Use in non-Spring
```java
import org.jooq.impl.DSL;
import org.jooq.DSLContext;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import java.sql.Connection;
import java.sql.DriverManager;

public class Main {
    public static void main(String[] args) throws Exception {
        // create DSLContext
        Connection connection = DriverManager.getConnection("jdbc:postgresql://localhost:5432/main_db", "user", "password");
        DSLContext dslContext = DSL.using(connection);

        // create RabbitTemplate
        RabbitTemplate rabbitTemplate = new RabbitTemplate();
        rabbitTemplate.setConnectionFactory(new ConnectionFactory());

        // process request
        String json = "{ \"operation\": \"insert\", \"table\": \"users\", \"data\": { \"id\": 1, \"name\": \"John Doe\", \"email\": \"john.doe@example.com\" } }";
        TableAndDataUtil.processRequest(dslContext, rabbitTemplate, json);
    }
}
```

### Added SQL script generation
Spawn Path:
```txt
   target/classes/schema/schemas.sql (Maven)
   build/resources/main/schema/schemas.sql (Gradle)
```
Here, take the maven project as an example: insert the following code in the build node:
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
### The functionality that will be implemented
* [ ] Built-in message queuing functionality
* [ ] Lightweight built-in caching capabilities
### New Support for Apache AGE Graph Database 🚀
The project now supports operations with the Apache AGE graph database using JSON DSL for various graph operations.

#### Supported Graph Database Operations
- **Graph Management**: Create graph, delete graph, get graph statistics
- **Label Management**: Create node labels, create edge labels
- **Cypher Queries**: CREATE, MATCH, MERGE, SET, DELETE
- **Batch Operations**: Batch creation of nodes and edges
- **Data Loading**: Load node and edge data from files
- **Path Queries**: Shortest path, all paths
- **Advanced Features**: Pagination, transactions, complex queries

#### Graph Database Usage Examples

##### 1. Creating a Graph and Basic Operations
##### 2. Querying and Analysis
[Code examples remain unchanged]

##### 3. Path Queries
[Code examples remain unchanged]

#### Prerequisites
Before using the graph database functionality, ensure:
1. PostgreSQL has the Apache AGE extension installed
2. Execute the following SQL to enable AGE:

### Tips
If you're deploying PostgreSQL via Docker, you can back up the database like this:



---

## MiniCache C++ Server

### Features

- **High Performance**: Multi-threaded TCP server with efficient connection handling
- **Redis-Compatible Protocol**: Supports RESP (Redis Serialization Protocol)
- **Cross-Platform**: Runs on Windows, Linux, macOS, and FreeBSD
- **Modular Design**: Separate components for parsing, data storage, and command handling
- **Professional Packaging**: Complete build system with dependency management

### Quick Start

#### Prerequisites

- **CMake** 3.15 or later
- **C++17** compatible compiler
- **Platform-specific dependencies**:
  - Windows: Visual Studio 2019+ or MinGW-w64
  - Linux: GCC 7+ or Clang 6+
  - macOS: Xcode 10+ or Homebrew GCC

#### Building

##### Using Build Scripts (Recommended)

**Linux/macOS/Msys2:**
```bash
# Quick build
./scripts/build_and_package.sh

# Debug build with verbose output
./scripts/build_and_package.sh -v -t Debug

# Clean build and create package
./scripts/build_and_package.sh -c -p

# Msys2-MinGW optimized build
./scripts/build_and_package.sh --msys2 -p
```

**Windows:**
```cmd
REM Quick build
scripts\build_and_package.bat

REM Debug build with verbose output
scripts\build_and_package.bat /v /t Debug

REM Clean build and create package
scripts\build_and_package.bat /c /p

REM MinGW build
scripts\build_and_package.bat /mingw /p
```

##### Manual Build

```bash
# Create build directory
mkdir build && cd build

# Configure
cmake -DCMAKE_BUILD_TYPE=Release ..

# Build
cmake --build . --config Release

# Copy dependencies (Windows/Msys2)
make copy_all_dependencies  # or
../scripts/copy_dependencies.sh mini_cache_server.exe
```

#### Running

```bash
# Start the server
./mini_cache_server

# Connect with client
./mini_cache_cli
```

### Dependency Management

The project includes comprehensive dependency management tools for different platforms:

#### Available Tools

1. **Shell Script** (`scripts/copy_dependencies.sh`)
   - For Msys2-MinGW environments
   - Uses `ldd` for dependency analysis
   - Supports recursive dependency resolution

2. **Windows Batch Script** (`scripts/copy_dependencies.bat`)
   - For native Windows environments
   - Supports both MSVC and MinGW toolchains
   - Uses `dumpbin` or `objdump` for analysis

3. **CMake Script** (`scripts/copy_dependencies.cmake`)
   - Integrated into build system
   - Cross-platform dependency resolution
   - Automatic target dependency copying

#### Usage Examples

```bash
# Copy dependencies with verbose output
./scripts/copy_dependencies.sh -v mini_cache_server.exe

# Dry run to preview dependencies
./scripts/copy_dependencies.sh --dry-run mini_cache_server.exe

# Force overwrite existing files
./scripts/copy_dependencies.sh -f mini_cache_server.exe output_dir

# Add custom search paths
./scripts/copy_dependencies.sh --path /custom/lib mini_cache_server.exe
```

#### CMake Integration

```cmake
# Enable automatic dependency copying
cmake -DAUTO_COPY_DEPENDENCIES=ON ..

# Manual dependency copying
make copy_mini_cache_server_dependencies
make copy_all_dependencies
```

### Packaging and Distribution

The project supports professional packaging with CPack:

```bash
# Create packages for current platform
make package

# Available package types:
# - Windows: NSIS installer, ZIP archive
# - macOS: DragNDrop DMG, TGZ archive
# - Linux: DEB, RPM, TGZ packages
```

#### Package Components

- **Runtime**: Executables and required DLLs
- **Development**: Headers and static libraries
- **Documentation**: User guides and API docs
- **Examples**: Sample configurations and scripts
- **Service**: Linux systemd service files

### Cross-Platform Compatibility

The project includes platform-specific compatibility layers:

- **`platform_compat.h`**: Unified cross-platform types and functions
- **Conditional compilation**: Platform-specific code paths
- **Standardized APIs**: Consistent interface across platforms

### Documentation

Detailed documentation is available:

- **[Cross-Platform Build Guide](CROSS_PLATFORM_BUILD.md)**: Platform-specific build instructions
- **[Dependency Management Guide](DEPENDENCY_MANAGEMENT.md)**: Comprehensive dependency tools usage
- **[Packaging Guide](PACKAGING.md)**: Distribution and packaging instructions

### Development

#### Project Structure

```
src/main/cpp/
├── cli/                 # Command-line client
├── server/              # Core server components
│   ├── ConfigParser.*   # Configuration management
│   ├── RespParser.*     # Redis protocol parser
│   ├── DataStore.*      # Data storage engine
│   ├── CommandHandler.* # Command processing
│   └── TcpServer.*      # Network server
├── conf/                # Configuration files
scripts/                 # Build and utility scripts
├── copy_dependencies.*  # Dependency management
├── build_and_package.*  # Build automation
└── minicache.service.in # Linux service template
```

#### Contributing

1. Follow the existing code style and conventions
2. Test on multiple platforms when possible
3. Update documentation for new features
4. Use the provided build scripts for consistency

### Troubleshooting

#### Common Issues

1. **Missing Dependencies**: Use dependency copy scripts
2. **Build Failures**: Check compiler and CMake versions
3. **Runtime Errors**: Verify all DLLs are in the correct location
4. **Platform Issues**: Refer to platform-specific build guides

#### Getting Help

- Check the documentation in the project root
- Use verbose build modes for detailed output
- Test with dry-run modes before making changes

---

### Reference
https://github.com/c-rainstorm/blog/blob/master/java/code-generate/javapoet.zh.md
[中文](readme_zh.md)