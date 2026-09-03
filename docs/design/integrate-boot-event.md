# integrate-boot-event 设计稿

> 状态：已实施（2026-09-03）· 实施中的两处偏离见文末「落地记录」
> 前置调研结论：内核用 Spring 原生事件机制（零新依赖），可靠层用 Spring Modulith 2.1.1 事件登记簿（可选），排除 Guava EventBus / Reactor / Axon 等第三方方案。

## 1. 定位与目标

为业务系统提供**进程内**的全局异步事件能力（内部总线），各业务模块通过事件解耦：

- 发布方依赖 `EventBus` 门面，不感知谁在监听、几个监听、同步还是异步；
- 监听方用注解声明式订阅，与全局异步执行、异常兜底、事务边界约定集成；
- 可靠投递（事务性 outbox、失败重投）作为可选层叠加，不引入 Kafka 等外部设施。

不做的事（明确排除）：跨服务消息、事件溯源/CQRS、响应式流。分布式广播（Redis RTopic 桥）留作后续扩展，见 §10。

## 2. 总体结构

```
业务代码 ──publish──▶ EventBus 门面 ──▶ ApplicationEventPublisher（Spring 原生）
                                          │
                     ┌────────────────────┼──────────────────────────┐
                     ▼                    ▼                          ▼
               @EventListener       @AsyncEventListener      @TransactionalEventListener
               （同步，事务内）      （异步，尽力而为）        (AFTER_COMMIT，事务提交后)
                                                                         │
                                                              可靠层开启时被 Modulith
                                                              Event Publication Registry
                                                              拦截并持久化（outbox + 重投）
```

分层职责：

| 层 | 内容 | 依赖 |
|---|---|---|
| 内核（本期） | EventBus 门面、`@AsyncEventListener` 复合注解、统一 `@EnableAsync` 收口、异步监听异常兜底 | 无新依赖 |
| 可靠层（本期，默认关） | Modulith 事件登记簿 glue：推荐默认值注入、启动提示 | `spring-modulith-starter-jdbc` + `spring-modulith-events-jackson`（compileOnly，显式引入） |
| 分布式桥（后续） | 本地事件 ↔ Redisson RTopic 广播 | redis 模块已有 RedissonClient |

## 3. 模块与构建变更

- 新模块 `module/integrate-boot-event`，包 `com.github.henc.integrateboot.event`，`java-library` + `gradle/publishing.gradle`；
- `settings.gradle` 注册；`module/integrate-boot-starter` 增加 `api project(':module:integrate-boot-event')`；
- `gradle/libs.versions.toml`：`spring-modulith = "2.1.1"`（starter-jdbc、events-jackson 两个 artifact）；
- `bom/build.gradle`：引入 `org.springframework.modulith:spring-modulith-bom` platform。**版本绑定注释必须写明：Modulith 2.1.x ↔ Boot 4.1.x 严格对应，升级 Boot 4.2 时需同步升 Modulith 2.2**（AGENTS.md "版本只改一处"约定）；
- 模块自身依赖：`base`、`spring-boot-autoconfigure`（自动配置）、`slf4j-api`（面向 SLF4J 编码）。Modulith 两个 artifact 声明为 compileOnly，**不随 POM 传递**——否则未启用可靠层的消费者会被 Modulith 自带的 AutoConfiguration 激活（自动建表等副作用），这不可接受。

## 4. API 设计

### 4.1 EventBus 门面

```java
package com.github.henc.integrateboot.event;

public interface EventBus {
    /** 发布一个事件；载体为任意业务对象（record / POJO）。 */
    void publish(Object event);

    /** 依序批量发布。 */
    default void publishAll(Object... events) {
        for (Object event : events) publish(event);
    }
}
```

实现 `ApplicationEventBus` 委托 `ApplicationEventPublisher`，发布时打 debug 日志（事件类型）。不引入 Envelope 包装——保持 Spring 原生语义，监听器直接声明业务事件类型，零摩擦；元数据（eventId 等）不强制，理由见 4.2。

### 4.2 事件载体约定

**无强制基类**。事件是任意 Jackson 可序列化对象（可靠层开启时会被序列化入库）。提供可选标记接口：

```java
/** 语义标记：跨模块集成事件。未来分布式桥用它筛选广播范围。 */
public interface IntegrationEvent {}
```

用法：`record OrderCreated(Long orderId) implements IntegrationEvent {}`。放 event 模块（非 base）：使用事件就必然依赖 EventBus，不存在 @Job 那种"注解先于模块存在"的场景，base 保持零改动。

### 4.3 监听端注解矩阵

| 场景 | 注解 | 事务语义 | 失败行为 | 可靠层是否接管 |
|---|---|---|---|---|
| 同步监听 | Spring `@EventListener` | 与发布方同线程同事务 | 异常向发布方传播（回滚发布方事务，特性而非缺陷） | 否 |
| 异步监听（尽力而为） | `@AsyncEventListener`（新增） | 无事务关联 | AsyncUncaughtExceptionHandler 兜底：error 日志 + 元事件 | 否 |
| 事务提交后 | Spring `@TransactionalEventListener(phase = AFTER_COMMIT)` | 发布方事务提交后执行 | 同上（未标注 @Async 时向发布方传播） | 是 |
| 事务后 + 异步 + 独立事务 | Modulith `@ApplicationModuleListener` | REQUIRES_NEW 异步执行 | 登记簿记录失败，可重投 | 是（推荐） |

新增复合注解：

```java
@EventListener
@Async
@Target(ElementType.METHOD) @Retention(RetentionPolicy.RUNTIME) @Documented
public @interface AsyncEventListener {}
```

⚠️ 验证点（实施时用 IT 覆盖）：`@EventListener` 元注解组合是官方支持的；`@Async` 作为元注解的组合行为需实测，若不被 advisor 识别则降级为文档指引（方法上并列标注两个原注解）。

### 4.4 统一异步收口

- `EventBusAutoConfiguration` 内嵌 `@EnableAsync`（`integrate-boot.event.async.enabled` 控制，默认 true）。业务应用不再需要自己写 `@EnableAsync` 配置类——框架统一负责；
- 注册 `AsyncConfigurer`（`@ConditionalOnMissingBean`，尊重应用自定义）：
  - `getAsyncExecutor()` 返回 null → 落回 Boot 的 `applicationTaskExecutor`，天然享受 `spring.threads.virtual.enabled` 虚拟线程，不另建线程池；
  - `getAsyncUncaughtExceptionHandler()` → `EventBusAsyncExceptionHandler`。
- 不复制 Boot 的 `spring.task.execution.*` 执行器配置，复用即可。

### 4.5 异常兜底与元事件

```java
/** 监听器执行失败的元事件：同步发布，供业务侧做告警/审计。 */
public record EventListenerFailedEvent(
        String listenerMethod,   // com.example.OrderListener#onOrderCreated
        Object event,
        Throwable exception) {}
```

`EventBusAsyncExceptionHandler` 行为：

1. SLF4J error 日志（监听方法、事件类型、异常堆栈），面向 SLF4J 编码，遵守项目日志约定；
2. 同步发布 `EventListenerFailedEvent`；
3. **递归防护**：handler 收到的方法参数本身是 `EventListenerFailedEvent` 时只记日志、不再发布元事件，避免失败风暴。

同步监听异常不兜底（见矩阵）：异常传播 + 发布方事务回滚是同步事件的语义。不做 `SimpleApplicationEventMulticaster` 侵入式改造。

## 5. 自动配置与配置项

`EventBusAutoConfiguration`（注册于 `META-INF/spring/...AutoConfiguration.imports`，复刻 scheduling 模块模板）：

```yaml
integrate-boot:
  event:
    enabled: true          # 总开关，默认开（纯进程内封装、零外部依赖，与 cache/jackson 的开箱即用一致；
                           # scheduling 默认关是因为依赖外部 XXL-JOB admin，本模块无此负担）
    async:
      enabled: true        # 统一 @EnableAsync 收口
    reliability:
      enabled: false       # 可靠层 glue；需 classpath 已显式引入 Modulith 依赖
```

装配条件：总开关 `@ConditionalOnProperty(matchIfMissing = true)`；可靠层子配置 `@ConditionalOnClass(EventPublicationRegistry)` + 显式开关。

## 6. 可靠层设计（Modulith 集成）

**启用方式**：消费者显式加依赖（版本走 BOM）+ 一个开关：

```groovy
implementation platform('com.github.henc:integrate-boot-bom')
implementation 'org.springframework.modulith:spring-modulith-starter-jdbc'
implementation 'org.springframework.modulith:spring-modulith-events-jackson'
```
```yaml
integrate-boot.event.reliability.enabled: true
```

前置条件：`spring-jdbc` + DataSource（项目内由 data 模块提供）。监听端改用 `@ApplicationModuleListener`（= `@Async + REQUIRES_NEW + @TransactionalEventListener`）。

**glue 做三件事**：

1. `EnvironmentPostProcessor`：当 `integrate-boot.event.reliability.enabled=true` 时注入**低优先级** property source，落下推荐默认值（用户可用原生 `spring.modulith.*` 覆盖）：
   - `spring.modulith.events.jdbc.schema-initialization.enabled=true`（自动建表；已有 Flyway/Liquibase 管理的表会自动退避；生产库不想自动建表可显式关闭）
   - `spring.modulith.events.republish-outstanding-events-on-restart=true`（重启重投未完成投递）
   - 其余（completion-mode=UPDATE、staleness monitor）保持 Modulith 默认，不越俎代庖；
2. 启动时检测 classpath 存在 Modulith → info 日志提示可靠层已激活（含 registry 存储/序列化方式）；
3. 版本管理（BOM platform，见 §3）。

不包装 Modulith 的 API（`IncompleteEventPublications` 等）——需要高级重投控制的业务直接依赖 `spring-modulith-events-api`，保持薄胶水原则。

## 7. 与现有模块的协同

| 模块 | 关系 |
|---|---|
| base | 零改动（IntegrationEvent 放 event 模块，理由见 §4.2） |
| logging | 兜底日志面向 SLF4J；P2：验证 Boot 4.1 的 TaskDecorator 装配，做 MDC（traceId）向异步监听器的传播 |
| exception | 无直接耦合：异步监听器异常不进 `@RestControllerAdvice`（无请求上下文），以日志 + 元事件收口；元事件监听器可自行桥接到告警 |
| jackson / data | 可靠层复用：Jackson 3 序列化 + DataSource 存储，均由既有模块提供 |
| scheduling | 结构模板（AutoConfiguration + imports + 属性开关），无运行时耦合 |

## 8. 包结构与测试计划

```
com.github.henc.integrateboot.event
├── EventBus.java / ApplicationEventBus.java
├── IntegrationEvent.java
├── AsyncEventListener.java
├── EventListenerFailedEvent.java
├── EventBusAsyncExceptionHandler.java
└── config/
    ├── EventBusAutoConfiguration.java
    ├── EventBusProperties.java
    └── ReliabilityDefaultsEnvironmentPostProcessor.java   (+ META-INF/spring.factories 注册)
```

模块单测（JUnit，无 Spring 上下文的委托逻辑 + ApplicationContextRunner 装配断言）：

- `ApplicationEventBusTest`：委托、批量发布；
- `EventBusAutoConfigurationTest`：默认装配 EventBus / enabled=false 全不装配 / async 关闭时不注册 AsyncConfigurer / 应用已有 AsyncConfigurer 时不覆盖。

测试应用 IT（H2，`*IT` 命名）：

- `EventBusIT`：同步收发；`@AsyncEventListener` 异步到达（latch 断言）；`AFTER_COMMIT` + 事务回滚不触发；监听器抛异常 → error 日志 + 元事件收到 + 无递归；
- `EventReliabilityIT`：**独立可选任务**（复刻"动态数据源测试"模式，见 commit 04bd82d）——test app 以 testImplementation 引入 Modulith 两个 artifact；用例：事务性事件发布 → 监听器首次失败 → 断言登记簿存在 FAILED 记录 → 经 `IncompleteEventPublications` 重投成功。

## 9. 关键设计决策记录

| # | 决策 | 理由 |
|---|---|---|
| 1 | 不引入任何第三方 EventBus | Guava EventBus 官方劝退；Reactor/Axon 范式不匹配；Spring 原生 + Modulith 覆盖全部需求 |
| 2 | 内核纯封装，零新依赖 | 框架定位是封装而非发明；Framework 7 事件 API 稳定 |
| 3 | 事件无强制基类/信封 | 保持 Spring 原生零摩擦；可靠层序列化也不要求基类 |
| 4 | 同步监听异常向发布方传播 | 事务耦合是同步事件语义， multicaster 侵入改造不值 |
| 5 | `@EnableAsync` 由本模块统一收口，执行器落回 Boot 默认（虚拟线程友好） | 消除业务应用重复配置；不另建线程池 |
| 6 | Modulith 依赖 compileOnly + 显式引入 | 防止其 AutoConfiguration 泄漏激活（自动建表等副作用） |
| 7 | 可靠层默认值经 EnvironmentPostProcessor 注入，可被原生配置覆盖 | 一个开关开箱即用，同时不锁死 Modulith 原生能力 |
| 8 | 可靠层薄胶水，不包装 Modulith API | 降低升级耦合（Modulith minor 严格绑定 Boot minor） |

## 10. 后续扩展（不在本期）

- **分布式桥**：`integrate-boot-event` 内 `@ConditionalOnClass(RedissonClient)` 的桥接配置，本地 `IntegrationEvent` 事件经 RTopic 广播到全部实例（广播语义；不能丢消息的场景文档指引 RReliableTopic / 队列结构）；
- **事件外置**：Modulith `spring-modulith-events-kafka` 等，路由声明 `@Externalized`；
- **可观测**：Micrometer 埋点（发布/消费计数、异步监听耗时），配合 logging 模块 MDC 传播。

## 11. 实施步骤

1. 骨架：settings.gradle / libs.versions.toml / bom / 模块 build.gradle（Modulith 声明 compileOnly，注释写明版本绑定关系）；
2. core：`EventBus`、`ApplicationEventBus`、`IntegrationEvent`、`@AsyncEventListener`、`EventListenerFailedEvent`、`EventBusAsyncExceptionHandler`；
3. config：自动配置 + imports + properties + EnvironmentPostProcessor（含 spring.factories）；
4. 模块单测；
5. starter 挂接；
6. 测试应用 `EventBusIT` + 独立可选任务 `EventReliabilityIT`（重点验证 §4.3 的 @Async 元注解组合、Modulith 2.1.1 ↔ Boot 4.1.0 兼容性）；
7. README 增加 `## integrate-boot-event` 章节（含监听器注解矩阵表、可靠层启用指引）；**AGENTS.md 模块清单同步加一行**；
8. 提交：`[A]新增事件模块integrate-boot-event（内核）` / 可靠层与文档可按需拆分后续提交。

## 12. 落地记录（实施偏离与确认）

1. **Boot 4 的 AsyncConfigurer 协作方式**（设计稿 §4.4 的"落回 Boot 执行器"落地为显式排序）：Boot 4 的 `TaskExecutionAutoConfiguration` 自带 `AsyncConfigurer` 包装机制（`@ConditionalOnBean(AsyncConfigurer)` 时包装应用侧 configurer，executor 回落 `applicationTaskExecutor`、异常 handler 完全委托）。模块因此加 `@AutoConfigureBefore(TaskExecutionAutoConfiguration.class)`，让 Boot 包装我们的 configurer 而不是先注册默认值把 `@ConditionalOnMissingBean` 顶掉（该问题由 `EventBusIT` 端到端暴露并验证修复）。
2. **`@AsyncEventListener` 元注解组合成立**：`@EventListener` 与 `@Async` 均支持元注解组合，模块级 `AsyncEventListenerWiringTest` 与应用级 `EventBusIT` 双层验证通过。
3. **`@IntegrateBoot` 约定扫描扩展**：新增 `**.listener.**` 层（监听器 bean 的发现需要）。刻意未加 `**.event.**`——它会连带扫描本模块 `...integrateboot.event.config` 下的 `@AutoConfiguration`，违反 Boot"自动配置类不得经组件扫描注册"的约束；事件载体类（record，无注解）仍可放 `xxx.event` 包。
4. **测试隔离**：`EventBusIT` 使用独立内存库（`eventbusdb`，复刻 `ErrorHandlingIT` 模式）——提交型用例必须真实落库（否则 AFTER_COMMIT 不触发），不能污染共享库；`EventReliabilityIT` 中嵌套监听器经 `@TestConfiguration` + `@Bean` 注册（约定扫描不覆盖测试类所在包）。
