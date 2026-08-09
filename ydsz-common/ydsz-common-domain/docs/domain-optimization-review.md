# ydsz-common-domain 模块优化与完善建议

> 对标行业主流竞品与互联网大厂研发规范，基于 `ydsz-common-domain` 最新代码（33 个类 / 约 4360 行）的实测分析。
> 分析维度：架构优化、功能增强、性能提升、体验改善、过度设计（精简）。

---

## 0. 模块现状（实测）

| 指标 | 现状 |
| --- | --- |
| 类数量 / 代码行 | 33 个类 / 约 4360 行 |
| 单元测试 | **零**（`src/test` 不存在，但 `pom.xml` 声明了 test 依赖，形同虚设） |
| 编译状态 | **当前源码无法编译**（2 处阻断，已提交入库） |
| 外部采用率 | 33 类中仅约 9 个被业务模块真实 `import` 引用，其余为 0 引用或仅模块内部自引用 |
| 分页 API 代际 | 三代并存：旧 `PageQuery/PageResult`、弃 `Slice/CursorPage/PageSlice`、新 `SliceQuery/SliceResult` |

**外部采用率实测表（按 import 精确统计，已排除模块自身与 target）**

| 采用情况 | 类 |
| --- | --- |
| 重度使用 | `PageQuery`(23)、`PageResult`(17)、`DataScopeType`(11)、`BaseStatusEnum`(11)、`ServiceType`(5)、`IdentityType`(4)、`TreeBuilder`(2)、`OrderItem`(1) |
| 0 引用（死代码/未落地） | `Slice`、`PageSlice`、`CursorPage`、`PageQueryFactory`、`DomainEvent`、`IdempotentOperation`、`ValidationGroups`、`StateTransitionUtil`、`SliceQuery`、`SliceResult`、`DeepPaginationRisk`、`DeepPaginationException`、`CursorDirection`、`Command`、`DTO`、`VO`、`TreeNode` |

---

## 1. P0 阻断：模块当前无法编译（必须立即修复）

> 这两处错误会让 `mvn install ydsz-common-domain` 直接失败，进而阻断所有依赖它的微服务构建。

### 1.1 `Slice.java` —— 非法语法（6 个编译错误）

`javac` 实测报错（已确认）：
```
Slice.java:157: 错误: 非法的表达式开始 / 需要 ->
Slice.java:226: 错误: 非法的表达式开始 / 需要 ->
Slice.java:241: 错误: 非法的表达式开始 / 需要 ->
```
根因：`Collections.emptyList<>()` 是**非法 Java 语法**——菱形操作符 `<>` 只能用于构造器/方法声明，不能用于方法调用。三处均写成了 `… : Collections.emptyList<>();`。

讽刺的是：该类已 `@Deprecated(since = "1.8.0", forRemoval = true)`，被 `SliceQuery + SliceResult` 取代，外部 **0 引用**。**死代码却拖垮了整个模块的构建。**

### 1.2 `PageQueryFactory.java` —— 调用不存在的方法

第 53 行：
```java
PageQuery query = PageQuery.of(pageNum, pageSize);   // PageQuery 根本没有 of(...) 静态方法！
```
`PageQuery` 仅提供实例方法，无任何 `static of(...)`。该类同样 `@Deprecated`、**0 引用**。

**修复见附录 A / B。**

---

## 2. 维度一：架构优化

### 2.1 分页 API 三代同堂，亟需收敛
- **旧**：`PageQuery` / `PageResult`（重度使用 23 / 17 处）
- **弃**：`Slice` / `CursorPage` / `PageSlice`（已 `@Deprecated(forRemoval=true)`，0 引用；且 `Slice` 还编译不过）
- **新**：`SliceQuery` / `SliceResult`（1.8.0 引入，0 引用，**未落地**）

三套并存 + 大面积 `@Deprecated` 标记，让开发者"不知道该用哪个"。**建议**：不要三套并存。鉴于新设计 0 采用，先冻结 `SliceQuery/SliceResult` 推广，把旧设计稳定化；若确需演进，制定明确的迁移排期（带版本号与到期日），而不是留三套半成品。

### 2.2 配置驱动形同虚设：`PageQuery.runtimeProperties` 永远为 null
`PageQuery` 持有 `runtimeProperties`（由 `PageQueryFactory` 注入），但：
- 工厂 **0 采用**，业务侧全部走**继承**（`extends PageQuery`）；
- 因此 `runtimeProperties` 始终为 `null`，`DomainProperties` 里 `maxDepth / maxSearchKeyLength / idempotent.defaultExpireSeconds` 等配置项**无任何消费方**。

**建议**：要么在统一构造入口（如 `PageQuery.builder()` 的默认注入或 `BaseQuery` 构造器）把配置绑定进去，要么**直接删除这套配置绑定能力**——只声明不生效的"可配置"是更危险的误导。

### 2.3 领域事件机制设计缺陷（AOP 对 `new` 出来的实体无效）
`DomainEventAspect` 使用 `@Around("@annotation(domainEvent)")`。Spring AOP 的 `@annotation` 切点**只对 Spring Bean（容器代理对象）生效**；而文档示例却把 `@DomainEvent` 标注在 **`new` 出来、未被容器管理的实体方法**上——这类方法**永远不会被切面拦截**。

且实测：`@DomainEvent` 注解 0 引用，`EventRegistry` 仅 `BaseEntity` 自用，外部 0 使用。整个事件体系基本是空的。

**建议**：移除 `DomainEvent` 注解 + `DomainEventAspect`（文档示例本身无法工作），保留 `BaseEntity` 已实现的显式 `registerEvent / pullDomainEvents` 模式即可，避免误导开发者。

### 2.4 依赖与模块边界腐化
- 为**已死的事件包**保留了 `spring-context / spring-aop / aspectj` 三个依赖；
- `DataScopeType / IdentityType / ServiceType` 标注 `@Deprecated(since = "1.7.0", forRemoval = true)`，Javadoc 声称"迁移到 common-rbac / common-tenant / common-gateway"，但**迁移从未落地**，仍在 11 / 4 / 5 处被使用。

**建议**：要么真正完成迁移并删除旧类，要么**撤销 deprecation 标记**——不要留下"即将移除"的空头承诺（这会污染 `jdeprscan` 报告且让调用方无所适从）。

---

## 3. 维度二：功能增强（让已定义的能力真正闭环）

### 3.1 深度分页风险能力完全悬空
`DeepPaginationRisk` 枚举 + `DeepPaginationException` 已定义（0 引用），但其 Javadoc 宣称的调用方 **`PageQuery#assessPaginationRisk()` 根本不存在**。即：能力定义好了，但没有任何代码调用它。

**建议**：在 `PageQuery` 上真正实现 `assessPaginationRisk()`（读取配置阈值：warn=10000 / reject=50000），并在 `SafeQueryInnerInterceptor`（已存在）中调用形成闭环——这是大厂对深分页的标准防护。

### 3.2 校验分组 `ValidationGroups` 未被使用
0 引用。建议在统一 Controller 基类或全局 `@Validated` 切面中落地，避免各 DTO 重复写 `@NotNull`/`@Null`。

### 3.3 状态机能力空转
`BaseStatusEnum.pathTo/successors` 已 `@Deprecated`，新 `StateTransitionUtil` 也 **0 引用**。整条状态机相关能力没有真实业务样例。

**建议**：选定 `StateTransitionUtil`，提供 1–2 个真实业务样例（如订单/审批状态流转）落地；否则这套 BFS 路径推导代码纯粹是维护负担。

---

## 4. 维度三：性能提升

| 项 | 说明 | 优先级 |
| --- | --- | --- |
| 深度分页缺少实际防护 | 见 3.1，`assessPaginationRisk` 未实现，大 offset 查询无阈值拦截，易拖垮 DB | 高 |
| 分页结果防御性拷贝 | `Slice/SliceResult/PageQuery` 的 `records` 直接暴露可变 `List`，外部可改内部集合；建议用 `List.copyOf` 不可变化 | 中 |
| `convert()` 集合拷贝 | 大量 `Collectors.toList()` 生成新集合（分页结果大时略有开销），影响小 | 低 |

---

## 5. 维度四：体验改善

### 5.1 文档/注释乱码与"幽灵类"引用（按文档写代码会直接编译失败）
- **乱码**：`PageSlice.java` Javadoc 出现编码损坏——`"适用。DO 。VO 转换场景"`（第 97 行）、`"判断当前页数据是否为。"`（第 111 行）。
- **幽灵类**（在 Javadoc/实现中被引用，但仓库中根本不存在，开发者照抄即报错）：
  - `IdempotentUtil`（`IdempotentOperation` Javadoc 调用 `IdempotentUtil.getCurrentKey()`）
  - `StatusTransitionAspect`（`BaseStatusEnum` Javadoc 提及）
  - `QueryBuilder`（`PageQueryFactory` Javadoc "统一使用 QueryBuilder 工厂"）
  - `TreeDepthExceededException`（`TreeBuilder` Javadoc 提及；且 `TreeBuilder` 实际会触发该异常但类不存在）

### 5.2 心智负担
三代分页对象 + 大面积 `@Deprecated(forRemoval)` + 一堆"待迁移"注释，缺乏一份"当前推荐用法 + 迁移时间线"的总览文档。

**建议**：补 `ARCHITECTURE.md`，明确每类能力的"推荐/弃用/替代"状态与版本节点。

### 5.3 契约标记接口形同摆设
`contract` 包的 `Command / DTO / VO / Query` 四个标记接口 **0 引用**，未体现任何分层约束价值。

---

## 6. 维度五：过度设计（建议精简 / 断舍离）

应在 2.0.0 做一次清理（删除前用 `jdeprscan` + 引用扫描确认 0 引用）：

1. **收敛分页 API**：`Slice` / `CursorPage` / `PageSlice` 三选一或直接统一到一套（推荐保留 `PageQuery/PageResult`）。
2. **删除 0 引用且 deprecated**：`PageQueryFactory`、`DomainEvent` 注解 + `DomainEventAspect`、`IdempotentOperation`（含引用缺失的 `IdempotentUtil`）、`Command/DTO/VO` 标记接口。
3. **删除孤儿童枚举**：`DeepPaginationRisk` / `DeepPaginationException`（无调用方；若按 3.1 落地则保留并实现闭环）。
4. **清理无消费配置**：`tree.maxDepth`、`idempotent.defaultExpireSeconds`、`maxSearchKeyLength` 等 `DomainProperties` 字段若无消费方应删除。
5. **删除死依赖**：`spring-aop` / `aspectj` / `spring-context`（仅服务于已删的事件切面）。
6. **修正 `TreeBuilder` 对 `TreeDepthExceededException` 的引用**（类不存在）。

---

## 7. 可落地的优化路线图

### P0（本周，恢复可构建）
- [ ] **修复或删除** `Slice.java` 与 `PageQueryFactory.java` 两处编译错误（见附录 A / B）。
  - 推荐：因二者均 0 引用且已 deprecated，**直接删除**更彻底，也顺带消除死代码。

### P1（本月）
- [ ] 收敛分页 API：冻结 `SliceQuery/SliceResult` 推广，或制定旧→新迁移排期（带版本号与到期日）。
- [ ] 落地深度分页风险闭环：`PageQuery.assessPaginationRisk()` + `SafeQueryInnerInterceptor` 调用。
- [ ] 处理 deprecation 债务：`DataScopeType` 等要么真迁移要么撤销标记。
- [ ] 清理 0 引用且 deprecated 的类（见第 6 节）。

### P2（下季度）
- [ ] 补单元测试：至少覆盖 `PageQuery` 分页计算、`SliceResult` 转换、`TreeBuilder`、`BaseQuery` 时间校验。
- [ ] 引入架构守护：用 **ArchUnit** 测试锁定"domain 不反向依赖 web/jdbc"等边界，防止再次腐化；CI 中跑 `jdeprscan`。
- [ ] 补 `ARCHITECTURE.md` + 推荐用法文档，修复所有幽灵类引用与乱码注释。

---

## 附录 A：`Slice.java` 编译错误修复

将三处非法 `Collections.emptyList<>()` 改为 `Collections.emptyList()`（无菱形）：

```java
// 第 157 行（convert 方法）
List<R> convertedRecords = records != null
        ? records.stream().map(converter).collect(Collectors.toList())
        : Collections.emptyList();          // ← 去掉 <>

// 第 226 行（of(List) 方法）
List<T> safeRecords = records != null ? records : Collections.emptyList();   // ← 去掉 <>

// 第 241 行（of(List, pageNum, pageSize, total) 方法）
List<T> safeRecords = records != null ? records : Collections.emptyList();   // ← 去掉 <>
```

> 若该文件确认不再需要（0 引用且 deprecated），更优解是**直接删除 `Slice.java`**，并在 2.0.0 完成对 `SliceQuery/SliceResult` 的取舍。

## 附录 B：`PageQueryFactory.java` 编译错误修复

方式一（推荐，因其 0 引用且 deprecated —— 直接删除类）：
- 删除 `PageQueryFactory.java`，并从 `DomainAutoConfiguration` 移除相关注册。

方式二（若必须保留，恢复编译）：在 `PageQuery` 中补静态工厂：
```java
// PageQuery.java 新增
public static PageQuery of(Integer pageNum, Integer pageSize) {
    return PageQuery.builder()
            .pageNum(pageNum)
            .pageSize(pageSize)
            .build();
}
```

## 附录 C：关键证据（实测命令摘要）
- `javac` 单独编译 `Slice.java` → 6 错误，确认 `emptyList<>()` 非法。
- 全仓 `import com.njydsz.common.domain.*` 精确统计 → 确认 `Slice/SliceQuery/SliceResult/StateTransitionUtil/ValidationGroups/IdempotentOperation/DeepPaginationRisk/DeepPaginationException/DomainEvent/PageQueryFactory` 等外部 0 引用。
- `grep` 确认 `PageQuery` 中无 `assessPaginationRisk` / `DeepPaginationRisk` 引用。
- `grep` 确认 `IdempotentUtil / StatusTransitionAspect / QueryBuilder / TreeDepthExceededException` 在仓库中不存在（仅有引用，无定义）。
- `DomainEventAspect` 切点 `@Around("@annotation(domainEvent)")` → 仅对 Spring Bean 生效，对 `new` 实体无效。
