# integrate-boot

A foundational framework for business systems, encapsulating core capabilities.

## Modules

```
integrate-boot
├── bom                          # BOM (Bill of Materials) — dependency version management
├── module
│   ├── integrate-boot-base      # Base entities + date/time utils (DateUtils) — plain Java, no deps
│   ├── integrate-boot-data      # Data-access integration (MyBatis-Flex + Spring Boot)
│   ├── integrate-boot-jackson   # Jackson serialization defaults (date format + typed mapper)
│   ├── integrate-boot-logging   # Service logging: SLF4J facade over Log4j2 + default config
│   ├── integrate-boot-exception # Global exception handling: exception hierarchy + ResultInfo advice
│   ├── integrate-boot-cache     # Local cache integration (Caffeine + Spring Cache)
│   ├── integrate-boot-redis     # Redis integration (Redisson + Spring Data Redis)
│   ├── integrate-boot-authentication # OAuth2 authorization server + password grant (optional)
│   ├── integrate-boot-resource-server # Bearer resource protection + token validation port (optional)
│   ├── integrate-boot-scheduling # Distributed scheduling on XXL-JOB: @Job discovery + registry (opt-in)
│   ├── integrate-boot-event     # In-process event bus: EventBus facade + async takeover (+ optional outbox)
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

### Taskfile

A [Taskfile](https://taskfile.dev) wraps the common Gradle invocations so local development
and CI pipelines share one entry point (`brew install go-task` for the `task` command).
Locally it also loads `.env`, so secrets like `REDIS_PASSWORD` don't need to be typed per run:

```bash
task build          # compile everything and run all tests
task test           # tests only
task ci             # clean, no-daemon build as run by CI
task publish-local  # BOM + all modules into build/repo
task --list         # all tasks, incl. per-module publishing and version bumping
```

## Publishing

Every library module is published with the same Maven repository layout used by Spring Boot:
`jar`, `pom`, Gradle module metadata, `-sources.jar`, and `-javadoc.jar`. The BOM is a POM-only
artifact. Maven consumers resolve the POM as a separate repository artifact; POM files are neither
committed at module roots nor embedded in library JARs.

Publish **everything** (BOM + all modules) into one local staging tree:

```bash
./gradlew publishAllPublicationsToLocalStageRepository
# -> build/repo/com/github/henc/integrate-boot-*/<version>/...
```

Or publish a single module, e.g.:

```bash
./gradlew :module:integrate-boot-data:publishMavenJavaPublicationToLocalStageRepository
./gradlew :bom:publishIntegrateBootBomPublicationToLocalStageRepository
```

(To publish to a remote repository such as Nexus or Artifactory, add a `maven { url = '...' }`
entry under `publishing.repositories` — in `bom/build.gradle` for the BOM, or in
`gradle/publishing.gradle` for the library modules.)

### The BOM — import once, get all versions

The `bom` module is a plain POM with a `<dependencyManagement>` section (no binary artifact).
Import it to inherit the whole dependency-version set with one line.

```xml
<!-- Maven -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.github.henc</groupId>
            <artifactId>integrate-boot-bom</artifactId>
            <version>0.0.1-SNAPSHOT</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

```groovy
// Gradle (Groovy DSL)
dependencies {
    implementation platform('com.github.henc:integrate-boot-bom:0.0.1-SNAPSHOT')
    // then depend on modules without versions, e.g.:
    implementation 'com.github.henc:integrate-boot-starter'
}
```

Each library module's POM re-imports the BOM under its own `<dependencyManagement>`, so even a
consumer that depends on a single module gets consistent transitive versions.

## Requirements

- JDK 21
- Gradle 8.14+ (provided via the wrapper)

## integrate-boot-base

Shared base entities and utilities, in plain Java with **no framework dependencies**, so any
layer (including non-Spring code) may use them. The starter aggregates it.

- **`ResultInfo`** — the standard response envelope: `success` / `code` / `message` / `requestId`
  plus an open `result` map. Created through the static factories, filled fluently:

  ```java
  // {"success":true,"code":0,"result":{"user":...,"roles":...}}
  return ResultInfo.success().put("user", user).put("roles", roles);

  // {"success":false,"code":-1,"message":"user not found"}
  return ResultInfo.failure("user not found");
  return ResultInfo.failure(40401, "user not found");   // explicit error code
  ```

  Success carries code `0` (`CODE_SUCCESS`); `failure(message)` defaults to code `-1`
  (`CODE_FAILURE`) — any non-zero code means failure, and services may define their own ranges.
  Fields are `protected`, so a service can extend the class with its own typed result shape
  while keeping the wire contract.

- **`KeyValue<K, V>`** — a minimal generic key-value pair (public mutable `key` / `value`
  fields plus bean getters/setters) for places a `Map` entry does not fit:

  ```java
  List<KeyValue<String, Integer>> counts = List.of(KeyValue.of("alice", 3), KeyValue.of("bob", 5));
  ```

### Date and time — `DateUtils`

The date/time toolkit with one opinionated entry point: `getCurrentDateTime()` returns the
*served* current time from the best available time source, not blindly the local clock:

```java
Date now = DateUtils.getCurrentDateTime();               // best available source
Date cheap = DateUtils.getCurrentDateTimeSimpleInterval(); // cached offset, for hot paths
Date exact = DateUtils.getDateFromDateTimeService();      // one direct read, no cache
Date local = DateUtils.getSystemDateTime();               // local clock, ignoring sources
```

The rest of the class is a plain-Java toolbox: parsing/formatting (`parseDate`,
`toDateTimeString`, common pattern constants), arithmetic (`addDay` / `addMonth` / ...),
type conversion (`dateToLocalDateTime`, ...), calendar-day differences, day/week/month/
quarter boundaries (`getDayBegin`, `getWeekEnd`, `getQuarterEnd`, ...), and enumeration
helpers (`listDaysBetweenTime`, ...).

**Time sources and fallback.** A source implements `DateTimeService`
(`base.datetime` package) and is managed by the static `DateTimeRegistry`. Sources answer
in this order, with automatic fallback whenever a source is unavailable (it returns
`null` or throws — never an outage for the caller):

1. the preferred source, if `integrate-boot.datetime.prefer` is configured;
2. all registered sources by priority (a custom source you register yourself defaults to
   outranking the shipped ones; then `redis`, then `db`);
3. the local system clock — the built-in last resort, so `getCurrentDateTime()` never
   throws and never returns `null`, even in a plain non-Spring application.

`integrate-boot-redis` registers a `redis` source (Redis `TIME`, millisecond precision)
and `integrate-boot-data` a `db` source (`select now()`) — automatically, whenever those
modules are on the classpath with their infrastructure configured. Reads are cheap:
a source that allows it has its offset from the local clock cached and refreshed in the
background (default every 10 minutes) instead of paying a remote call per read.

Shared switches (read by whichever datetime module is present):

```yaml
integrate-boot:
  datetime:
    prefer: redis          # redis | db | server | <custom type>; default: priority order
    interval-enabled: true # cached-offset fast path; default true
    check-interval: 10m    # offset refresh period; default 10 minutes
```

A custom time source is a class plus one line (picked up automatically when any datetime
module is present; register it yourself via `DateTimeRegistry.register` otherwise):

```java
public class SatelliteDateTimeService implements DateTimeService {
    @Override public String getType() { return "satellite"; }
    @Override public Date getCurrentDate() {
        try { return satelliteClient.now(); } catch (IOException e) { return null; } // fall back
    }
}
```

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
- A **database time source** for `DateUtils`: `DbDateTimeService` (`select now()`) is
  registered automatically whenever a datasource is configured, so
  `DateUtils.getCurrentDateTime()` serves the database server's clock with automatic
  fallback (see [Date and time — `DateUtils`](#date-and-time--dateutils))

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

## integrate-boot-logging

Unified service logging: application code logs through the **SLF4J** facade, and the backend is
**Log4j2** (replacing Spring Boot's default Logback). The module ships a production-sane default
`log4j2.xml` inside its jar, so a service gets console output plus daily/size-rolled log files
with zero configuration.

### What you get out of the box — zero config

- **SLF4J → Log4j2 binding** via `spring-boot-starter-log4j2` (`log4j-slf4j2-impl`). Logback
  (`spring-boot-starter-logging`) is excluded from every starter the platform publishes, so the
  two backends never end up on one classpath (that combination breaks logging outright).
- **Default `log4j2.xml`** discovered automatically from the module jar: console + rolling file
  `${LOG_PATH}/${APP_NAME}.log`, daily rollover plus a 100 MB size cap, gzipped archives kept
  for 30 days.
- **Level tiers.** Loggers are divided into three tiers (see below): **info** is the default,
  **warn** caps third-party noise, **debug** ships disabled.
- **Startup guard.** On boot the module verifies the classpath is in the intended shape and logs
  a loud, actionable `ERROR` if Logback leaked in or SLF4J bound to the wrong provider — instead
  of silently losing logs to a classpath-order accident.

### Level tiers — info / warn / debug

| Tier  | Applies to | Default |
|-------|------------|---------|
| info  | Business code (root logger) | **active** — the default level |
| warn  | Third-party components (`org.apache`, `io.netty`, `org.redisson`, HikariCP) | active |
| debug | Troubleshooting | **disabled** — the debug block sits commented out |

To debug, either swap the commented `Root` blocks in the shipped `log4j2.xml` (comment the info
`Root`, uncomment the debug `Root`, restart), or avoid touching the file entirely and change
levels at runtime via Spring Boot — `logging.level.root=debug` for everything, or
`logging.level.<package>=debug` to widen a single package.

### Usage

Nothing is required to enable the defaults. Tune without replacing the config file:

```yaml
logging:
  file:
    path: /var/logs/my-service   # sets LOG_PATH for the shipped log4j2.xml
  level:
    root: info                   # runtime levels via Spring Boot as usual
    com.example.mapper: debug
```

- `APP_NAME` — system property or environment variable (default `application`); the log file
  name stem.
- `LOG_PATH` — system property or environment variable (default `logs`); also driven by Spring
  Boot's `logging.file.path` property.

An application replaces the shipped configuration with its own `log4j2-spring.xml` (checked
first by Spring Boot), its own `log4j2.xml` (application resources win over jars), or by
pointing `logging.config` at a custom file.

When the application declares additional Spring Boot starters itself, keep excluding Logback
from them — the published exclusions only cover the starters integrate-boot declares:

```groovy
implementation('org.springframework.boot:spring-boot-starter-actuator') {
    exclude group: 'org.springframework.boot', module: 'spring-boot-starter-logging'
}
```

## integrate-boot-exception

Global exception handling: a small unchecked exception hierarchy plus one
`@RestControllerAdvice` that renders **every** failure into the shared `ResultInfo` envelope —
expected business failures respond HTTP 200 with the envelope carrying the failure, protocol
and client errors respond with their matching 4xx/5xx status. Controllers never hand-roll
error responses.

### What you get out of the box — zero config

- **Common exceptions**, each carrying a business code, a message and an HTTP status:

  | Exception               | HTTP | Default code |
  |-------------------------|------|--------------|
  | `BusinessException`     | 200  | `-1` (`ResultInfo.CODE_FAILURE`) |
  | `BadRequestException`   | 400  | `400` |
  | `UnauthorizedException` | 401  | `401` |
  | `ForbiddenException`    | 403  | `403` |
  | `NotFoundException`     | 404  | `404` |
  | `ConflictException`     | 409  | `409` |

  A business failure is a handled outcome, not a protocol error — `BusinessException` keeps
  the HTTP layer at 200 and the envelope's `success=false` / `code` / `message` carry the
  failure, exactly as `ResultInfo.failure(message)` would. The other classes map to their
  matching 4xx status for callers that distinguish protocol-level errors.

- **A global handler** that maps: any `BaseException` (see below) to its own code / message /
  status; Bean Validation and argument-binding failures to `400` with the offending fields
  spelled out; wrong HTTP method to `405`; unsupported media type to `415`; an unmatched path
  to `404`; everything else to `500` with a generic message (the full stack trace goes to the
  log, never to the client).
- **Sane logging levels**: expected failures (business exceptions, 4xx) log at `WARN` without
  a stack trace; unexpected ones log at `ERROR` with the full trace.
- The module brings the servlet web stack and Bean Validation transitively, so a service gets
  `@Valid` support together with structured 400 responses.

### Usage — throw, don't catch

```java
throw new BusinessException("insufficient balance");
// -> HTTP 200, {"success":false,"code":-1,"message":"insufficient balance"}

throw new NotFoundException("order " + id + " not found");
// -> HTTP 404, {"success":false,"code":404,"message":"order 1 not found"}

throw new ConflictException(10003, "duplicate order id");   // explicit business code
```

Validation failures need no try/catch either — `@Valid` binding errors come back as a `400`
with the field details joined into the message:

```
POST /errors/validate  {"name":"","age":200}
-> HTTP 400, {"success":false,"code":400,"message":"name: must not be blank; age: must be less than or equal to 150"}
```

### Defining your own exceptions

Two extension points, usable separately or together.

**1. An error-code enum** (implements `ErrorCode`) — reuse the common exception classes with
your own code range:

```java
public enum OrderErrorCode implements ErrorCode {
    INSUFFICIENT_STOCK(10001, "insufficient stock"),
    ORDER_NOT_FOUND(10002, "order not found");

    private final int code;
    private final String message;

    OrderErrorCode(int code, String message) { this.code = code; this.message = message; }

    @Override public int getCode() { return code; }
    @Override public String getMessage() { return message; }
}

throw new BusinessException(OrderErrorCode.INSUFFICIENT_STOCK);
// -> HTTP 200, {"success":false,"code":10001,"message":"insufficient stock"}

throw new NotFoundException(OrderErrorCode.ORDER_NOT_FOUND);
// -> HTTP 404, {"success":false,"code":10002,"message":"order not found"}
```

**2. An exception type** (extends `BaseException`) — for modules that want their own exception
class. The global handler catches `BaseException`, so every subclass is handled automatically:

```java
public class OrderException extends BaseException {

    public OrderException(ErrorCode errorCode) {
        super(errorCode.getCode(), errorCode.getMessage(), HttpStatus.CONFLICT);
    }
}

throw new OrderException(OrderErrorCode.INSUFFICIENT_STOCK);
// -> HTTP 409, {"success":false,"code":10001,"message":"insufficient stock"}
```

### Replacing the handler

The advice is registered by `ExceptionAutoConfiguration` behind `@ConditionalOnMissingBean` —
define your own `globalExceptionHandler` bean to take over wholesale, e.g. to render **every**
`BaseException` (not just `BusinessException`) as HTTP 200 with the envelope carrying the
failure:

```java
@Bean
public GlobalExceptionHandler globalExceptionHandler() {
    return new GlobalExceptionHandler() {
        @Override
        public ResponseEntity<ResultInfo> handleBaseException(BaseException ex) {
            return ResponseEntity.ok(ResultInfo.failure(ex.getCode(), ex.getMessage()));
        }
    };
}
```

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
- A **Redis time source** for `DateUtils`: `RedisDateTimeService` (the Redis server clock,
  via the `TIME` command) is registered automatically whenever a Redis connection is
  configured — with both the redis and db sources present it is the preferred one
  (see [Date and time — `DateUtils`](#date-and-time--dateutils))

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

### Multi-node deployment and key rotation

The default RSA key pair is generated randomly once per process. It is convenient for local
development only and must not be used by a multi-node production authorization service: each node
would otherwise sign tokens with a different private key, so a token issued by one node could be
rejected by another node or by a resource service after a restart.

For production, all authorization-server nodes must load the same signing key from a shared secret
source such as a KMS, Vault, Kubernetes Secret, cloud Secret Manager or an encrypted keystore. Keep
the key id (`kid`) stable across restarts, and configure resource services with the corresponding
public JWK set or a shared token-introspection endpoint. Never commit private keys or keystore
passwords to the repository.

Rotate keys by publishing the new public key while retaining the old public key, switching all
issuer nodes to the new private key, and removing the old public key only after the maximum lifetime
of tokens signed with it has elapsed. This prevents valid tokens from becoming unusable during a
rolling deployment. In production configuration, prefer failing startup when the shared signing
key is unavailable rather than silently generating a temporary key.

> The `integrate-boot-resource-server` module does not generate tokens or signing keys. Its
> `TokenValidationPort` implementation must validate against the issuer's shared public keys or a
> centralized introspection service.

> Note: the `password` grant carries the user's credentials to the token endpoint, so prefer
> `authorization_code` + PKCE for human-facing clients. The password grant is provided mainly for
> machine-to-machine and legacy-client compatibility.

## integrate-boot-resource-server

Standalone bearer-token resource protection for business services. This is an **optional** module and
is intentionally independent from `integrate-boot-authentication`: it does not issue tokens, create
JWK signing keys, expose OAuth2 authorization endpoints, or include the password grant.

### Usage

Depend on the module directly:

```groovy
dependencies {
    implementation platform('com.github.henc:integrate-boot-bom:0.0.1-SNAPSHOT')
    implementation 'com.github.henc:integrate-boot-resource-server'
}
```

#### Zero code — point it at the issuer's JWKS endpoint

Configure the authorization server's JWKS URL and the module installs a JWT-backed
`TokenValidationPort` (signature + expiry validation via Spring Security's `NimbusJwtDecoder`;
`sub` becomes the subject, `scope`/`scp` become `SCOPE_x` authorities):

```yaml
integrate-boot:
  resource-server:
    jwt:
      jwk-set-uri: http://auth-service:8080/oauth2/jwks
      issuer: https://auth-service        # optional: additionally verify the iss claim
```

#### Or provide your own validation port

For opaque tokens, introspection endpoints or custom claim mapping, implement the
framework-independent validation port. The module passes the raw token value without the
`Bearer ` prefix; the application owns signature, issuer, audience and expiry validation
(an application-provided port always overrides the JWT default above):

```java
@Component
public class AccessTokenValidator implements TokenValidationPort {
    @Override
    public TokenValidationResult validate(String token) {
        // Validate the token with the service's issuer or token-introspection client.
        return TokenValidationResult.valid(
                "user-123",
                Map.of("tenant", "acme"),
                Set.of("SCOPE_orders:read"),
                Instant.now().plusSeconds(300));
    }
}
```

Requests with `Authorization: Bearer <token>` are authenticated using the returned subject and
authorities. Invalid, empty, malformed or rejected tokens receive HTTP 401. Requests without a
bearer header continue through the chain and are rejected by the default authorization rule unless
the path is configured as public:

```yaml
integrate-boot:
  resource-server:
    permit-all-paths:
      - /health
      - /public/**
```

The default chain is stateless and protects every other request. Define your own
`SecurityFilterChain` or the `TokenValidationPort` implementation when the service needs custom
rules or a different token backend. The resource-server module is not aggregated by
`integrate-boot-starter`; services can add it without pulling in the authorization server.

When both this module and `integrate-boot-authentication` are on the classpath (a monolith that
both issues and validates tokens), this module's filter chain owns the application endpoints and
the authorization-server module's convenience chain backs off — exactly one default chain exists
per application (Spring Security 7 rejects two matches-any-request chains at startup).

`@IntegrateBoot` convenience annotation.

Depending on `integrate-boot-starter` transitively brings in `integrate-boot-base`
(the `ResultInfo` / `KeyValue` base entities), `integrate-boot-data`
(MyBatis-Flex + datasource auto-configuration), `integrate-boot-jackson` (date/time defaults +
the typed `ObjectMapper`), `integrate-boot-cache` (Caffeine + the local cache managers),
`integrate-boot-redis` (Redisson + RedisTemplate), `integrate-boot-logging` (SLF4J facade
over Log4j2 with the default `log4j2.xml`) and `integrate-boot-exception` (the common
exception hierarchy + the global handler; also provides the servlet web stack and Bean
Validation), so a service only needs one dependency to get the whole stack.

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
| Event listener | `**.listener.**`  | `xxx.event.listener.XxxListeners`|

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
   │   ├── domain/UserRepository.java
   │   ├── event/UserCreated.java
   │   └── event/listener/UserListeners.java
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

## integrate-boot-event

In-process event bus on Spring's native event mechanism: modules communicate through
plain business records and one `EventBus` facade, without any external broker.

### What you get out of the box — zero config

- `EventBus` — a thin logging facade over `ApplicationEventPublisher`; inject it and
  `publish(...)`. Events are plain objects (records recommended), no base class required.
- `@AsyncEventListener` — an `@EventListener + @Async` shortcut: the listener runs on
  Boot's `applicationTaskExecutor` (virtual-thread backed when
  `spring.threads.virtual.enabled=true`), decoupled from the publisher's thread and
  transaction.
- Unified `@EnableAsync` — the platform owns async configuration, so applications never
  write their own. Boot 4's `AsyncConfigurer` wrapping is honoured (the module registers
  itself before `TaskExecutionAutoConfiguration`).
- A failure safety net — a failing async listener is error-logged and announced through
  the synchronous `EventListenerFailedEvent` meta-event (recursion-guarded); the
  publisher is never disturbed.
- `IntegrationEvent` — an optional marker interface documenting cross-module events
  (the future distributed bridge will select on it).
- Listeners in `**.listener.**` packages are picked up by `@IntegrateBoot`'s
  conventional scan (event payload classes may live anywhere, e.g. `xxx.event`).

### Listener contracts

Delivery semantics are chosen by the *listener*, never the publisher:

| Contract | Annotation | Failure behavior |
|----------|------------|------------------|
| Synchronous, in the publisher's transaction | `@EventListener` | propagates to the publisher and rolls back its transaction — that is the point of sync events |
| Asynchronous, best-effort | `@AsyncEventListener` | logged + `EventListenerFailedEvent`; publisher unaffected |
| After the publishing transaction commits | `@TransactionalEventListener(phase = AFTER_COMMIT)` | with the reliability layer: persisted and re-delivered |
| Durable: after commit + async + own transaction | `@ApplicationModuleListener` (Modulith) | outbox-backed; re-delivered on failure and restart |

### Usage

```java
// The event: a plain record; IntegrationEvent is an optional marker for cross-module facts.
public record UserCreated(Long id, String userName) implements IntegrationEvent {}

// Publish — from a @Transactional service the natural fit.
@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final EventBus eventBus;

    // ...

    @Override
    @Transactional
    public User create(User user) {
        userRepository.insert(user);
        eventBus.publish(new UserCreated(user.getId(), user.getUserName()));
        return user;
    }
}

// React — each listener picks its own delivery contract.
@Component
public class UserListeners {

    @AsyncEventListener
    public void sendWelcomeMail(UserCreated event) { /* ... */ }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void refreshProjection(UserCreated event) { /* ... */ }
}
```

### Configuration

```yaml
integrate-boot:
  event:
    enabled: true          # master switch (default on — plain in-process wrapper, no infrastructure)
    async:
      enabled: true        # unified @EnableAsync takeover (default on)
    reliability:
      enabled: false       # transactional outbox via Spring Modulith (opt-in, see below)
```

### Optional reliability layer (transactional outbox)

A plain `@TransactionalEventListener` delivery vanishes if the listener fails or the
application crashes between commit and delivery. Switching the reliability layer on
backs such deliveries with Spring Modulith's event publication registry (an outbox):
each publication is stored in the same transaction as the business data, and incomplete
ones are re-delivered after restart or through Modulith's `IncompleteEventPublications`.

The Modulith artifacts never arrive transitively (the event module keeps them
`compileOnly` so its auto-configurations cannot leak onto consumers' classpaths), so
opting in is explicit — declare the artifacts (versions come from the BOM), flip the
switch, and mark durable listeners with `@ApplicationModuleListener`:

```groovy
dependencies {
    implementation platform('com.github.henc:integrate-boot-bom')
    implementation 'org.springframework.modulith:spring-modulith-starter-jdbc'
    implementation 'org.springframework.modulith:spring-modulith-events-jackson'
}
```

```yaml
integrate-boot:
  event:
    reliability:
      enabled: true
```

The switch additionally contributes recommended defaults (registry table bootstrap when
missing, re-delivery of outstanding publications on restart); any explicit
`spring.modulith.*` setting overrides them. Requires `spring-jdbc` + a `DataSource`
(provided by `integrate-boot-data` in this stack). Version coupling: Modulith 2.1.x
pairs with Spring Boot 4.1.x — the BOM pins them in lockstep.

## test/integrate-boot-test

Sample application that boots the whole stack with `@IntegrateBoot` over an in-memory H2
database (no external setup) and verifies the modules end-to-end: conventional layer
scanning, MyBatis-Flex mapper access, transactions, underscore-to-camelCase mapping, and
the global exception handler (business / not-found / conflict / validation / 405 / 404 /
generic-500 responses, see `ErrorHandlingIT`).

The Redis layer requires a running Redis instance. The connection is read from environment
variables so no secret is stored in the repository — pass the password on the command line:

```bash
REDIS_PASSWORD=<your-redis-password> ./gradlew :test:integrate-boot-test:test
```

`REDIS_HOST`, `REDIS_PORT` and `REDIS_DATABASE` default to `localhost` / `6379` / `0`.

Besides the in-process suite, two opt-in suites run in their own JVMs through dedicated
Gradle tasks: the dynamic-datasource tests (`task dynamic-test`, see
`DynamicDataSourceIT`) and the event-reliability tests that exercise the Spring Modulith
outbox end to end (`task reliability-test`, see `EventReliabilityIT` — the reliability
source set adds the Modulith artifacts exactly the way a consumer would).
