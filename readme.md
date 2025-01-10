## jpa-codegen-jooq

### Main Features
This is a Maven or Gradle plugin designed to generate JOOQ code from JPA entities, eliminating the need for JOOQ to scan the database schema. This approach leverages the strengths of both technologies: JPA simplifies ORM(Object-Relational Mapping) and data migration, while JOOQ provides a powerful SQL builder and query execution framework.

### Usage
``` Come soons ```

### Notes
* Single Data Source: Both JPA and JOOQ should use the same DataSource.
* Avoid Redundant Work:Since both JPA and JOOQ are means of accessing the database, they might lead to redundant work in certain scenarios. For example, if you manage database schema changes using JPA migration tools (such as Flyway or Liquibase), you must ensure these changes are also reflected in the JOOQ code generation process.
* Shared DataSource: Use a shared DataSource to ensure that both frameworks connect to the same database instance.

[中文](readme_zh.md)