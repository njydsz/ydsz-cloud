# ydsz-common-json 竞品对标分析与优化建议报告（终版）

> 审计日期：2026-09-01 | 功能版本：1.0.0 | 父 POM：1.0.0-SNAPSHOT
> 审计方法：全量源码静态走读（25,620 行 / 80 个 Java 文件，三路并行深读）+ `mvn compile` 实跑 + 最小化复现程序实跑验证（JDK 21，复现程序存档于 `.workbuddy/verify-json/`）
> 对标基准：Jackson 2.x（databind + annotations）、Fastjson2、Gson；规范基准：云顶编码规范（§33.7 L1 工具模块评判标准）+ 互联网大厂基础库研发规范（测试门禁 / JMH 基准 / fuzzing / RFC 合规）
> **战略约束（Marvin 确认）**：公司内网项目，云顶编码规范不允许引入竞品 JSON 库——自研路线唯一，竞品仅作对标参照不作替代选项。

---

## 一、结论摘要

| 维度 | 评分 | 一句话结论 |
|---|---|---|
| 架构设计 | 6/10 | 分层清晰（Facade→Mapper→Provider→Reader/Writer），但双序列化路径 + 四套词法实现造成行为分叉，分叉已兑现为多个生产级缺陷（本轮已修复主要项） |
| 功能完整性 | 6.5/10 | 注解体系覆盖面持续收敛（access/@JsonView 语义/@JsonTypeInfo 序列化已补齐），树模型补齐 at/findValue/fields 基线，JsonPatch 完成 RFC 合规 |
| 正确性 | 7/10 | 5 个实跑复现 P0 已全部修复（重入/容量/0 值吞掉/access/NaN），List 多态反序列化已修；Map→Bean 直转等残留项见路线图 |
| 工程化 | 5/10 | 测试从 0 到 39 项全绿、checkstyle 0 违规、测试目录已解除 gitignore 屏蔽入库；JMH 基准与 fuzzing 仍未建立（最大短板） |
| 能力储备 | 7/10 | 按修正后评判标准（§33.7）：零引用设施属合理储备；储备设施的自身正确性缺陷（JMX/warmup/JsonPatch）已修复 |

**总评：5.5/10（审计起点 4/10）。** P0 止血 + P1 合规 + 能力深化三轮完成后，核心序列化/反序列化路径的正确性已建立 39 项测试背书。当前最大风险已从"静默数据损坏"转移为"性能声明无基准背书"——JMH 套件是自研路线下一阶段的第一优先级。

---

## 二、审计发现与修复状态

### 2.1 实跑复现的 P0（全部已修复，`mvn test` 背书）

| # | 缺陷 | 根因 | 修复 |
|---|---|---|---|
| P0-1/2 | `toJson(List/Map<无注解Bean>)` 输出损坏 JSON（丢 `[`/键名），HTTP 响应路径同中招 | ThreadLocal `fastWriterPool` 重入：嵌套序列化对同一 writer `reset()` | `SerializationContext.poolDepth` 五入口进出计数，嵌套调用使用独立缓冲 |
| P0-3 | Bean 含 8KB String 字段抛 `Range out of bounds` | BeanSerializer 仅入口粗估容量，无逐字段保障 | String 按实际长度（转义 6 倍展开）ensureCapacity + 本地 buf 引用刷新；`writeStringDirectNoCheck` 自带容量检查 |
| P0-4 | 无注解 Bean 的 `Integer=0`/`Boolean=false` 被静默吞掉 | 五处 `intVal != 0` 判断混淆 null 与 0 | 改 null 判断，包装类型 0 值正常输出，与注解路径语义一致 |
| P0-5 | `Map` 中 `Double.NaN` 静默变 null，多路径策略不一 | writeFloat/ValueFormatter 无 NaN 检查 | 全路径统一输出 null，消除非法字面量 |
| P0-6 | `@JsonProperty.access` 声明支持实则零实现（WRITE_ONLY 密码泄漏） | 注解定义存在、全模块零消费点 | `FieldMeta.serializable` 三点接入 + `hasFieldAnnotations` 判定同步；修正枚举 javadoc 方向写反 |

### 2.2 P1 修复（全部已完成）

| 项 | 修复内容 |
|---|---|
| JsonPatch RFC 6902 合规（5 处） | test 数值按值比较（§4.6）；test 路径缺失必须失败；ADD 不再自动创建中间节点；整文档路径 `""` 支持 ADD/REPLACE/TEST；JSON Pointer `/` 修正为空键成员（RFC 6901） |
| @JsonView 语义对齐 | Jackson DEFAULT_VIEW_INCLUSION 默认包含；`hasFieldAnnotations` 计入 @JsonView 防快速路径绕过。已知边界：仅字段级注解，方法级 P2 待补 |
| @JsonTypeInfo 序列化输出 | `resolveTypeId` 沿类层级解析 + ValueWriter 输出 As.PROPERTY 类型属性——多态 round-trip 闭环 |
| List 多态反序列化 | `deserializeBeanListFast` 逐元素 `resolveType`——原先抽象基类列表必然 ClassCastException |
| JMX 配置观测 | `getConfigDetails` 改 `getInstance()`（原 `copyOf(null)` 永远读到默认配置） |
| TYPE_CODE_CACHE 上界 | 无界 CHM → BoundedLruCache(1024) |
| 深度限制统一 | BeanReader 改走 `resolveMaxDepth()`（多 Mapper 自定义深度此前失效） |
| warmup | 补 `@PatchMapping` 扫描 |
| 监听器生命周期 | 配置变更监听器 `@PreDestroy` 注销（原容器热重启累积泄漏） |
| StringInterner 重写 | 全局 synchronized → CHM 无锁；javadoc 虚标（分段锁/LRU）修正 |
| 树模型基线 API | `at()`/`findValue()`/`findValues()`/`fields()` |
| NaN 统一 / 文档纠偏 | 见 P0-5；README 撤回 O(1)/零拷贝/239 测试虚标，monitoring-enabled 标注预留 |

### 2.3 评判标准修正记录（2026-09-01）

初版报告曾以"业务零引用"作为 JsonPatch/warmup/JMX/SPI 设施过度设计的判据，**该判据不成立**（Marvin 指正，已固化至《云顶编码规范》§33.7）：工具/公共库是能力提供方，API 覆盖面即价值构成；引用统计仅用于刻画消费成熟度与替换影响面；零引用设施的正确推论是"测试义务加重"。修正后的有效批评维度：平台内重复、可替代性（须 ADR）、负债状态（无测试储备=裸奔资产）。

---

## 三、剩余路线图（自研路线）

### P1.5（下一轮）

1. **JMH 基准套件**（最优先）：与竞品同用例对照，所有性能 PR 附基准数据——自研库可信度的核心支柱；
2. **failOnUnknownProperties 配置链**：JsonConfig → JsonRuntimeConfig → JsonProperties → BeanReader 全链贯通（opt-in，默认保持容错）；
3. **方法级注解支持**：@JsonView/@JsonProperty/@JsonFormat 的 getter/setter 方法级识别（对齐 Jackson 注解放置习惯）。

### P2（架构收敛）

1. **Map→Bean 直转引擎**：消除 `deserializeBeanListFast` 的 serialize→deserialize 往返（当前 2 倍开销），须与 BeanReader 复用同一套字段语义（日期/别名/多态/格式），不引入第五份转换实现；
2. **统一词法层**：四套词法实现（JSONReader/BeanReader/JsonParserUtil/JsonParser）收敛到单一读写底座；
3. **合并双序列化路径**：BeanSerializer 与 writeBeanNoAnnotationOptimized 收敛为单一实现；
4. FieldMeta 拆分（元数据/访问/格式化三职责）与死代码清理；
5. **fuzzing**：随机/畸形 JSON 对解析器做健壮性轰炸。

### 治理

- 多代理并行工作规范：本仓库存在多会话并行提交，本轮曾发生"误删测试 + gitignore 回退"冲突（已恢复）。建议：测试目录与 pom 测试依赖变更需显式协调，避免会话间互相覆盖。

---

## 四、验证记录（终）

- `mvn -o -pl ydsz-common/ydsz-common-json test checkstyle:check`：**Tests run: 39, Failures: 0, Errors: 0**（YdszJsonRegressionTest 20 + JsonPatchComplianceTest 10 + CapabilityEnhancementTest 9）；**0 Checkstyle violations**；BUILD SUCCESS
- 复现程序实跑：`.workbuddy/verify-json/`（VerifyJson/VerifyJson2/VerifyAccess，P0 修复前后对照输出存档）
- ydsz-common 全量编译 BUILD SUCCESS（下游无破坏）

*报告重建说明：本文件曾被并行会话从磁盘误删（未跟踪状态），终版由会话上下文重建，包含全部三轮修正的累积结论。*
*报告生成：WorkBuddy | 2026-09-01 终版*
