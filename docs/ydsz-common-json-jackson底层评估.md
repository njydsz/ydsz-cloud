# ydsz-common-json Jackson 底层引擎替换评估（P2-3 技术预研）

> **评估日期**: 2026-08-09
> **评估目标**: 是否应将 `ydsz-common-json` 自研 JSON 引擎底层替换为 Jackson，保留对外 API 与定制能力
> **关联报告**: 《ydsz-common-json 过度设计评估与优化建议报告》

---

## 一、决策摘要

| 维度 | 结论 |
|------|------|
| **是否立即替换** | **不建议立即执行**。建议先完成 P0/P1 代码治理（已完成），观察 1-2 个迭代后再评估 |
| **中期方向** | 如团队决定长期维护自研引擎，保持现状（已治理）；如希望降低维护成本，可启动"适配器替换" |
| **最优路径** | **保留自研引擎 + 引入 Jackson 作为可选的第二引擎**（双引擎共存，通过 SPI 切换），而非完全替换 |

**核心判断**：经过本次过度设计治理（代码量已从 23,944 行降至约 1.5 万行级别），自研引擎的维护负担已显著降低；但长期看，自研 JSON 引擎的安全补丁、JDK 兼容、性能优化均需团队自主维护，与 Jackson 社区（100+ 贡献者、15+ 年）相比是结构性劣势。

---

## 二、现状基线（治理后）

| 指标 | 治理前 | 治理后 |
|------|-------|-------|
| 源码行数 | ~23,944 | ~1.5 万级（-37%） |
| 超 1000 行文件 | 6 个 | 4 个 |
| 注解数 | 31 | 26（含移除） |
| 无淘汰缓存 | 6 处 | 0（BoundedLruCache 全覆盖） |
| ThreadLocal 清理 | 3 个无清理 | 全部有清理入口 + @PreDestroy |
| 循环依赖 | 1 处 | 0 |
| 死代码 | ~100 行 | 0 |
| 编译状态 | **无法编译（6 处错误 + module-info）** | 编译通过 |

---

## 三、替换方案对比

### 方案 A：完全替换为 Jackson（API 兼容层）

```
现状：业务代码 → YdszJson/JsonMapper（自研引擎）
目标：业务代码 → YdszJson/JsonMapper（薄适配层）→ Jackson ObjectMapper
```

**保留**（团队特有定制，通过 Jackson Module/Serializer 实现）：
- `@JsonClass` AutoType 白名单（→ 自定义 Jackson AnnotationIntrospector）
- XSS 过滤 `XssStringDeserializer`（→ 自定义 JsonDeserializer）
- 敏感数据脱敏 `SensitiveDataSerializer`（→ 自定义 JsonSerializer）
- `JsonModule` SPI（→ 映射为 Jackson Module）

**替换**（交给 Jackson）：
- 流式解析（JsonParser/JsonGenerator）
- POJO 绑定 / 注解处理 / 树模型
- 日期数字格式化、泛型反序列化

| 优点 | 缺点 |
|------|------|
| 维护成本降 60-70% | 引入外部依赖，失去"零依赖"卖点 |
| 获得社区安全补丁（CVE 响应及时） | API 兼容适配层需要回归测试 |
| JDK 版本兼容由社区维护 | 定制行为（XSS/脱敏）适配 Jackson 需要额外工作 |
| 性能持续由社区优化 | 团队 JSON 技术积累不再自主沉淀 |

**工作量**：10-15 人日（含全量回归）
**风险**：中高（核心路径替换，需完整测试覆盖）

### 方案 B：保持自研 + Jackson 可切换（双引擎，推荐）

```
业务代码 → YdszJson/JsonMapper（统一门面）
              ├── 引擎实现 1：YdszEngine（自研，默认）
              └── 引擎实现 2：JacksonEngine（可选，通过配置切换）
```

- 在 `JsonMapper` 之下引入 `JsonEngine` SPI 接口
- 默认使用自研引擎（已治理、性能可控、零依赖）
- 配置 `ydsz.json.engine=jackson` 可切换（灰度验证场景）

| 优点 | 缺点 |
|------|------|
| 保留自研引擎的零依赖/性能优势 | 双引擎维护成本（两套实现） |
| Jackson 作为验证基准（JMH 对比） | 两引擎行为差异需要对齐测试 |
| 灰度切换，风险可控 | 初期实现工作量 ~5 人日 |

**工作量**：5-8 人日（SPI 抽取 + Jackson 适配器）
**风险**：低（默认路径不变）

### 方案 C：维持现状（已治理的自研引擎）

- 优点：零额外投入，已治理的代码可维护
- 缺点：长期安全/兼容维护自主承担；无社区背书

---

## 四、关键决策因素

### 4.1 安全（最高优先级）

| 因素 | 自研 | Jackson |
|------|------|---------|
| 反序列化 RCE 防护 | `@JsonClass` 白名单 + 深度/大小限制 | `activateDefaultTyping` 默认关闭 + CVE 响应 |
| CVE 预警渠道 | 无（需自主发现） | NVD/CVE 及时披露 |
| 漏洞修复 | 团队自主 | 社区 24-72h 出补丁 |
| AutoType 风险 | 白名单模型更严 | 需正确配置 |

### 4.2 性能

- 自研引擎已做 JMH 基准（测试期依赖 jackson-databind/fastjson2 对比）
- 纯字符串/POJO 场景自研引擎经 JIT 后与 Jackson 差距 <10%（估算，需实测）
- 若性能敏感路径（网关、日志序列化）无瓶颈，性能不构成替换理由

### 4.3 团队与技术积累

- 自研引擎是团队的差异化技术资产（性能优化、安全模型经验）
- 若团队有 2+ 人可长期投入引擎维护，方案 B/C 可行
- 若引擎维护仅靠 1 人或偶发投入，方案 A 更稳妥

### 4.4 生态兼容

- 当前 21 个子模块深度依赖 `YdszJson` API（Redis 序列化、MyBatis TypeHandler、Feign 编解码等）
- 对外 API 兼容是硬约束（无论哪个方案）

---

## 五、推荐路线图

```
Phase 1（当前已完成）：P0/P1/P2 代码治理 ✅
Phase 2（1-2 迭代后，可选）：抽取 JsonEngine SPI
    - JsonMapper 下引入引擎接口
    - 实现 JacksonEngine 适配器（test scope 起步）
    - JMH 对比自研 vs Jackson，产出性能报告
Phase 3（基于 Phase 2 数据决策）：
    - 性能无显著差异 → 维持自研（方案 C）
    - 维护成本成为瓶颈 → 灰度切 Jackson（方案 B → A）
```

**决策门槛**：
- 出现 1 次自主发现的反序列化安全漏洞且修复耗时 > 2 人日 → 启动方案 A
- 团队引擎维护人力降至 <0.5 人日/周 → 启动方案 B
- 新 JDK 大版本升级时引擎适配成本 > 3 人日 → 评估方案 A

---

## 六、附录：治理后仍需关注的自研引擎风险

| 风险 | 缓解措施 |
|------|---------|
| Java 17+ `setAccessible` 强封装 | 预研 `--add-opens` 或 MethodHandles 替代（BeanReader/FieldMeta） |
| 动态类加载缓存膨胀 | 已由 BoundedLruCache 兜底（容量 256-1024） |
| 线程池 ThreadLocal 残留 | 已提供 clearThreadLocals + @PreDestroy |
| 数值解析回归（外部提交 7e26ebf） | 需外部提交方修复 castResult 类型转换 |
