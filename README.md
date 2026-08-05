# integrate-boot

A foundational framework for business systems, encapsulating core capabilities.

## Modules

```
integrate-boot
├── bom                          # BOM (Bill of Materials) — dependency version management
├── module
│   ├── integrate-boot-data      # Data-access integration (MyBatis-Flex + Spring Boot)
│   ├── integrate-boot-cache     # Local cache integration (Caffeine + Spring Cache)
│   └── integrate-boot-starter   # Bootstrap entry: @IntegrateBoot annotation + aggregated deps
└── test
    └── integrate-boot-test      # Sample app exercising the modules end-to-end
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

### Dynamic (multi) datasource

Dynamic datasource is backed by MyBatis-Flex's built-in `FlexDataSource`. Opt in with a
single switch, then declare each datasource under `mybatis-flex.datasource.<key>.*`:

```yaml
integrate-boot:
  data:
    datasource:
      dynamic:
        enabled: true

mybatis-flex:
  datasource:
    master:                              # first entry is the default datasource
      url: jdbc:mysql://host/db1
      username: root
      password: secret
    slave:
      url: jdbc:mysql://host/db2
      username: root
      password: secret
```

Switch datasources programmatically or with an annotation:

```java
// programmatic — scoped to the lambda, restores the default afterwards
List<User> rows = DataSourceKey.use("slave", () -> userMapper.selectAll());

// declarative — on a mapper interface or method
@UseDataSource("slave")
public interface SlaveMapper extends BaseMapper<SlaveEntity> { }
```

If the switch is on but no `mybatis-flex.datasource.*` is configured, the app fails fast
with a clear message. Leave the switch off (the default) for single-datasource apps — their
behaviour is unchanged.

## integrate-boot-cache

Local cache module based on **Caffeine** + Spring's cache abstraction. It auto-configures two
ready-to-use `CaffeineCacheManager` beans, so a service gets a sensible local cache without
writing any wiring code:

| Bean name              | Expiry                       | Notes                                         |
|------------------------|------------------------------|-----------------------------------------------|
| `cacheManagerPermanent`| none                         | Bounded only by `maximum-size`; reference data |
| `cacheManagerExpiring` | `expire-after-write` (2m default), `@Primary` | The manager plain `@Cacheable` resolves to |

### What you get out of the box

- Two Caffeine-backed `CacheManager` beans, addressable by name
- A `@Primary` manager with a bounded TTL, so declarative caching is safe by default
- Tunable specs for each manager under `integrate-boot.cache.*`
- Null-value caching enabled by default (helps prevent cache penetration)

### Usage

1. Depend on the module (or just use the starter, which aggregates it):

   ```groovy
   dependencies {
       implementation platform('com.github.henc:integrate-boot-bom:0.0.1-SNAPSHOT')
       implementation 'com.github.henc:integrate-boot-cache'
   }
   ```

2. Use `@Cacheable` / `@CacheEvict` directly — `@IntegrateBoot` is already meta-annotated
   with `@EnableCaching`, so declarative caching is on by default:

   ```java
   @IntegrateBoot
   public class MyApplication { ... }

   @Service
   public class UserService {
       // resolves to the @Primary "cacheManagerExpiring"
       @Cacheable("users")
       public User findById(Long id) { ... }

       // pin to the permanent manager for reference data
       @Cacheable(cacheManager = "cacheManagerPermanent", value = "dictionaries")
       public Dictionary loadDict(String code) { ... }
   }
   ```

3. Or use a manager programmatically:

   ```java
   @Autowired
   @Qualifier("cacheManagerPermanent")
   private CacheManager cacheManager;

   cacheManager.getCache("dictionaries").put(code, dict);
   ```

### Configuration

Both managers are tuned under `integrate-boot.cache.*`. Defaults are shown below; only override
what you need:

```yaml
integrate-boot:
  cache:
    cache-null-values: true
    permanent:                    # never expires by time
      initial-capacity: 0
      maximum-size: 10000
    expiring:                     # @Primary, default 2-minute TTL
      initial-capacity: 0
      maximum-size: 10000
      expire-after-write: 2m      # set expire-after-access instead/in addition as needed
```

`expire-after-access` / `expire-after-write` are optional durations; leaving them unset means
"no expiry" for that policy, which is why `permanent` ships without any.

Either manager can be replaced by defining your own bean under the same name — the defaults are
guarded by `@ConditionalOnMissingBean`.

## integrate-boot-starter
Bootstrap entry point that aggregates the data layer and exposes the
`@IntegrateBoot` convenience annotation.

Depending on `integrate-boot-starter` transitively brings in `integrate-boot-data`
(MyBatis-Flex + datasource auto-configuration) and `integrate-boot-cache` (Caffeine + the
local cache managers), so a service only needs one dependency to get the whole stack.

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

## test/integrate-boot-test

Sample application that boots the whole stack with `@IntegrateBoot` over an in-memory H2
database (no external setup) and verifies the modules end-to-end: conventional layer
scanning, MyBatis-Flex mapper access, transactions, and underscore-to-camelCase mapping.

Run the integration tests:

```bash
./gradlew :test:integrate-boot-test:test
```
