# integrate-boot

A foundational framework for business systems, encapsulating core capabilities.

## Modules

```
integrate-boot
├── bom                          # BOM (Bill of Materials) — dependency version management
└── module
    ├── integrate-boot-data      # Data-access integration (MyBatis-Flex + Spring Boot)
    └── integrate-boot-starter   # Bootstrap entry: @IntegrateBoot annotation + aggregated deps
```

## Build

This project uses Gradle (Groovy DSL). Use the included wrapper — no local Gradle install required.

```bash
# List all projects
./gradlew projects

# Build everything
./gradlew build
```

## Requirements

- JDK 21
- Gradle 8.14+ (provided via the wrapper)

## integrate-boot-data

Data-access starter based on **MyBatis-Flex** + **Spring Boot**. Add it as a dependency and
you only need to configure a datasource to start writing queries — the data layer is wired
up automatically.

### What you get out of the box

- MyBatis-Flex `SqlSessionFactory` / `SqlSessionTemplate` (from the MyBatis-Flex starter)
- Mapper scanning for any `@Mapper`-annotated interface (no `@MapperScan` needed)
- Transactions via MyBatis-Flex's `FlexTransactionManager` — just use `@Transactional`
- Sensible defaults, e.g. `map-underscore-to-camel-case` enabled so snake_case columns map
  to camelCase fields automatically

### Usage

1. Depend on the module:

   ```groovy
   dependencies {
       implementation platform('com.github.henc:integrate-boot-bom:0.0.1-SNAPSHOT')
       implementation 'com.github.henc:integrate-boot-data'
       // plus your JDBC driver, e.g.:
       runtimeOnly 'org.postgresql:postgresql'
   }
   ```

2. Configure a datasource in `application.yml`:

   ```yaml
   spring:
     datasource:
       url: jdbc:postgresql://localhost:5432/mydb
       username: myuser
       password: secret
       driver-class-name: org.postgresql.Driver
   ```

3. Write a mapper and call it — that's it:

   ```java
   @Mapper
   public interface UserMapper {
       @Select("SELECT * FROM user WHERE id = #{id}")
       User findById(Long id);
   }
   ```

### Optional MyBatis-Flex settings

```yaml
mybatis-flex:
  mapper-locations: classpath*:/mapper/**/*.xml
  type-aliases-package: com.example.domain
  configuration:
    cache-enabled: true
```

Any setting under `mybatis-flex.configuration.*` overrides the defaults applied by this module.

## integrate-boot-starter

Bootstrap entry point that aggregates the data layer and exposes the
`@IntegrateBoot` convenience annotation.

Depending on `integrate-boot-starter` transitively brings in `integrate-boot-data`
(MyBatis-Flex + datasource auto-configuration), so a service only needs one dependency
to get the whole stack.

### Bean location conventions

`@IntegrateBoot` does **not** scan the whole application package. Instead it scans the
conventional layer packages anywhere in the application, so business code is picked up as
long as it follows the layering rules below:

| Layer       | Package convention   | Example                          |
|-------------|----------------------|----------------------------------|
| Controller  | `**.controller.**`   | `xxx.controller.XxxController`   |
| Service     | `**.service.**`      | `xxx.service.XxxService`         |
| Service impl| `**.service.impl.**` | `xxx.service.impl.XxxServiceImpl`|
| Repository  | `**.domain.**`       | `xxx.domain.XxxRepository`       |

### Usage

1. Depend on the starter:

   ```groovy
   dependencies {
       implementation platform('com.github.henc:integrate-boot-bom:0.0.1-SNAPSHOT')
       implementation 'com.github.henc:integrate-boot-starter'
       // plus your JDBC driver, e.g.:
       runtimeOnly 'org.postgresql:postgresql'
   }
   ```

2. Annotate the main class with `@IntegrateBoot` instead of `@SpringBootApplication`:

   ```java
   @IntegrateBoot
   public class MyApplication {
       public static void main(String[] args) {
           SpringApplication.run(MyApplication.class, args);
       }
   }
   ```

3. Lay out business code by the conventions above, e.g.:

   ```
   com.example
   ├── user
   │   ├── controller/UserController.java
   │   ├── service/UserService.java
   │   ├── service/impl/UserServiceImpl.java
   │   └── domain/UserRepository.java
   └── order
       ├── controller/OrderController.java
       └── ...
   ```

4. To disable a specific auto-configuration, use `exclude` / `excludeName`:

   ```java
   @IntegrateBoot(exclude = { RedisAutoConfiguration.class })
   ```

   To scan additional packages beyond the conventions, add a regular `@ComponentScan`
   next to `@IntegrateBoot`, or fall back to `@SpringBootApplication`.

