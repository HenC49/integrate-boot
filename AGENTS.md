# AGENTS.md — integrate-boot 工作区指令

面向业务系统的基础框架：封装数据访问、缓存、Redis、日志、异常、认证等通用能力，供业务服务按模块引入。技术栈：Java 21 + Spring Boot 4.1 + Gradle（Groovy DSL）。

## 结构

- `bom/` — BOM（java-platform），全项目依赖版本的唯一来源；artifactId 为 `integrate-boot-bom`
- `module/` — 12 个库模块：`base`（纯 Java 实体/日期工具/`@Job` 任务注解，无框架依赖）、`data`（MyBatis-Flex + 动态数据源）、`jackson`、`logging`（SLF4J + Log4j2）、`exception`（全局异常 + ResultInfo）、`cache`（Caffeine）、`redis`（Redisson）、`authentication`（OAuth2 授权服务）、`resource-server`、`scheduling`（XXL-JOB 定时任务：发现 `@Job` 注解方法与 `SchedulingTaskHandler` bean，运行时经 `integrate-boot.scheduling.enabled` 选择性启用）、`event`（进程内事件总线：`EventBus` 门面封装 Spring 原生事件 + `@AsyncEventListener` + 统一 `@EnableAsync`；Modulith outbox 可靠层可选，`integrate-boot.event.reliability.enabled` 开关，Modulith 依赖 compileOnly 不随 POM 传递）、`starter`（聚合入口 + `@IntegrateBoot`，约定层扫描含 `**.listener.**`）
- `test/integrate-boot-test/` — 端到端示例应用（H2 内存库），集成测试以 `*IT` 命名
- `gradle/libs.versions.toml` — 版本目录；`gradle/publishing.gradle` — 库模块共享发布约定；根 `build.gradle` — 全子项目通用配置

## 常用命令

优先用 Taskfile 包装（需 go-task，本地自动加载 `.env`），否则直接用 `./gradlew`：

```bash
task build          # 等价 ./gradlew build（编译 + 全部测试）
task test           # 仅测试
task ci             # clean + --no-daemon 完整构建
task publish-local  # BOM + 全部模块发布到 build/repo
task set-version VERSION=x.y.z
task dynamic-test   # 动态数据源 IT（独立 JVM，可选）
task reliability-test # 事件可靠层 IT：Modulith outbox（独立 JVM，可选）
```

单模块发布：`task publish-module MODULE=integrate-boot-data`。单模块测试：`./gradlew :module:integrate-boot-base:test`。

要求 JDK 21；Gradle 已开启 configuration-cache / parallel / caching。

## 硬性约束（改代码前必读）

- **禁用 Logback**：平台统一 SLF4J + Log4j2。`spring-boot-starter-logging` 会在根 `build.gradle` 全局剔除；任何新增 Spring Boot starter 依赖时必须再显式 `exclude group: 'org.springframework.boot', module: 'spring-boot-starter-logging'`（排除项随 POM 传递给外部消费者）。logging 模块有启动守卫检测泄漏。
- **编译参数 `-parameters`**：由根 `build.gradle` 统一注入。Spring Framework 7 从字节码解析参数名，缺失会导致 `@PathVariable` 等运行时报错；新模块不要破坏该配置。
- **版本只改一处**：新增/升级依赖在 `gradle/libs.versions.toml` 声明，并视需要加入 `bom/build.gradle` 的 constraints；模块 build.gradle 一律 `platform(project(':bom'))` 引版本，不写版本号。
- **测试平台对齐**：Spring Boot 4.1 带 JUnit Platform 6.x，Gradle 内置 launcher 是 1.x，依赖根 build.gradle 注入的 `junit-platform-launcher` 对齐，勿删。
- **Spring Boot 4 拆分点**：MockMvc 自动配置在 `spring-boot-starter-webmvc-test`（不在 starter-test 内）。

## 架构与编码约定

- 依赖方向：`base` 不依赖任何框架；各功能模块可依赖 base；`starter` 聚合全部；测试应用只依赖 starter。新能力做成 `module/integrate-boot-<name>` 并在 `settings.gradle` 注册，再挂到 starter。
- 新建库模块：`java-library` 插件 + `apply from: "$rootDir/gradle/publishing.gradle"`（自动带 sources/javadoc jar）。
- 统一响应信封 `ResultInfo`（success/code/message/requestId/result），异常经 `exception` 模块的 `@RestControllerAdvice` 渲染；业务代码不要自造返回结构。
- 日志面向 SLF4J 编码，勿直接依赖 Log4j2 API。
- 构建脚本注释详尽（英文），是设计意图的权威说明，改动前先读周边注释并保持风格。

## 其他

- 提交信息风格：`[A]新增xxx` / `[M]修改xxx`（中文，A=新增，M=修改）。
- `.env`（git-ignored）存放样例应用密钥如 `REDIS_PASSWORD`；`logs/` 是 logging 模块默认配置产出的运行日志，均已忽略。
- 改动认证/资源服务、动态数据源等敏感区前，先读 `README.md` 对应章节。
