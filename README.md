# integrate-boot

A foundational framework for business systems, encapsulating core capabilities.

## Modules

```
integrate-boot
├── bom                          # BOM (Bill of Materials) — dependency version management
├── module
│   ├── integrate-boot-data      # Data-access integration (MyBatis-Flex + Spring Boot)
│   ├── integrate-boot-jackson   # Jackson serialization defaults (date format + typed mapper)
│   ├── integrate-boot-cache     # Local cache integration (Caffeine + Spring Cache)
│   ├── integrate-boot-redis     # Redis integration (Redisson + Spring Data Redis)
│   ├── integrate-boot-authentication # OAuth2 authorization server + password grant (optional)
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

## integrate-boot-jackson

Centralizes Jackson (3.x, `tools.jackson.*`) serialization defaults for the whole application.

### What you get out of the box — zero config

- **Web-global date/time format.** Both `java.util.Date` / `Calendar` and
  `java.time.LocalDateTime` serialize as `yyyy-MM-dd HH:mm:ss` in `GMT+8`. This is applied via a
  `JsonMapperBuilderCustomizer` on Spring Boot's auto-configured `ObjectMapper`, so it affects
  REST responses with no YAML needed.
- **`typedObjectMapper` bean.** A standalone `ObjectMapper` (named `typedObjectMapper`) that
  additionally writes the concrete type as a `@class` property (default typing). It is **not**
  `@Primary`, so web responses stay clean of type noise; inject it by name where type-preserving
  serialization is required (Redis values, micro-service RPC).

### Usage

Nothing is required to enable the defaults. Override the format/timezone if needed:

```yaml
integrate-boot:
  jackson:
    date-format: yyyy-MM-dd HH:mm:ss   # optional, this is the default
    time-zone: GMT+8                    # optional, this is the default
```

Use the typed mapper where values must round-trip into their original types:

```java
@Autowired
@Qualifier("typedObjectMapper")
private ObjectMapper typedObjectMapper;
```

The Redis module already wires its `RedisTemplate` to this mapper, so cached objects (including
date/time fields) serialize consistently with the rest of the app.

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

## integrate-boot-redis

Redis module built on **Redisson** + Spring Data Redis. It leans on the Redisson Spring Boot
starter, which auto-configures a `RedissonClient` and uses Redisson as the underlying
`RedisConnectionFactory` from Spring Boot's native `spring.data.redis.*` properties. Because
both `RedisTemplate` and `RedissonClient` are backed by that single connection factory, they
share the same Redis client and configuration — nothing extra is needed to keep them in sync.

### What you get out of the box

- A `RedissonClient` auto-configured from `spring.data.redis.*`
- A `@Primary RedisTemplate` / `StringRedisTemplate` with String keys + JSON values. Values go
  through the shared `typedObjectMapper` (Jackson 3, with type information and the configured
  date/time format), so cached objects restore their concrete types
- Optional extra Redis instances under `integrate-boot.redis.multi.*`, each exposing its own
  `RedissonClient` / `RedisTemplate` / `StringRedisTemplate` beans (inject by name)
- Distributed-lock / collection APIs directly from `RedissonClient` (`RLock`, `RMap`, `RBucket`, ...)

### Usage

1. Depend on the module (or just use the starter, which aggregates it):

   ```groovy
   dependencies {
       implementation platform('com.github.henc:integrate-boot-bom:0.0.1-SNAPSHOT')
       implementation 'com.github.henc:integrate-boot-redis'
   }
   ```

2. Configure the default Redis connection with the standard Spring Boot properties. Secrets
   such as the password are best supplied via environment variables rather than committed to
   source:

   ```yaml
   spring:
     data:
       redis:
         host: ${REDIS_HOST:localhost}
         port: ${REDIS_PORT:6379}
         password: ${REDIS_PASSWORD:}        # pass via REDIS_PASSWORD env var
         database: ${REDIS_DATABASE:0}
   ```

3. Use `RedisTemplate` or `RedissonClient` directly — both connect to the same Redis:

   ```java
   @Autowired
   private RedisTemplate<Object, Object> redisTemplate;   // JSON-serialized values

   @Autowired
   private RedissonClient redissonClient;                 // RLock, RMap, RBucket ...

   RLock lock = redissonClient.getLock("order:lock:123");
   if (lock.tryLock(10, 60, TimeUnit.SECONDS)) {
       try { ... } finally { lock.unlock(); }
   }
   ```

### Multiple Redis instances

Declare extra connections under `integrate-boot.redis.multi.<name>.*`; each yields three named
beans (`redissonClient-<name>`, `redisTemplate-<name>`, `stringRedisTemplate-<name>`), injected
via `@Qualifier`:

```yaml
integrate-boot:
  redis:
    multi:
      user:                          # instance "user"
        host: 10.0.0.1
        port: 6379
        password: pass-user
        database: 1
      session:                       # instance "session" (cluster)
        cluster:
          nodes:
            - 10.0.0.2:7001
            - 10.0.0.2:7002
            - 10.0.0.2:7003
        password: pass-session
```

```java
@Resource(name = "redisTemplate-user")
private RedisTemplate<Object, Object> userRedisTemplate;

@Resource(name = "redissonClient-session")
private RedissonClient sessionRedisson;
```

A `multi` entry supports standalone (default), sentinel (`sentinel.master` + `sentinel.nodes`)
and cluster (`cluster.nodes`) topologies, plus `username`, `password`, `database`, `timeout`
and `ssl`. An empty / absent `multi` map (the default) means single-Redis mode.

## integrate-boot-authentication

OAuth2 authorization server + JWT resource protection, built on the Spring Boot 4.1
`spring-boot-starter-security-oauth2-authorization-server` (Spring Security 7.1). It is an
**optional** module — add it explicitly only to services that need authentication; the starter
does not aggregate it, so services without auth keep running unconstrained.

### What you get out of the box — zero config

- A full OAuth2 authorization server issuing **JWT** access tokens (RSA key pair generated at
  startup) and opaque refresh tokens, at the standard endpoints (`/oauth2/token`,
  `/oauth2/authorize`, `/oauth2/jwks`, ...)
- Standard grants: `authorization_code`, `client_credentials`, `refresh_token`
- A custom **`password` grant** (`grant_type=password`, username/password → token), re-added as a
  custom grant since OAuth 2.1 / Spring Authorization Server dropped it
- A demo client (`client` / `secret`) and a demo user (`user` / `password`) so the server is
  usable before any configuration — override them with your own beans
- JWT-based resource protection for the application's own endpoints (Bearer token in
  `Authorization` header), with the OAuth2 / actuator paths permitted

### Usage

1. Depend on the module (it is not aggregated by the starter):

   ```groovy
   dependencies {
       implementation platform('com.github.henc:integrate-boot-bom:0.0.1-SNAPSHOT')
       implementation 'com.github.henc:integrate-boot-authentication'
   }
   ```

2. With the defaults, get a token via the password grant:

   ```bash
   curl -u client:secret -d "grant_type=password&username=user&password=password&scope=read" \
       http://localhost:8080/oauth2/token
   # -> {"access_token":"<JWT>","refresh_token":"...","token_type":"Bearer","expires_in":3600}
   ```

3. Call a protected endpoint with the token:

   ```bash
   curl -H "Authorization: Bearer <JWT>" http://localhost:8080/api/users
   ```

4. Plug in your own user store by implementing `UserDetailsPasswordService`:

   ```java
   @Component
   public class DbUserDetailsService implements UserDetailsPasswordService {
       @Override
       public UserDetails loadUserByUsername(String username) {
           // query your user table and return a UserDetails with the password hash + authorities
       }
   }
   ```

   When this bean is present, the module's demo user backs off (`@ConditionalOnMissingBean`).

### Configure clients and issuer

OAuth2 clients, the issuer and JWK are configured through Spring Boot's native properties — the
module's own properties only toggle the password grant and add public paths:

```yaml
spring:
  security:
    oauth2:
      authorization-server:
        client:
          my-client:
            registration:
              client-id: my-client
              client-secret: "{bcrypt}$2a$10$..."   # hashed
              client-authentication-methods: [client_secret_basic, client_secret_post]
              authorization-grant-types: [password, refresh_token, client_credentials]
              redirect-uris: ["http://127.0.0.1:8080/login/oauth2/code/my-client"]
              scopes: [read, write]

integrate-boot:
  auth:
    password-grant-enabled: true          # default true
    permit-all-paths:                      # extra public paths, on top of /oauth2/**, /login, ...
      - /public/**
```

Define your own `JWKSource<SecurityContext>` bean to load a fixed RSA key pair (instead of the
auto-generated one) for production.

> Note: the `password` grant carries the user's credentials to the token endpoint, so prefer
> `authorization_code` + PKCE for human-facing clients. The password grant is provided mainly for
> machine-to-machine and legacy-client compatibility.

## integrate-boot-starter
Bootstrap entry point that aggregates the data layer and exposes the
`@IntegrateBoot` convenience annotation.

Depending on `integrate-boot-starter` transitively brings in `integrate-boot-data`
(MyBatis-Flex + datasource auto-configuration), `integrate-boot-jackson` (date/time defaults +
the typed `ObjectMapper`), `integrate-boot-cache` (Caffeine + the local cache managers) and
`integrate-boot-redis` (Redisson + RedisTemplate), so a service only needs one dependency to
get the whole stack.

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

The Redis layer requires a running Redis instance. The connection is read from environment
variables so no secret is stored in the repository — pass the password on the command line:

```bash
REDIS_PASSWORD=<your-redis-password> ./gradlew :test:integrate-boot-test:test
```

`REDIS_HOST`, `REDIS_PORT` and `REDIS_DATABASE` default to `localhost` / `6379` / `0`.
