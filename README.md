# integrate-boot

A foundational framework for business systems, encapsulating core capabilities.

## Modules

```
integrate-boot
├── bom                          # BOM (Bill of Materials) — dependency version management
└── module
    └── integrate-boot-data      # Data-access integration (MyBatis-Flex + Spring Boot)
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
