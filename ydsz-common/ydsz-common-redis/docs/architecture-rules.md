# Redis 模块架构规则

本文档定义了 `ydsz-common-redis` 模块的架构约束规则，供消费方项目通过 ArchUnit 或 SonarQube 等静态分析工具执行，确保代码遵循分层与解耦原则。

## 规则 1：门面类弃用约束

**ID**: `redis-001`

**描述**: 新代码禁止直接注入 `RedisService` 门面类，应直接注入对应的 Ops 子组件。

**严重程度**: WARNING（存量代码不追溯，新代码 PR 卡控）

**ArchUnit 规则示例**:

```java
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

public class RedisArchitectureRules {

    private static final JavaClasses classes = new ClassFileImporter()
            .importPackages("com.njydsz");

    /**
     * 禁止直接依赖 RedisService 门面类（新代码应注入子组件）
     */
    public static final ArchRule NO_DIRECT_REDIS_SERVICE_DEPENDENCY =
            noClasses().that().resideInAPackage("..service..")
                    .and().haveSimpleNameNotEndingWith("Test")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("com.njydsz.common.redis.service.RedisService")
                    .because("RedisService 已弃用，请直接注入 RedisStringOps、RedisHashOps 等子组件");
}
```

**豁免条件**:

- 存量代码：已在使用的代码无需修改，但迁移优先级高的模块应在迭代中逐步替换
- 测试代码：测试类中可以注入 RedisService 进行集成测试

## 规则 2：子组件依赖约束

**ID**: `redis-002`

**描述**: 业务代码只能依赖对应类型的 Ops 组件，不得跨类型使用（例如需要 Hash 操作就注入 RedisHashOps，不得通过 RedisService 间接调用）。

**严重程度**: INFO（建议级别）

**说明**:

```java
// ❌ 不推荐：通过门面类间接使用
public class UserService {
    @Autowired
    private RedisService redisService;  // 门面类

    public void updateUser(User user) {
        redisService.hSet("user:" + user.getId(), "name", user.getName());
    }
}

// ✅ 推荐：直接注入子组件
public class UserService {
    @Autowired
    private RedisHashOps hashOps;

    public void updateUser(User user) {
        hashOps.hSet("user:" + user.getId(), "name", user.getName());
    }
}
```

## 规则 3：CacheProvider 接口约束

**ID**: `redis-003`

**描述**: 注解缓存切面（如 YdszCacheable）必须通过 `CacheProvider` 接口操作缓存，不得直接依赖 `RedisService` 或具体 Ops 组件。

**严重程度**: ERROR（强制）

**ArchUnit 规则示例**:

```java
/**
 * 注解切面类必须通过 CacheProvider 接口操作缓存
 */
public static final ArchRule CACHE_ASPECT_MUST_USE_PROVIDER =
    classes().that().areAnnotatedWith("org.springframework.stereotype.Aspect")
            .and().resideInAPackage("..redis..")
            .should().onlyAccessClassesThat(
                haveFullyQualifiedName("com.njydsz.common.redis.service.CacheProvider")
                    .or(arePrimitives())
                    .orHaveQualifiedNameStartingWith("java.")
            )
            .because("缓存切面必须通过 CacheProvider 接口解耦，不得直接依赖具体实现");
```

## 规则 4：禁止循环依赖

**ID**: `redis-004`

**描述**: ydsz-common-redis 模块不得依赖 ydsz-common-lock 等可能形成循环的模块。

**严重程度**: ERROR（强制）

**ArchUnit 规则示例**:

```java
/**
 * Redis 模块禁止反向依赖 Lock 模块
 */
public static final ArchRule NO_CIRCULAR_DEPENDENCY =
    noClasses().that().resideInAPackage("..redis..")
            .should().dependOnClassesThat()
            .resideInAPackage("..lock..")
            .because("ydsz-common-redis 不得依赖 ydsz-common-lock，避免循环依赖");
```

## 迁移路线图

| 版本 | 里程碑 |
|---|---|
| 1.x | RedisService 标记 `@Deprecated`，新代码通过 ArchUnit 卡控 |
| 2.0 | 完成存量代码 80% 迁移，RedisService 添加 `@Deprecated(forRemoval = true)` |
| 3.0 | 移除 RedisService 门面类，所有代码通过 ops 组件或 CacheProvider 接口操作 |

## 对消费方接入指南

在消费方项目的测试模块中添加 ArchUnit 依赖：

```xml
<dependency>
    <groupId>com.tngtech.archunit</groupId>
    <artifactId>archunit-junit5</artifactId>
    <version>1.3.0</version>
    <scope>test</scope>
</dependency>
```

然后在测试类中执行规则：

```java
@AnalyzeClasses(packages = "com.njydsz")
class RedisArchitectureTest {
    @ArchTest
    static final ArchRule noDirectRedisService = RedisArchitectureRules.NO_DIRECT_REDIS_SERVICE_DEPENDENCY;
}
```
