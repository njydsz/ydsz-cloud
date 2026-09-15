# ydsz-common-seata

> Seata 分布式事务桥接层（L5 业务服务层）— TCC/AT/XA 模式委托 seata-spring-boot-starter 原生能力，提供配置前缀映射 + 健康检查 + Spring TX 集成

基于 Seata 框架封装分布式事务能力，通过 Spring Boot 自动配置桥接 `seata-spring-boot-starter`，将 `ydsz.seata.*` 配置前缀映射到 Seata 原生属性，同时提供健康检查与 Spring `PlatformTransactionManager` 集成，使业务模块以统一方式启用分布式事务，无需直接耦合 Seata 原生 API。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L5 业务服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 提供 Seata 分布式事务配置前缀映射、健康检查、Spring TX 集成能力 |
| **依赖** | common-core、common-exception、common-util、spring-tx（必选）；seata-spring-boot-starter（optional） |
| **版本** | 26.09.01 |

## 生命周期与成熟度标注

> 依据《云顶编码规范》§33.7：公共能力储备须显式标注生命周期，避免"裸奔资产"。

| 属性 | 值 |
|---|---|
| **生命周期阶段** | 能力储备（Capability Reserve）— 已具备完整自动装配与健康检查，**尚无业务消费方** |
| **消费现状** | 2026-09-15 复用手册扫描：业务模块对该模块的直接 import 为 0（供给侧 30 个模块中唯一零消费） |
| **启用前提** | ① 业务模块显式引入 `seata-spring-boot-starter`（optional 依赖）；② 配置 `ydsz.seata.enabled=true`；③ 存在跨库/跨服务写场景 |
| **落地路径** | 按 ADR《Seata 分布式事务 PoC 设计》先完成 PoC 验证，再产生首个业务消费方 |
| **判定说明** | 零消费**不构成过度设计**（工具/基础能力按需供给属正常形态，§33.7）：本模块以运行时自动装配供给，非编译期引用型能力 |
| **测试义务** | 储备能力须补自动化验证（配置映射 + 自动装配条件 + 健康检查降级路径）；当前模块 `src/test` 为空 —— 属已知负债 |
| **复核时点** | 下次复用深度检查（脚本供给侧零消费项若仍为 0，需重新评估保留必要性） |

## 核心能力

### 1. 配置前缀映射

将 `ydsz.seata.*` 配置属性桥接到 Seata 原生配置（`seata.*`），使业务模块通过统一前缀管理 Seata 配置，无需关心 Seata 原生属性命名。

| 类 | 说明 |
|---|---|
| `SeataProperties` | Seata 配置属性（`ydsz.seata.*`），支持 enabled / application-id / tx-service-group / 各模式开关等映射 |

### 2. Spring Boot 自动配置

自动装配 Seata 核心组件，桥接 Spring `PlatformTransactionManager`，使 `@Transactional` 注解无缝支持分布式事务。

| 类 | 说明 |
|---|---|
| `SeataAutoConfiguration` | Seata 自动配置（`ydsz.seata.enabled=true` 时激活），注册 `GlobalTransactionScanner` 与 Seata 事务管理器代理 |

### 3. 健康检查

暴露 Seata TC 连接状态到 Spring Boot Actuator 健康端点，便于运维监控分布式事务可用性。

| 类 | 说明 |
|---|---|
| `SeataHealthIndicator` | Seata 健康检查，检测 TC 连接状态、TransactionManager 初始化结果 |

## 接入方式

### 1. 添加 POM 依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-seata</artifactId>
</dependency>
```

启用分布式事务还需引入 Seata Starter（optional 依赖）：

```xml
<dependency>
    <groupId>io.seata</groupId>
    <artifactId>seata-spring-boot-starter</artifactId>
</dependency>
```

### 2. 配置启用

```yaml
ydsz:
  seata:
    enabled: true                    # 启用 Seata 分布式事务（默认 false）
    application-id: ${spring.application.name}
    tx-service-group: default_tx_group
```

## 支持的 Seata 模式

本模块**不实现**任何分布式事务模式，TCC / AT / XA 模式**委托 `seata-spring-boot-starter` 原生能力**，仅负责：

- 统一配置前缀映射（`ydsz.seata.*` → `seata.*`）
- 健康检查集成
- Spring `PlatformTransactionManager` 桥接

各模式的具体配置请参考 Seata 官方文档。

## 类清单

| 类 | 路径 | 说明 |
|---|---|---|
| `SeataProperties` | `com.njydsz.common.seata.config` | Seata 配置属性（`ydsz.seata.*`） |
| `SeataAutoConfiguration` | `com.njydsz.common.seata.config` | Spring Boot 自动配置 |
| `SeataHealthIndicator` | `com.njydsz.common.seata.health` | Spring Boot 健康检查 |

## 注意事项

1. **Seata Starter 为可选依赖**：`seata-spring-boot-starter` 在本模块 POM 中声明为 `<optional>true</optional>`，业务模块需显式引入才能启用分布式事务。
2. **不独立部署**：本模块作为公共依赖库随业务服务一同部署，不单独运行。
3. **模式能力委托**：TCC / AT / XA 各模式的具体实现由 Seata 原生框架提供，本模块不做封装。
4. **Spring TX 集成**：启用后 `@Transactional` 注解将自动被 Seata 全局事务代理覆盖，无需额外配置。

## 变更记录

- **26.09.01**（2026-08-16）：初始版本，提供配置前缀映射、自动配置、健康检查能力
- **26.09.15**：补充生命周期与成熟度标注（能力储备 / 零业务消费 / 测试义务），依据 2026-09-15 公共模块复用深度检查
