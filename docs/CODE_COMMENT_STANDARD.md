# 代码注释规范（YDSZ-PMIS）

> 对标 Google Java Style Guide、《阿里巴巴 Java 开发手册（嵩山版）》、TSDoc / JSDoc 官方规范。
> 本文档是全项目补全注释的**唯一依据**，所有模块必须遵循。

---

## 0. 核心原则

| 原则 | 说明 |
|---|---|
| **写"为什么"，不写"是什么"** | 代码已经表达了 What，注释要补充 Why、边界、副作用、坑 |
| **注释必须有效** | Javadoc 必须位于**所有注解之前**，否则工具不采集，等同无效注释 |
| **禁止噪音注释** | `// 设置名称` 配 `setName()` 属于噪音，应删除 |
| **注释与代码同步** | 改代码必须改注释；过期注释比没有注释更有害 |
| **中文优先** | 业务语义用中文表达；技术术语、类名、方法名保留英文 |

### 反例：注释错位（本项目已发现 50 处，必须修复）

```java
// ❌ 错误：Javadoc 在 @Override 之后 —— javadoc 工具与 IDE 均不采集，注释失效
@Override
/**
 * IP 黑名单拦截。
 */
public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) { }

// ✅ 正确：Javadoc 位于所有注解之前
/**
 * IP 黑名单拦截。
 */
@Override
public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) { }
```

---

## 1. 后端 Java 规范

### 1.1 必须有文档注释的元素

| 元素 | 要求 | 说明 |
|---|---|---|
| `class` / `interface` / `enum` / `record` / `@interface` | **必须** | 含职责说明 + `@author` + `@since` |
| `public` / `protected` 方法 | **必须** | 含描述 + `@param` + `@return` + `@throws` |
| `public` 常量、枚举项 | **必须** | 单行 `/** ... */` |
| DTO / VO / Entity 字段 | **必须** | 单行 `/** ... */`，说明业务含义、单位、取值范围 |
| `private` 复杂方法（>20 行或有算法逻辑） | **建议** | 至少说明意图与边界 |

### 1.2 豁免项（写了反而是噪音）

- `@Override` 方法：父类/接口已有 Javadoc 时可省略；**但如果有额外实现约定（副作用、降级策略、顺序），必须写**
- 简单 getter / setter（单行 return / 赋值）
- 构造器（除非有非平凡的初始化契约）
- `equals` / `hashCode` / `toString` / `main`
- Lombok 生成的方法

### 1.3 类级 Javadoc 模板

```java
/**
 * 【一句话职责说明，以句号结尾】
 *
 * <p>【补充说明：设计意图、使用场景、与其他组件的关系】
 *
 * <h3>核心能力</h3>
 * <ol>
 *   <li><b>能力一</b>：说明</li>
 *   <li><b>能力二</b>：说明</li>
 * </ol>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 *   XxxService service = new XxxService();
 *   Result r = service.execute(param);
 * }</pre>
 *
 * <h3>注意事项</h3>
 * <ul>
 *   <li>线程安全性说明</li>
 *   <li>性能特征 / 容量上限</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see RelatedClass
 */
```

### 1.4 方法级 Javadoc 模板

```java
/**
 * 【动词开头的一句话说明，以句号结尾】
 *
 * <p>【可选：算法思路、边界条件、失败降级策略、幂等性、事务边界】
 *
 * @param userId 用户 ID，不可为 {@code null}
 * @param limit  返回条数上限，取值范围 [1, 200]，超出按 200 处理
 * @return 项目列表；无数据时返回空列表而非 {@code null}
 * @throws BizException 当用户无该项目访问权限时抛出，错误码 {@code PERM_DENIED}
 */
```

### 1.5 关键要求

- **`@param` / `@return` 描述业务语义**，不要写 `@param userId userId` 这种废话
- **明确 null 契约**：能否传 null、是否返回 null / 空集合
- **明确异常**：什么条件抛什么异常、错误码
- **并发与事务**：线程安全性、`@Transactional` 传播行为、锁粒度
- **降级策略**：Feign fallback、缓存穿透、Redis 不可用时的行为
- 引用其他类型用 `{@link Xxx}`，代码片段用 `{@code xxx}`

### 1.6 分层侧重点（DDD 分层）

| 层 | 注释侧重 |
|---|---|
| `*-api`（DTO / Feign） | 字段业务含义、校验规则、Feign 降级行为 |
| `*-domain`（领域模型） | 业务规则、不变量（invariant）、状态机流转 |
| `*-infra`（基础设施） | 数据源、索引依赖、缓存键设计、外部系统契约 |
| `*-server`（应用服务） | 事务边界、编排顺序、幂等设计 |
| `*-web`（Controller） | 接口语义、权限要求、限流策略 |

---

## 2. 前端 TypeScript / Vue 规范

### 2.1 必须有注释的元素

| 元素 | 要求 |
|---|---|
| 每个 `.ts` / `.vue` 文件 | **必须**有文件头注释 |
| `export` 的 function / class | **必须**有 JSDoc（含 `@param` / `@returns`） |
| `export` 的 interface / type | **必须**说明用途；复杂字段逐个注释 |
| Composable（`useXxx`） | **必须**说明入参、返回值、副作用、生命周期依赖 |
| Vue 组件 Props / Emits | **必须**逐项注释 |

### 2.2 文件头模板

```ts
/**
 * 【模块职责一句话说明】
 *
 * @remarks
 * 【补充说明：设计意图、依赖关系、使用约束】
 *
 * @author ydsz-team
 * @since 1.0.0
 */
```

### 2.3 函数 JSDoc 模板

```ts
/**
 * 将时间戳格式化为指定格式的日期字符串。
 *
 * @remarks
 * 内部使用 dayjs 解析；解析失败时**不抛异常**，
 * 会打印 error 日志并原样返回入参，调用方需自行校验结果。
 *
 * @param time - 时间戳（毫秒）或可被 dayjs 解析的日期字符串
 * @param format - 输出格式，默认 `'YYYY-MM-DD'`
 * @returns 格式化后的日期字符串；解析失败时返回原始入参
 *
 * @example
 * ```ts
 * formatDate(1700000000000);            // '2023-11-15'
 * formatDate('2023-11-15', 'MM/DD');    // '11/15'
 * ```
 */
export function formatDate(time: number | string, format = 'YYYY-MM-DD') { }
```

### 2.4 Vue SFC 模板

```vue
<!--
  【组件职责一句话说明】

  @displayName UserPicker
  @author ydsz-team
  @since 1.0.0
-->
<script setup lang="ts">
interface Props {
  /** 已选中的用户 ID 列表 */
  modelValue: string[];
  /** 是否允许多选，默认 false */
  multiple?: boolean;
  /** 最大可选数量，仅在 multiple 为 true 时生效 */
  max?: number;
}

interface Emits {
  /** 选中项变化时触发，携带最新的用户 ID 列表 */
  (e: 'update:modelValue', value: string[]): void;
}
</script>
```

### 2.5 TSDoc 标签使用

- `@param name - 描述`（注意 TSDoc 要求参数名后跟 ` - `）
- `@returns` 而非 `@return`
- `@remarks` 写补充说明，`@example` 写用例
- `@deprecated` 标注废弃，必须说明替代方案
- **不要**在 TS 里写 `@type` / `@returns {string}` 这类类型标注，类型由 TS 自身表达

---

## 3. 禁止事项

```java
// ❌ 噪音注释
/** 获取名称 */
public String getName() { return name; }

// ❌ 复读机注释
/**
 * @param userId userId
 * @return 结果
 */

// ❌ 过期注释（代码已改，注释未改）
/** 返回前 10 条 */
public List<X> list() { return query(50); }

// ❌ 被注释掉的死代码 —— 直接删除，Git 有历史
// public void oldMethod() { ... }

// ❌ 无意义的分隔符
//////////////////////////////////

// ❌ 情绪化 / 无信息量
// 这里不要动，动了就崩
```

---

## 4. 校验方式

```bash
# 全量扫描注释覆盖率
python .workbuddy/scripts/scan_comments.py

# 查看指定模块待办明细
python .workbuddy/scripts/scan_comments.py ydsz-common
python .workbuddy/scripts/scan_comments.py comm/@core
```

### 达标线

| 指标 | 目标 |
|---|---|
| 类级 Javadoc 覆盖率 | 100% |
| public/protected 方法 Javadoc 覆盖率 | ≥ 98% |
| Javadoc 错位数 | 0 |
| 前端文件头覆盖率 | 100% |
| 前端导出成员 JSDoc 覆盖率 | ≥ 98% |

---

*本规范随项目演进，修改需同步通知团队。*
