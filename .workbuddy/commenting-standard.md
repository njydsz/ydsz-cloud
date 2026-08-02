# 代码注释完善标准（对标互联网大厂）

> 适用范围：ydsz-pmis 全部后端（Java）与前端（TypeScript / Vue）源码文件。
> 参考基准：阿里巴巴《Java 开发手册》（嵩山版）、Google Java Style Guide、Google TypeScript Style Guide、Tencent/ByteDance 内部 TS/Vue 注释规约、JSDoc / TSDoc 规范。
> 目标：在不改动任何业务逻辑的前提下，补齐缺失的、完善薄弱的注释，使每个公开类型与公开方法都有准确、可读、说明"为什么"而非"是什么"的文档。

---

## 0. 总原则（从大厂规约提炼）

1. **注释解释 WHY，而非复述 WHAT。** 代码本身能说清的（如 `i++`）不要写注释；业务意图、约束、边界、踩坑点、为什么用这个算法/参数才需要注释。
2. **不写无意义/过期注释。** 不写 `"设置名称"` 这类对 `setName()` 的复述；删除与代码矛盾的注释。
3. **公开即文档。** 所有 `public` / `protected` 类、接口、枚举、方法、常量都必须有文档注释；`private` 方法如逻辑非显而易见也应有简短说明。
4. **保持语言一致性。** 本仓库注释以**中文**为主，继续沿用中文；代码标识符保持英文。
5. **只加注释，不改逻辑。** 严禁修改方法签名、变量名、控制流、返回值、import 等任何代码行为；仅插入/补全注释文本。
6. **已有良好注释的文件跳过。** 若文件已有规范、完整的 Javadoc/JSDoc，不要重复或覆盖，避免噪声。
7. **格式正确。** Java 用标准 Javadoc（`/** ... */` + 标签）；TS/Vue 用 JSDoc（`/** ... */` + TSDoc 标签）；行内说明用 `//`。

---

## 1. Java 后端注释规范

### 1.1 类 / 接口 / 枚举（必须）

每个顶层类型前加类级 Javadoc，包含：
- 一句话职责描述（首句以 `.` 结尾，Javadoc 工具会提取为首句摘要）。
- 关键设计点、使用场景、线程安全说明（如适用）。
- 标签：`@author ydsz-team`、`@since <版本或日期>`、`@see`（相关类，可选）。

```java
/**
 * 网关 IP 工具类（WebFlux 响应式版本）。
 *
 * <p>提供从 {@link ServerHttpRequest} 提取客户端真实 IP 以及 IP 白名单校验功能。
 *
 * <p><b>线程安全性：</b>本类方法均为无状态静态方法，可安全并发调用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class GatewayIpUtils { ... }
```

### 1.2 方法（公开/保护必须）

每个公开/保护方法加方法级 Javadoc：
- 功能描述（讲清意图与边界条件）。
- 参数：`@param 名称 说明`（说明含义与合法/非法取值、是否可为 null）。
- 返回值：`@return 说明`（包含特殊返回值含义，如"无法获取时返回 null"）。
- 异常：`@throws 异常类 触发条件`。
- 复杂算法用 `<ol>/<ul>` 列出步骤；代码片段用 `{@code ...}` 或 `<pre>`。

```java
/**
 * 从 WebFlux 请求中提取客户端真实 IP（含可信代理链校验）。
 *
 * <p>判断逻辑：
 * <ol>
 *   <li>获取直连 IP（不可伪造）</li>
 *   <li>仅当直连 IP 为可信代理时才信任 X-Forwarded-For / X-Real-IP</li>
 * </ol>
 *
 * @param request WebFlux 请求，为 null 时返回 {@link #DEFAULT_IP}
 * @return 客户端 IP，无法获取时返回默认 IP
 */
public static String getClientIp(ServerHttpRequest request) { ... }
```

### 1.3 字段 / 常量（建议）

`public`/`static final` 常量必须有字段注释说明含义与单位/取值范围；
`private` 字段如非显而易见可加简短注释。

```java
/** 可信代理头：X-Forwarded-For */
private static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";
```

### 1.4 行内注释

仅对"非显而易见"的逻辑写 `//`：Magic Number 的来源、不得已的 hack、与某版本/某依赖的兼容取舍、业务特殊规则。避免逐行复述。

### 1.5 禁止项

- 不要用 `/** */` 包裹纯装饰或玩笑内容。
- 不要删除原有有效注释。
- 不要为 `getter/setter`、`equals`、`hashCode`、`toString` 等自解释方法强行写长注释（可省略或一行）。

---

## 2. 前端 TypeScript 注释规范

### 2.1 导出类型（必须）

每个 `export` 的函数、类、接口、类型别名、常量都要有 JSDoc：

```ts
/**
 * 解析并格式化日期为指定 pattern。
 *
 * @param date - 输入日期，支持 Date / 时间戳 / ISO 字符串
 * @param pattern - 输出格式，默认 'YYYY-MM-DD'
 * @returns 格式化后的字符串；输入非法时返回空串
 */
export function formatDate(date: Date | number | string, pattern = 'YYYY-MM-DD'): string {
  ...
}
```

### 2.2 接口 / 类型别名

说明用途，并对非直观字段加行内或块注释：

```ts
/** 用户基础信息 */
export interface UserInfo {
  /** 用户唯一 ID */
  id: string;
  /** 所属租户 ID，多租户隔离的隔离键 */
  tenantId: string;
  /** 角色编码列表 */
  roles: string[];
}
```

### 2.3 行内注释

对业务规则、魔法值、兼容处理、复杂正则/算法写 `//`，同样解释 WHY。

---

## 3. 前端 Vue（SFC）注释规范

### 3.1 组件级

在 `<script setup>` 顶部或 `<template>` 上方用 `<!-- -->` 或 JSDoc 描述组件职责、使用场景、关键 props/emits：

```vue
<!--
 * 审批表单组件
 * 用途：新建/编辑审批流节点配置，复用于审批中心与流程设计器。
 * 关键 props：mode（create|edit）、nodeId
 * 关键 emits：submit、cancel
-->
```

### 3.2 Props / Emits / Expose

对应 `defineProps` / `defineEmits` / `defineExpose` 的每个成员加注释说明含义、取值与触发时机。

```ts
const props = defineProps<{
  /** 表单模式：create 新建，edit 编辑 */
  mode: 'create' | 'edit';
  /** 待编辑节点 ID，新建时为空 */
  nodeId?: string;
}>();
```

### 3.3 组合式函数（composables）

导出函数按 2.1 规范写 JSDoc；说明返回的响应式状态含义与副作用。

---

## 4. 执行流程（供子代理遵循）

对每个目标目录：
1. 通读目录内每个源码文件。
2. 判断注释完整度：公开类型/方法是否有规范文档注释？复杂逻辑是否有 WHY 注释？
3. 仅对**缺失或不规范**的部分，用 `Edit` 精准插入注释（优先局部编辑，避免整文件重写；若整文件重写须 100% 保留原代码）。
4. 已规范完整的文件直接跳过，不做冗余改动。
5. 处理完一个目录后，输出简短汇总：处理了几个文件、跳过几个、补了哪些类型的注释。

> 严禁修改任何代码逻辑、变量名、签名、import；仅注释层面的增补。
