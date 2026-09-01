# ydsz-common-cache 模块深度分析报告

> **重建说明（2026-09-01）**：本报告初版及二审修订版于当日 13:33 被并行会话的工作区清理误删（未提交过 git）。本版为依据当日执行记录、记忆日志与本轮实测数据重建：§3~§7 内容与实测数据逐字保留或更新；§0~§2 为依据记忆日志核心结论重建（结论与数据均经实跑核验，个别行文与初版可能有差异）。本版已提交 git 保护，防止再次丢失。

## 0. 事实基线（全部经代码核验）

- 模块规模：约 9,100 行纯 JDK 手写（41 文件），类名与 Caffeine 同构（WindowTinyLFU / FrequencySketch / StripedConcurrentCache 等）；
- 测试状态（一审时）：`src/test` 为空，0 个单元测试（截至本报告收尾已达 32 个，全绿）；
- Caffeine 3.2.4 已存在于根 pom dependencyManagement + 7 个模块 pom，但全仓 0 处 import（已声明未消费）；
- 全仓调用约 15 处：auth 8 / workflow 6 / lock 2 / system CacheConfig；
- 编译验证：`mvn -o compile` exit 0。

## 1. 五维度分析

### 1.1 架构优化

- 三层结构：`api`（Cache/CachePolicy/CacheProtectionGuard）→ `builder`（CacheBuilder/CacheType）→ `internal`（concurrent / tinylfu / decorator 三族）；
- 装饰器体系：`ExpirableCache`（读时惰性过期 + TTL jitter——**对标 Caffeine 的领先项，替换时不可丢**）、`WriteThroughCache`；
- Spring 门面：`SpringYdszCache` / `YdszCacheManager` / `YdszCacheAutoConfiguration`（注解式缓存 + per-cache 配置覆盖）；
- 可观测性：Micrometer 指标 + HealthIndicator + 自定义 CacheMetricsEndpoint；
- **核心建议（A1）**："Caffeine 内核 + 保留门面"防腐层替换，约 9,100 → 约 3,000 行——与 L1 零依赖纯度约束（`l1.module.skip`）冲突，需决策会裁决（JMH 对照数据见 §7）。

### 1.2 功能增强（对标 Caffeine 能力矩阵）

- **自研领先项**：三防组件（缓存穿透空值占位 / 击穿单飞 / 雪崩 TTL jitter）、Spring 注解门面、开箱可观测性；
- **对标缺口**：refreshAfterWrite、AsyncCache 异步 API、加载器完整语义（上述在 Caffeine 中均为原生能力，自研版为部分等价或缺失）。

### 1.3 性能提升（逐条附源码证据）

- 读路径（probation 提升）持全局写锁——高并发读退化（JMH 16 线程 readHit 实测：TINYLFU 3.7k ops/ms，随线程数不升反降，见 §7）；
- `FrequencySketch.reset()` 全表 CAS 在写锁内；
- `RemovalListener` 曾持锁同步回调（已改异步，P2 #10）；
- JMH 三档实测：自研最优实现（STRIPED）readHit 吞吐落后 Caffeine 约 1.5~7 倍（线程越多差距越大，见 §7）。

### 1.4 体验改善（开发者 + 运维）

- README 与实现不一致（能力表/版本号，已对齐，P1 #8）；
- 配置默认值双路径不对齐（实例级 vs 注解级，已统一，P1 #8）；
- 空值 TTL 两种配置方式（实例级 `nullValueExpire` + 注解级 `ydsz.cache.null-value-ttl-*`，均已落地）。

### 1.5 过度设计（按库模块标准重评）

**二审勘误（Marvin 确认的评判标准）**：公共/工具库不以仓内引用计数评判 API 价值——工具库的价值正是按需供给、收敛散落实现。

- O5（asMap）一审事实错误，**撤销**——workflow/userinfo 有真实调用；
- O3/O4（CacheKeyGenerator/CacheMetricsCollector）由"删除"转为 **API 演进管理**（README 文档化公开 API）；
- 仅 O1/O6/O7/O8 维持（依据为复杂度/正确性而非引用计数）；
- 一审误删 3 文件（StripedLock/CacheKeyGenerator/CacheMetricsCollector，约 820 行）**已恢复入库**（编译验证通过）。

### 1.6 正确性缺陷清单（P0/P1，含证据位置）

| # | 缺陷 | 状态 |
|---|---|---|
| 1 | `CacheBuilder.maximumSize(-1)` 实按 initialCapacity=16 限容（createBaseCache） | **已修复**（DEFAULT_UNBOUNDED_CAPACITY=1024 兜底） |
| 2 | `WindowTinyLFU.clear()` 不重置 windowSize/totalCount | **已修复** |
| 3 | 读路径（probation 提升）持全局写锁 | 待 A1 决策（Caffeine 内核天然解决） |
| 4 | `FrequencySketch.reset()` 全表 CAS 在写锁内 | 待 A1 决策 |
| 5 | RemovalListener 持锁回调、builder 的 listenerExecutor 死配置 | **已修复**（P2 #10 异步化） |
| 6 | CacheProtectionGuard 失败重试无界风暴 + NullValueGuard/nullKeyExpirations 双状态 map | **已修复**（失败传播对标 Caffeine：异常直达所有等待者，无递归重试） |
| 7 | **【P0·新增】StripedConcurrentCache 断链 NPE**（JMH 16 线程实跑抓获）：map（ConcurrentHashMap）与双向链表两结构更新不在同一临界区——`put` 的 `map.putIfAbsent` 在 evictLock 外发布节点，窗口内被 remove/evict 摘走后 `addToTail` 接回形成幽灵节点（指针为残留旧值），`removeFromList` 依据旧指针错写前驱/后继 → 断链 → `evictOne` 采样循环 NPE（`evictOne:472`，`Cannot read field "lastAccessNanos" because "current" is null`） | **已修复**（map 操作全部移入 evictLock 临界区，get 保持无锁；复现程序 16 NPE→0，回归测试 StripedConcurrentCacheTest 入库） |

## 2. 对标竞品差距总评

- **最大差距**：自研内核的性能与正确性落后于被复刻件（Caffeine），且长期无测试兜底——JMH 三档实测确认（§7）：Caffeine readHit 16 线程 229k ops/ms，自研最优 31k（约 7.3 倍）；混合读写约 8.2 倍；
- **真正领先项**：三防组件 / Spring 门面 / 可观测性 / TTL jitter——这些是"防腐层替换"中必须保留的门面资产；
- **结论**：建议 A1"Caffeine 内核 + 保留门面"，净删约 6,000 行内核代码，性能对齐业界基准；该路线与 L1 纯度约束冲突，需决策会裁决（§4）。

## 3. 落地路线图

### P0（本周内，2 人日）
1. **决策会**：确认 A1"Caffeine 内核 + 保留门面"路线（这是后续所有动作的前提；注意该路线与 L1 零依赖纯度约束 `l1.module.skip` 冲突，决策时需一并裁决治理规则）——**待决策**（JMH 对照数据已就绪，见 §7）；
2. ~~**修语义 bug**：`maximumSize(-1)` 路径~~ **已修复**（DEFAULT_UNBOUNDED_CAPACITY=1024 兜底，含 `clear()` 计数泄漏修复）；
3. ~~**补最小测试集**~~ **已完成**：起步 17 个用例全绿，截至收尾 32 个全绿（三防语义 7 + CacheBuilder 5 + 原子操作 6 + ExpirableCache 3 + 监听器异步 3 + TinyLFU 4 + Spring 空值 TTL 3 + StripedConcurrentCache 并发回归 1）；
4. ~~**删除死代码 O2-O5**~~ **已撤销并恢复入库**：一审的"仓内零引用"判定标准不适用于公共库（见 1.5 二审勘误），删除的 StripedLock/CacheKeyGenerator/CacheMetricsCollector 已恢复且经编译验证。

### P1（两周内）
5. 内核替换落地：`Cache` 接口保持不变，`internal/*` 换 Caffeine 适配器；ExpirableCache 降级为薄 TTL 装饰器（保留 jitter 领先项）；三防组件改挂 Caffeine `computeIfAbsent`（天然原子 + 单飞）——**待决策**（与 L1 纯度约束冲突，见 §4；JMH 证据见 §7）；
6. ~~统一单飞原语~~ **已完成（语义保真方案）**：CacheProtectionGuard/SpringYdszCache/AbstractCache（compute 系列与 getAsync）四处信号逻辑统一为同一失败语义（异常直达所有等待者、无递归重试、等待者不丢失自身操作——merge/compute 排队重试）。注：原建议"首次失败返回 null + 有界重试"经评审改为 Caffeine 式异常传播（调用方决定重试策略，避免掩盖真实故障）；未做物理合并（三处调用面不同），通过语义测试锁定不变量；
7. ~~`SpringYdszCache` 打通注解级空值 TTL（F1）~~ **已完成**：`ydsz.cache.null-value-ttl-min/max`（全局 + per-cache 覆盖），短 TTL 空值占位替代主 TTL NullValue 条目，含 3 用例验证（占位屏蔽/过期恢复/真实值优先/装配覆盖）；
8. ~~修 metadata JSON 枚举漂移、README 版本、双路径默认值对齐~~ **已完成**：metadata 先前已修；README 能力表与变更记录对齐（并发语义、nullValueExpire、注解级空值 TTL、监听器异步、null 键口径）；双路径默认值经两参 `getWithProtection` 统一为 30~60 秒；
9. ~~JMH 基线（换内核前先测当前实现，留存对照数据）~~ **已完成**：三档（1/4/16 线程）× 3 实现 × 3 场景，数据与结论见 §7；16 线程档实跑抓获 StripedConcurrentCache 断链 P0 并当场修复（§1.6 #7）。

### P2（一个月内）
10. ~~监听器真正异步化（接 CacheThreadPoolManager listenerPool）~~ **已完成**：`Cache.setListenerExecutor(Executor)` 接口 + AbstractCache/WriteThroughCache 异步派发，默认 `listenerPool` 守护线程池（CacheBuilder 自动注入），含 3 用例验证；
11. ~~`getWithProtection` 参数上移 Builder~~ **已完成**：`CacheBuilder.nullValueExpire(min, max, unit)` 实例级注入 + 两参 `getWithProtection(key, loader)` 便捷重载；
12. ~~评估 L1+L2 薄装饰器统一 auth 模块 6 处手写两级缓存（F2）~~ **评估完成，裁定不做**（详见 §6 F2 评估结论：一审"6 处手写"事实不成立——实际是 3 处已构建在 ydsz-common-cache 上的两级缓存 + 样板重复，不满足抽象门槛）；
13. ~~趁 JaCoCo 落地，将本模块覆盖率门禁纳入 CI~~ **已完成（pom 级门禁）**：仓库无 CI 配置，门禁落在模块 pom——JaCoCo `check`（BUNDLE 指令覆盖率 ≥ 35%，基线实测 37.9%）；`mvn verify` 实跑 "All coverage checks have been met" + 32/32 测试 + checkstyle 通过。

---

## 4. 风险提示

- 内核替换唯一行为风险点：`ExpirableCache` 的"读时惰性过期"语义在 Caffeine 中等价存在（expireAfterAccess/Write），TTL jitter 需保留为装饰器，不可丢；
- `StripedConcurrentCache` 的"写多读少"场景 Caffeine 同样覆盖（其写路径即分段缓冲），`CacheType.STRIPED` 枚举可保留为透传配置，避免破坏 15 处调用点的 API；
- 本报告初版分析为只读；三轮执行均按用户指令完成——第一轮 P0 修复与测试（含误删文件恢复），第二轮按优先级完成全部 P1/P2 代码项（#6~#8、#10~#11），第三轮完成 JMH 基线（#9）、覆盖率门禁（#13）并顺带修复 JMH 实跑抓获的 StripedConcurrentCache 断链 P0；**仅剩 A1 内核替换一项待明确指令**（需决策会裁决 L1 纯度约束冲突，JMH 证据已就绪）。

---

## 5. 执行记录（2026-09-01）

| 动作 | 结果 |
|---|---|
| CacheBuilder 兜底容量修复（P0） | `maximumSize(-1)` → 1024 兜底，防 16 条目陷阱；已入库 |
| WindowTinyLFU `clear()` 计数泄漏修复 | `windowSize`/`totalCount` 重置；已入库 |
| metadata JSON 枚举漂移 / CacheType javadoc 修正 | 已入库 |
| 新增测试 4 类 17 用例 | surefire 实跑 `Tests run: 17, Failures: 0, Errors: 0`；已入库 |
| 一审误删 3 文件（O2-O4） | **已恢复入库**（git 提交含 +128/+120/+229 行恢复），编译验证通过 |
| **第二轮：并发语义加固（P1 #6）** | 失败传播（异常直达所有等待者，含 guard 等待路径 CompletionException 解包还原原始异常）；空值占位真实值优先；putIfAbsent/computeIfAbsent/compute/merge 原子化（per-key 单飞 + merge/compute 等待者排队重试，16 线程 merge 计数==16 实测验证）；getAsync 单飞。测试 +9 用例 |
| **第二轮：注解级空值 TTL（P1 #7，F1）** | `YdszCacheProperties` 新增 null-value-ttl-min/max（全局 + per-cache CacheConfig 覆盖）→ `YdszCacheManager.getCache()` 注入 → `SpringYdszCache` 短 TTL 占位（CacheProtectionGuard 公共 API 复用）；测试 +3 用例 |
| **第二轮：监听器异步化（P2 #10）** | `setListenerExecutor` 接口 + 默认 listenerPool 守护线程异步派发；测试 +3 用例 |
| **第二轮：空值 TTL 上移 Builder（P2 #11）** | `nullValueExpire(min, max, unit)` + 两参 `getWithProtection`；null 键统计口径统一；测试 +2 用例 |
| **第二轮：README 对齐（P1 #8）** | 并发语义说明、两种空值 TTL 配置、null-value-ttl 配置项、监听器异步说明、变更记录（1.0.0 / 2026-09-01 条目） |
| **第二轮全量回归** | `mvn -o test` 实跑：`Tests run: 31, Failures: 0, Errors: 0, Skipped: 0`，BUILD SUCCESS |
| **第三轮：StripedConcurrentCache 断链 P0（JMH 抓获）** | 根因：map 与链表双结构更新非原子（put 的 putIfAbsent 在 evictLock 外发布节点 → 幽灵节点 → 断链 → evictOne NPE）。修复：Segment.put/remove 的 map 操作全部移入 evictLock 临界区（get 保持无锁）。验证：插桩复现程序抓到断链现场（k=52 节点 prev/next=NULL 但前驱仍指向它）→ 修复后 16 线程 × 20 秒 errors=0；JMH 16 线程档修复前 NPE、修复后 9 项基准 0 异常 |
| **第三轮：JMH 基线（P1 #9）** | 三档 × 3 实现 × 3 场景实测，数据见 §7；test scope 引 Caffeine/JMH 不污染 L1 主代码纯度 |
| **第三轮：覆盖率门禁（P2 #13）** | 模块 pom 启用 JaCoCo（继承根 pom prepare-agent + report）+ `check` 门禁（BUNDLE 指令覆盖 ≥ 35%，基线 37.9% / 分支 31.7% / 行 37.4%）；`mvn verify` 实跑通过 |
| **第三轮全量回归** | `mvn -o verify` 实跑：**`Tests run: 32, Failures: 0, Errors: 0, Skipped: 0`，"All coverage checks have been met"，checkstyle 通过，BUILD SUCCESS**（8 测试类，含新增 StripedConcurrentCacheTest 16 线程 × 5 秒并发回归） |
| **第三轮：报告重建** | 初版报告被并行会话工作区清理误删（未提交过 git），本版依据执行记录/记忆日志/当日实测数据重建，并提交 git 保护 |
| Caffeine 内核替换（A1） | **未执行**，待决策（与 L1 纯度约束 `l1.module.skip` 冲突，需一并裁决；JMH 对照数据已就绪，见 §7） |

*报告依据：ydsz-common-cache @ 2026-09-01 源码（41 文件逐行核验）、全仓 grep 扫描、mvn 实跑编译与 surefire 测试、JMH 1.37 实测。二审勘误依据用户方法论纠正：公共库不以仓内引用计数评判 API 价值。*

---

## 6. F2 评估结论（2026-09-01，P2 #12 裁定：不做薄装饰器统一）

- **一审前提不成立**："auth 模块 6 处手写两级缓存"经核验实为——3 处已构建在 ydsz-common-cache 之上的 L1+L2 两级缓存（RedisRolePermissionLoader / RedisRoleColumnPermissionResolver / RedisRoleDataPermissionResolver，各含近似 `buildCache()` 样板）+ ConcurrentHashMap 的用途为有界反射元数据缓存（非业务缓存）；
- **裁定**：不建 L1+L2 薄装饰器统一抽象——3 处样板重复不满足抽象门槛（抽象收益 < 维护成本），强行统一会引入间接层；
- **附带发现**（可低成本修复）：3 处 `buildCache()` 均未设 maximumSize（无界）；javadoc 注释写"Caffeine"但实际构建的是 YdszCache——文档与实现不符。

---

## 7. JMH 基线数据（2026-09-01，P1 #9）

**方法论**：JMH 1.37，Corretto JDK 21.0.8，fork 1 / warmup 3×1s / measurement 5×1s；容量 10,000 预填充，key 均匀随机（固定种子 42，序列长 2^20），每线程独立游标（Scope.Thread）避免共享计数器串行化；Caffeine 3.2.4 经 test scope 引入（L1 主代码纯度不变）。基准源码：`src/test/java/.../benchmark/CacheThroughputBenchmark.java`。

**吞吐（ops/ms，±为 95% 置信误差）**：

| 场景 | 线程 | TINYLFU | STRIPED | CAFFEINE |
|---|---|---|---|---|
| readHit（100% 命中读） | 1 | 19,396 ±7,217 | 8,118 ±2,476 | 24,625 ±6,802 |
| readHit | 4 | 11,211 ±1,734 | 16,540 ±4,380 | 51,734 ±14,348 |
| readHit | 16 | 3,709 ±10,917 | 31,301 ±24,901 | **229,341 ±151,874** |
| mixedWriteRead（~6% 写） | 1 | 16,012 ±7,911 | 8,382 ±742 | 23,364 ±5,392 |
| mixedWriteRead | 4 | 11,762 ±3,252 | 22,518 ±9,548 | 105,711 ±26,841 |
| mixedWriteRead | 16 | 6,680 ±11,707 | 11,160 ±6,367 | **91,516 ±102,702** |
| computeIfAbsentMiss（未命中加载） | 1 | 1,681 ±450 | 1,566 ±337 | 4,866 ±1,706 |
| computeIfAbsentMiss | 4 | 1,265 ±210 | 3,019 ±518 | 5,479 ±1,281 |
| computeIfAbsentMiss | 16 | 1,144 ±110 | 2,510 ±1,196 | 1,820 ±2,714 |

**结论（A1 决策证据）**：

1. **Caffeine 读路径全面且规模化领先**：readHit 1→16 线程 24.6k → 229k（9.3 倍扩展）；自研最优 STRIPED 8.1k → 31.3k（3.9 倍），16 线程档差距约 7.3 倍；
2. **TINYLFU 读吞吐随线程数不升反降**（19.4k → 3.7k）——§1.6 #3"读路径持全局写锁"的实测印证；
3. **STRIPED 分段有效**（8.1k → 31.3k 正向扩展），但天花板约为 Caffeine 的 1/7；
4. **未命中加载路径（computeIfAbsentMiss）双方接近**（16 线程 Caffeine 1.8k vs STRIPED 2.5k，误差棒重叠）——自研三防单飞语义在该路径有竞争力，替换时行为风险低；
5. **16 线程档误差棒偏宽**：测量窗口与本机并行会话活动重叠，数据作方向性基线（与 1/4 线程趋势一致），非实验室级数字；
6. **16 线程修复前后对照**：修复前 STRIPED 实跑触发断链 NPE（§1.6 #7），修复后 9 项基准 0 异常——P0 修复经基准负载实跑验证。

---

*报告版本：重建版 v3（2026-09-01）。历史版本：初版（一审）→ 二审修订版（均因未提交 git 被并行会话清理误删）。*
