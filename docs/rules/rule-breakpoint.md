<!--
  ===========================================================================
  文件名: rule-breakpoint.md
  路径:   docs/rules/rule-breakpoint.md
  作用:   LiteRule 1.4.0+ 规则断点调试（P2-3）的设计目标、SPI 接口、引擎集成时机、
          使用示例与限制说明
  关联:   ydsz-pmis-literule/.../core/BreakpointHook.java  /  DefaultBreakpointHook.java
          /  DefaultRuleEngine.java  /  rule-trace-replay.md  /  rule-canary.md
  ===========================================================================
-->

# 规则断点调试

> 适用于 1.4.0 起。LiteRule 通过 `BreakpointHook` SPI 在每条规则评估前后注入回调点，支撑断点命中、单步跳过、上下文快照、SUSPEND 挂起等能力，为运营与开发提供 **IDE 风格的规则调试体验**：在不重启引擎、不影响其他规则的前提下，对指定规则"打断点、看上下文、单步走"。

## 1. 设计目标

- **IDE 风格调试体验**：类比 IDE 的断点/单步/继续，规则评估可在指定 `ruleCode` 前后暂停，便于查看 `facts / result / elapsedMs / exception` 等运行时状态
- **按需启用、零默认开销**：默认不装配任何 `BreakpointHook`；装配后引擎先调用 `hasBreakpoint(ruleCode)` 判断，**仅命中断点的规则才触发回调**，未命中规则的评估路径无额外开销
- **SPI 解耦**：`BreakpointHook` 是引擎层 SPI，由应用层（如 `literule-debug` 模块或运维工具）提供实现；引擎不依赖具体实现，便于在不同环境（本地调试 / 线上审计）切换
- **异常隔离**：Hook 抛出的任何异常均被引擎吞掉并记录 `debug` 日志，**不会影响规则评估流程与结果**
- **facts 快照独立**：传给 Hook 的 `facts` 是 `RuleContext.facts` 的 `LinkedHashMap` 副本，Hook 对快照的修改不会回写到实际评估上下文，避免调试行为污染线上数据
- **与既有能力正交**：断点 Hook 与 Trace 记录、熔断、超时、灰度路由独立工作，可在同一评估循环中并存

## 2. 核心 SPI：BreakpointHook

接口定义在 `com.njydsz.pmis.literule.core.BreakpointHook`：

```java
public interface BreakpointHook {

    /** 规则评估前回调；返回动作决定是否继续/挂起/跳过 */
    default BreakpointAction onBeforeEvaluate(BreakpointContext context) {
        return BreakpointAction.CONTINUE;
    }

    /** 规则评估后回调；可查看 result/elapsedMs/exception */
    default void onAfterEvaluate(BreakpointContext context) {
        // 默认空实现
    }

    /** 检查指定规则是否启用断点；引擎据此决定是否触发 hook，避免对全部规则产生开销 */
    default boolean hasBreakpoint(String ruleCode) {
        return false;
    }
}
```

### 2.1 BreakpointAction 枚举

| 动作 | 含义 | 引擎行为 |
|------|------|---------|
| `CONTINUE` | 继续评估当前规则（默认） | 正常调用 `Rule.evaluate(context)` |
| `SUSPEND` | 挂起当前规则评估 | 引擎层不感知阻塞；**实际阻塞由 Hook 实现内部完成**（如阻塞等待外部唤醒信号），引擎继续向下执行 |
| `STEP_OVER` | 单步跳过当前规则 | 不评估当前规则，直接进入下一条规则；不写入 `triggered` 列表，不触发 `onAfterEvaluate` |

> `SUSPEND` 与 `STEP_OVER` 的区别：`STEP_OVER` 是引擎层"跳过"，不评估；`SUSPEND` 是"由 Hook 自行决定何时返回"，典型实现是在 `onBeforeEvaluate` 中阻塞等待用户"继续"指令后再返回 `CONTINUE`，从而实现真正的"挂起-唤醒"调试语义。

### 2.2 BreakpointContext 上下文

```java
public class BreakpointContext {
    private final String phase;          // "BEFORE" / "AFTER"
    private final String traceId;        // 评估批次追踪 ID
    private final String ruleCode;       // 规则编码
    private final String ruleName;       // 规则名称
    private final String scenario;       // 业务场景
    private final Map<String, Object> facts;  // facts 副本（LinkedHashMap）
    private RuleResult result;           // BEFORE 时为 null；AFTER 时已填充
    private long elapsedMs;              // AFTER 时已填充
    private Throwable exception;          // 评估异常（无异常为 null）
    // getter / setter 省略
}
```

| 字段 | BEFORE 阶段 | AFTER 阶段 |
|------|-----------|-----------|
| `phase` | `"BEFORE"` | `"AFTER"` |
| `ruleCode` / `ruleName` / `scenario` / `traceId` | 已填充 | 已填充 |
| `facts` | `RuleContext.facts` 的 `LinkedHashMap` 副本 | 同一副本引用 |
| `result` | `null` | 已填充（可能为未触发结果） |
| `elapsedMs` | `0` | 规则评估耗时（毫秒） |
| `exception` | `null` | 评估异常时填充，否则 `null` |

## 3. 默认实现：DefaultBreakpointHook

`com.njydsz.pmis.literule.core.DefaultBreakpointHook` 提供**基于 `ConcurrentHashMap` 的断点集合管理**，覆盖"增删查 + 总开关"的最小能力集：

```java
public class DefaultBreakpointHook implements BreakpointHook {
    private final Set<String> breakpoints = ConcurrentHashMap.newKeySet();
    private volatile boolean enabled = true;

    public void addBreakpoint(String ruleCode);     // null / blank 静默拒绝
    public void removeBreakpoint(String ruleCode);
    public void clearBreakpoints();
    public Set<String> getBreakpoints();            // 返回不可修改视图
    public void setEnabled(boolean enabled);        // 总开关：关闭后 hasBreakpoint 恒为 false
    public boolean isEnabled();

    @Override
    public boolean hasBreakpoint(String ruleCode) {
        if (!enabled || ruleCode == null) return false;
        return breakpoints.contains(ruleCode);
    }
}
```

特性：

- **线程安全**：基于 `ConcurrentHashMap.newKeySet()`，支持多线程并发增删断点（如运维线程加断点、评估线程读断点）
- **null 安全**：`addBreakpoint(null/blank)` 静默拒绝不入库；`hasBreakpoint(null)` 返回 `false`；`removeBreakpoint(null)` 不抛异常
- **只读视图**：`getBreakpoints()` 返回 `Collections.unmodifiableSet` 包装，任何 `add/remove` 调用抛 `UnsupportedOperationException`，避免外部破坏内部状态
- **总开关**：`setEnabled(false)` 后即使集合非空，`hasBreakpoint` 也恒返回 `false`，便于一键关闭全局限流调试

## 4. 引擎集成：DefaultRuleEngine

`DefaultRuleEngine.evaluate(RuleContext)` 在评估循环中按以下时机调用 Hook：

### 4.1 调用时序

```text
对每条规则 rule（按优先级升序）：
  ┌─────────────────────────────────────────────────┐
  │ 1. 场景过滤 shouldEvaluate(rule, scenario)       │
  │ 2. 熔断检查 circuitBreaker.allowEvaluate(...)    │── 否 ── 跳过该规则
  └──────────────────────┬──────────────────────────┘
                         │ 通过
                         ▼
  ┌─────────────────────────────────────────────────┐
  │ 3. bpHook = this.breakpointHook                 │
  │    hasBreakpoint = bpHook != null &&            │
  │                    bpHook.hasBreakpoint(code)   │
  └──────────────────────┬──────────────────────────┘
                         │ hasBreakpoint == true
                         ▼
  ┌─────────────────────────────────────────────────┐
  │ 4. bpFactsSnapshot = new LinkedHashMap<>(facts)│  ← facts 副本
  │    beforeCtx = new BreakpointContext(           │
  │        "BEFORE", traceId, code, name,            │
  │        scenario, bpFactsSnapshot)               │
  │    action = bpHook.onBeforeEvaluate(beforeCtx)  │  ← try/catch 吞异常
  └──────────────────────┬──────────────────────────┘
                         │
              ┌──────────┴──────────┐
              │ action == STEP_OVER?│
              └──────────┬──────────┘
                  是 ────┴──── 否
                  │            │
                  ▼            ▼
            continue     5. start = System.nanoTime()
            跳过该规则   评估 rule.evaluate(context)
                         （可能经 timeoutExecutor / canaryRouter）
                                  │
                                  ▼
                         6. elapsed = (nanoTime - start) / 1_000_000
                            record(...)  写统计
                                  │
                                  ▼
                         ┌─────────────────────────────────────────┐
                         │ 7. afterCtx = new BreakpointContext(    │
                         │       "AFTER", ..., bpFactsSnapshot)    │
                         │    afterCtx.setResult(result)           │
                         │    afterCtx.setElapsedMs(elapsed)        │
                         │    if (caughtException != null)         │
                         │        afterCtx.setException(...)        │
                         │    bpHook.onAfterEvaluate(afterCtx)      │ ← try/catch 吞异常
                         └─────────────────────────────────────────┘
                                  │
                                  ▼
                         8. 熔断记录 / 指标记录 / Trace 记录
                         9. 若 isTriggered → 加入 triggered 列表
```

### 4.2 关键设计点

1. **Hook 调用位置**：`onBeforeEvaluate` 在熔断检查之后、计时开始之前；`onAfterEvaluate` 在统计记录之后、Trace 记录之前
2. **facts 快照**：BEFORE 阶段构造 `new LinkedHashMap<>(context.getFacts())`，BEFORE 与 AFTER 共享同一快照引用；**修改快照不会回写 `context.facts`**（当前版本不支持 facts 注入，见 [§7 限制](#7-限制与后续演进)）
3. **异常吞掉**：BEFORE/AFTER 的 Hook 调用均包裹在 `try/catch (Exception)` 中，异常仅记录 `debug` 日志，不传播到评估循环
4. **STEP_OVER 语义**：返回 `STEP_OVER` 后引擎直接 `continue`，**不评估当前规则、不触发 `onAfterEvaluate`、不计入统计**
5. **SUSPEND 语义**：引擎层不阻塞，**实际阻塞由 Hook 实现内部完成**（典型做法：在 `onBeforeEvaluate` 中 `lock.await()` 等待外部唤醒后再返回 `CONTINUE`）

### 4.3 自动装配

`LiteRuleAutoConfiguration` 通过 `ObjectProvider<BreakpointHook>` 可选注入：

```java
@Bean
public RuleEngine ruleEngine(...,
                              ObjectProvider<BreakpointHook> breakpointHookProvider,
                              ApplicationContext applicationContext) {
    DefaultRuleEngine engine = new DefaultRuleEngine();
    BreakpointHook bpHook = breakpointHookProvider.getIfAvailable();
    if (bpHook != null) {
        engine.setBreakpointHook(bpHook);
    }
    // ...
}
```

- 应用层未提供 `BreakpointHook` Bean 时，`breakpointHookProvider.getIfAvailable()` 返回 `null`，引擎不启用断点调试
- 应用层提供任意 `BreakpointHook` 实现（如 `DefaultBreakpointHook` 或自定义阻塞式 Hook）即可生效

## 5. 性能影响

断点调试对评估循环的性能影响极小，原因：

1. **默认不装配**：未注入 `BreakpointHook` Bean 时，`breakpointHook` 字段为 `null`，评估循环仅多一次 `bpHook != null` 的 volatile 读
2. **按需触发**：装配 Hook 后，每条规则先调用 `hasBreakpoint(ruleCode)`；`DefaultBreakpointHook` 的实现是一次 `ConcurrentHashMap.contains` 查询（O(1)），未命中断点的规则**不构造 `BreakpointContext`、不拷贝 facts、不调用 `onBeforeEvaluate/onAfterEvaluate`**
3. **仅在命中时拷贝 facts**：只有 `hasBreakpoint == true` 的规则才会执行 `new LinkedHashMap<>(context.getFacts())`，避免对全部规则产生 Map 拷贝开销
4. **建议生产环境谨慎使用**：在线调试场景下，建议仅对少量目标规则设置断点，并在调试结束后 `clearBreakpoints()` 或 `setEnabled(false)`

| 场景 | 每条规则额外开销 |
|------|----------------|
| 未注入 Hook | 1 次 volatile 读 |
| 注入 Hook 但未设断点 | 1 次 volatile 读 + 1 次 `ConcurrentHashMap.contains` |
| 注入 Hook 且命中断点 | 上述 + 1 次 facts 拷贝 + 2 次 Hook 调用（含 `BreakpointContext` 构造） |

## 6. 使用示例

### 6.1 编程式：DefaultBreakpointHook + addBreakpoint

最简单的用法：注入 `DefaultBreakpointHook`，对目标规则加断点，评估时观察 Hook 收到的上下文。

```java
@Service
public class RuleDebugService {

    @Autowired
    private RuleEngine ruleEngine;

    /** 通过 ObjectProvider 暴露 DefaultBreakpointHook 供运维操作 */
    @Bean
    public DefaultBreakpointHook breakpointHook() {
        return new DefaultBreakpointHook();
    }

    public void debugRule(String ruleCode, Map<String, Object> facts) {
        DefaultBreakpointHook hook = (DefaultBreakpointHook) ruleEngine.getBreakpointHook();
        if (hook == null) {
            throw new IllegalStateException("未装配 BreakpointHook");
        }

        // 1. 对目标规则加断点
        hook.addBreakpoint(ruleCode);

        // 2. 评估，引擎会在 ruleCode 前后触发 onBeforeEvaluate/onAfterEvaluate
        //    （此处未自定义 Hook，默认 DefaultBreakpointHook 的回调为空实现，
        //     仅 hasBreakpoint 生效；如需查看上下文，见 6.2 自定义 Hook）
        RuleContext ctx = RuleContext.of(facts, "DEBUG", "MANUAL", UUID.randomUUID().toString());
        List<RuleResult> results = ruleEngine.evaluate(ctx);

        // 3. 调试结束，移除断点
        hook.removeBreakpoint(ruleCode);
    }
}
```

### 6.2 自定义 Hook：实现 BreakpointHook 接口

实现自定义 Hook，在 `onBeforeEvaluate` 中记录断点命中事件、阻塞等待外部"继续"信号，实现真正的挂起-唤醒调试：

```java
public class InteractiveDebugHook implements BreakpointHook {

    private final Set<String> breakpoints = ConcurrentHashMap.newKeySet();
    private final Map<String, CountDownLatch> suspendLatches = new ConcurrentHashMap<>();

    /** 运维调用：唤醒被挂起的规则 */
    public void resume(String ruleCode) {
        CountDownLatch latch = suspendLatches.get(ruleCode);
        if (latch != null) {
            latch.countDown();
        }
    }

    @Override
    public boolean hasBreakpoint(String ruleCode) {
        return breakpoints.contains(ruleCode);
    }

    @Override
    public BreakpointAction onBeforeEvaluate(BreakpointContext ctx) {
        // 1. 推送断点命中事件给前端调试器（含 facts 快照）
        debuggerClient.notifyBreakpointHit(ctx);

        // 2. 阻塞等待用户"继续/单步"指令（SUSPEND 语义由 Hook 内部实现）
        CountDownLatch latch = new CountDownLatch(1);
        suspendLatches.put(ctx.getRuleCode(), latch);
        try {
            latch.await(30, TimeUnit.SECONDS);  // 超时自动放行，避免死锁
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            suspendLatches.remove(ctx.getRuleCode());
        }
        return BreakpointAction.CONTINUE;
    }

    @Override
    public void onAfterEvaluate(BreakpointContext ctx) {
        // 推送评估结果（含 result/elapsedMs/exception）
        debuggerClient.notifyBreakpointResult(ctx);
    }
}
```

> 注意：上述阻塞式 Hook 会让评估线程在 `onBeforeEvaluate` 中等待，**仅适用于在线调试场景**。生产评估应避免阻塞，改用 `onAfterEvaluate` 做异步快照即可。

## 7. 限制与后续演进

### 7.1 当前限制

1. **不支持 facts 注入**：`BreakpointContext.facts` 虽是可变的 `LinkedHashMap` 副本，但**当前版本不将 Hook 对快照的修改回写到 `RuleContext.facts`**，规则评估仍读取原始 facts。即"快照只读视图"，无法用于在线篡改测试数据
2. **SUSPEND 需 Hook 自行实现阻塞**：引擎层不感知 `SUSPEND` 语义，**实际阻塞由 Hook 实现内部完成**（如 `CountDownLatch.await`）；若 Hook 直接返回 `SUSPEND` 而不阻塞，引擎仍会继续评估当前规则（与 `CONTINUE` 等价）
3. **facts 快照在 BEFORE/AFTER 间共享引用**：BEFORE 与 AFTER 阶段拿到的是同一份 `bpFactsSnapshot`，BEFORE 阶段对快照的修改在 AFTER 阶段可见（但因不回写，对规则评估不可见）
4. **不跨规则共享断点状态**：每条规则的断点 Hook 调用相互独立，无法直接在 Hook 内获取"上一条规则的评估结果"（如需关联，需在 Hook 实现中自行维护跨规则状态）
5. **不支持条件断点**：当前仅按 `ruleCode` 命中，不支持"当 `facts.amount > 1000` 时才中断"这类条件断点（可在 Hook 实现的 `onBeforeEvaluate` 中自行判断 `ctx.getFacts()` 后决定是否阻塞）

### 7.2 后续演进路径

- **facts 注入回写**：在 `onBeforeEvaluate` 返回后，将 `bpFactsSnapshot` 回写到 `RuleContext`（需 `RuleContext` 支持可变 facts 或引入调试专用上下文），支撑在线篡改测试数据
- **条件断点**：扩展 `BreakpointHook` 接口，支持 `shouldBreak(BreakpointContext)` 谓词，仅当 facts 满足条件时才中断
- **远程调试协议**：基于 JDI（Java Debug Interface）或自定义 WebSocket 协议，将断点命中事件推送到远程 IDE / 运维控制台，实现跨进程规则调试
- **断点命中统计**：在 `DefaultBreakpointHook` 中累计每条规则的断点命中次数、累计挂起时长，便于审计调试行为
- **与 Trace 回放联动**：断点命中时自动关联当前 `traceId`，支持"在断点处一键触发 trace 回放"，对比历史评估结果

## 8. 典型应用场景

| 场景 | Hook 实现要点 | 价值 |
|------|-------------|------|
| **在线调试器** | `onBeforeEvaluate` 阻塞等待用户"继续/单步/查看变量"指令，推送 `facts` 快照到前端 | IDE 风格的规则调试，无需重启引擎、不影响其他规则 |
| **审计快照** | `onAfterEvaluate` 异步持久化 `BreakpointContext`（含 `result/elapsedMs/exception/facts`）到审计表 | 线上问题复盘，定位"当时该规则为何触发/未触发" |
| **动态插桩** | 仅对目标 `ruleCode` 调用 `addBreakpoint`，调试结束 `clearBreakpoints` | 按需启用，避免对全部规则产生性能开销 |
| **回归验证** | `onAfterEvaluate` 收集结果，对比预期输出 | 规则变更后对历史 facts 重新评估，断言触发集合符合预期 |

## 9. 单元测试

- 测试类：[BreakpointHookTest](file:///d:/Code/ydsz/ydsz-pmis/ydsz-pmis-backend/ydsz-pmis-literule/src/test/java/com/njydsz/pmis/literule/core/BreakpointHookTest.java)
- 覆盖场景：
  1. `addBreakpoint` 后 `hasBreakpoint` 返回 `true`
  2. 未添加的规则 `hasBreakpoint` 返回 `false`
  3. `removeBreakpoint` 后断点失效
  4. `clearBreakpoints` 后全部断点失效
  5. `disabled` 时 `hasBreakpoint` 恒返回 `false`
  6. `null` / `blank` ruleCode 安全处理，不抛异常且不入库
  7. `getBreakpoints` 返回不可修改视图
  8. 未设置 `breakpointHook` 时规则正常评估
  9. Hook 已注入但未设置断点时不触发回调
  10. 设置断点后 `onBeforeEvaluate` 被调用且收到 BEFORE 上下文
  11. `onAfterEvaluate` 收到已填充的 `result` 与 `elapsedMs`
  12. `STEP_OVER` 动作跳过规则评估，结果列表不包含该规则
  13. Hook 抛出异常被引擎吞掉，不影响规则评估结果
  14. Hook 修改 facts 快照不影响实际 `context.facts`
