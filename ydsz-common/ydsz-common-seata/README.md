# ydsz-common-seata

> Seata 分布式事务桥接层（L5 业务服务层）— TCC/AT/XA/SAGA 模式委托 seata-spring-boot-starter 原生能力，提供 **XID 跨服务传播** + **动态数据源适配** + **运行时健康检查** + **事务 Metrics** + **属性自动桥接**

基于 Seata 框架封装分布式事务能力，通过 Spring Boot 自动配置桥接 `seata-spring-boot-starter`，提供从配置到运行时监控的完整分布式事务治理链路。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L5 业务服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 提供 Seata 分布式事务全链路治理：XID 传播、数据源适配、健康检查、Metrics、属性桥接 |
| **依赖** | common-core、common-exception、common-util、common-jdbc、spring-tx（必选）；seata-spring-boot-starter（optional） |
| **版本** | 26.09.01 |

## 生命周期与成熟度标注

> 依据《云顶编码规范》§33.7：公共能力储备须显式标注生命周期，避免"裸奔资产"。

| 属性 | 值 |
|---|---|
| **生命周期阶段** | 能力储备（Capability Reserve）— 已具备完整运行时治理能力，**尚无业务消费方** |
| **启用前提** | ① 业务模块引入 `seata-spring-boot-starter`；② 配置 `ydsz.seata.enabled=true`；③ 存在跨服务写场景 |
| **落地路径** | 按 ADR-004 / ADR-012 完成 PoC 验证后产生首个业务消费方 |

## 核心能力

### 1. XID 跨服务传播（规范 YDIZ-TX-004）

自动在 Feign 调用链中透传 Seata XID（全局事务 ID），确保分布式事务上下文连续性。

| 类 | 说明 |
|---|---|
| `FeignXidRequestInterceptor` | Feign 请求拦截器 — 读取当前线程 XID 并注入到请求 Header `TX_XID` |
| `XidServletFilter` | Servlet 过滤器 — 接收上游 `TX_XID` Header 并绑定到 Seata `RootContext` |

**特性**：通过反射调用 Seata API，无编译期硬依赖；无 Seata 时自动降级为无操作。

### 2. Seata AT 模式与动态数据源适配（ADR-004 风险化解）

解决 Seata `DataSourceProxy` 与 YDSZ `DynamicRoutingDataSource` 的集成冲突，使 AT 模式在动态数据源场景下正确生成 `undo_log`。

| 类 | 说明 |
|---|---|
| `SeataDynamicDataSourceAdapter` | 继承 `DynamicRoutingDataSource`，`addDataSource` 时自动包装为 `DataSourceProxy` |

**装配顺序**：物理 DataSource → Seata DataSourceProxy → 注册到 DynamicRoutingDataSource。

### 3. 事务 Metrics（规范 §25.7 强制要求）

通过 AOP 切面自动记录分布式事务执行次数和耗时。

| 类 | 说明 |
|---|---|
| `SeataTransactionMetricsAspect` | 拦截 `@YdszGlobalTransactional` 方法，记录 `seata.tx.count` + `seata.Tx.duration`（含 P50/P90/P99 分位数） |

### 4. 运行时健康检查（规范 §25.7 强制要求）

从配置级升级为运行时级，探测 Seata `TransactionManager` 初始化状态。

| 类 | 说明 |
|---|---|
| `SeataHealthIndicator` | `/actuator/health/seata` 端点 — 含 TC 连通性、TM 初始化状态、数据源代理模式 |

### 5. 属性自动桥接（EnvironmentPostProcessor）

通过 `SeataPropertyBridgePostProcessor` 将 `ydzs.seata.*` 自动桥接到 Seata 原生 `seata.*` 命名空间，业务方只需维护 `ydsz.seata.*` 前缀。

| 类 | 说明 |
|---|---|
| `SeataPropertyBridgePostProcessor` | EnvironmentPostProcessor 实现，宽松桥接策略（原生配置优先） |

### 6. 启动期配置校验（Fail Fast）

| 类 | 说明 |
|---|---|
| `SeataConfigurationValidator` | `@PostConstruct` 校验配置合法性，不合规时抛出 `IllegalArgumentException` 阻止启动 |

### 7. 规范合规注解（规范 YDIZ-TX-001~004）

| 类 | 说明 |
|---|---|
| `@YdszGlobalTransactional` | 封装原生 `@GlobalTransactional`，内置合规默认值（timeoutMillis=30000, rollbackFor=Exception） |

## 接入 Checklist（规范 §25.8）

业务模块首次接入分布式事务时，需完成以下步骤：

- [ ] `pom.xml` 中添加 `ydsz-common-seata` 依赖
- [ ] 显式引入 `seata-spring-boot-starter`
- [ ] `application.yml` 中配置 `ydsz.seata.enabled: true` 及模式
- [ ] 如使用 TCC 模式：实现 `TccAction` 接口，确保 Try/Confirm/Cancel 幂等
- [ ] 如使用 AT 模式：确保 `undo_log` 表已初始化
- [ ] AT 模式 + 动态数据源：`SeataDynamicDataSourceAdapter` 自动注册，无需额外配置
- [ ] XID 跨服务传播：`FeignXidRequestInterceptor` + `XidServletFilter` 自动注册，无需额外代码
- [ ] 生产环境：配置 `ydsz.seata.xid-sign-secret`（建议 ≥ 16 位）
- [ ] 验证健康检查端点 `/actuator/health/seata` 返回 `transactionManager=initialized`
- [ ] 确认 Metrics 端点 `/actuator/metrics/seata.tx.duration` 可访问

## 配置参考（最小生产配置）

```yaml
ydsz:
  seata:
    enabled: true
    default-type: LOCAL              # 默认 LOCAL 降级，按需切换
    data-source-proxy-mode: AT       # 自动代理数据源
    enable-auto-data-source-proxy: true
    tx-service-group: default_tx_group
    xid-sign-secret: ${SEATA_XID_SIGN_SECRET}  # 生产必配
    undo-log:
      serialization: jackson
      table-name: undo_log
    tm:
      global-transaction-timeout: 30000    # 规范 YDIZ-TX-002 要求 ≤ 30000ms
      commit-retry-count: 5
      rollback-retry-count: 5
```

## 使用示例

```java
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final InventoryClient inventoryClient;

    /**
     * 使用 @YdszGlobalTransactional 替代原生 @GlobalTransactional。
     * timeoutMillis 默认 30000ms（规范 YDIZ-TX-002），无需显式声明。
     * Feign 调用自动透传 XID，无需手动注入 Header。
     */
    @YdszGlobalTransactional(name = "order-create-order")
    public void createOrder(OrderDTO dto) {
        orderRepository.save(order);
        inventoryClient.deduct(dto.getSkuId(), dto.getQuantity());
    }
}
```

## 类清单

| 类 | 路径 | 说明 |
|---|---|---|
| `SeataProperties` | `com.njydsz.common.seata.config` | Seata 配置属性（`ydsz.seata.*`） |
| `SeataAutoConfiguration` | `com.njydsz.common.seata.config` | Spring Boot 自动配置（注册全部 Bean） |
| `SeataPropertyBridgePostProcessor` | `com.njydsz.common.seata.config` | 属性桥接（ydsz.seata → seata） |
| `SeataConfigurationValidator` | `com.njydsz.common.seata.validator` | 启动期配置校验 |
| `SeataHealthIndicator` | `com.njydsz.common.seata.health` | 运行时健康检查 |
| `FeignXidRequestInterceptor` | `com.njydsz.common.seata.xid` | XID Feign 传播 |
| `XidServletFilter` | `com.njydsz.common.seata.xid` | XID 接收绑定 |
| `SeataDynamicDataSourceAdapter` | `com.njydsz.common.seata.datasource` | AT + 动态数据源适配 |
| `SeataTransactionMetricsAspect` | `com.njydsz.common.seata.metrics` | 事务 Metrics 切面 |
| `@YdszGlobalTransactional` | `com.njydsz.common.seata.annotation` | 规范合规注解 |

## 注意事项

1. **Seata Starter 为可选依赖**：业务模块需显式引入 `seata-spring-boot-starter` 才能启用分布式事务
2. **不独立部署**：随业务服务一同部署
3. **XID 传播通过反射**：编译期不依赖 Seata/Feign，运行时自动降级
4. **TransactionManager 探测**：健康检查通过反射探测 Seata API，无 Seata 时降级为配置级报告
5. **动态数据源适配**：当同时存在 Seata `DataSourceProxy` 和 `DynamicRoutingDataSource` 时自动注册 `SeataDynamicDataSourceAdapter`

## 规范引用

| 规则 ID | 等级 | 内容 | 实现位置 |
|---------|------|------|---------|
| YDIZ-TX-001 | P0 | @GlobalTransactional 禁止嵌套 | `@YdszGlobalTransactional` 替换原生 |
| YDIZ-TX-002 | P0 | timeoutMillis ≤ 30000ms | `SeataProperties.Tm.globalTransactionTimeout = 30000` |
| YDIZ-TX-003 | P1 | TCC 模式必须幂等 | 文档约束，SDK 能力待 TCC 模块补充 |
| YDIZ-TX-004 | P1 | XID 跨服务传播 | `FeignXidRequestInterceptor` + `XidServletFilter` |
| §25.7 | 强制 | 暴露 seata.tx.count / seata.tx.duration | `SeataTransactionMetricsAspect` |

## 变更记录

- **26.09.20**（2026-09-20）：
  - 修复 YDIZ-TX-002 P0 违规：超时默认值 60000 → 30000ms
  - 补齐 XID 跨服务传播链路（`FeignXidRequestInterceptor` + `XidServletFilter`，反射调用避免硬依赖）
  - 新增 `SeataDynamicDataSourceAdapter` 解决 AT + 动态数据源集成冲突
  - 新增 `SeataTransactionMetricsAspect` 暴露事务 Metrics（seata.tx.count / seata.tx.duration）
  - 升级 `SeataHealthIndicator` 为运行时级（TransactionManager 初始化探测）
  - 新增 `SeataPropertyBridgePostProcessor` 实现属性自动桥接
  - 新增 `SeataConfigurationValidator` 启动期配置校验
  - 新增 `@YdszGlobalTransactional` 规范合规注解
  - POM 补充 `ydsz-common-jdbc` 和 `spring-boot-starter-aop` optional 依赖
- **26.09.15**：补充生命周期与成熟度标注
- **26.09.01**（2026-08-16）：初始版本
