<!--
  ===========================================================================
  文件名: rule-qlexpress-skeleton.md
  路径:   docs/rules/rule-qlexpress-skeleton.md
  作用:   LiteRule 1.4.0+ QLExpress 备选表达式引擎骨架设计与切换计划
  关联:   ydsz-pmis-literule 源码  /  rule-expression-validation.md
  ===========================================================================
-->

# QLExpress 备选实现骨架

> 适用于 1.4.0 起。LiteRule 在保留 Aviator 作为默认表达式引擎的基础上，提供 `QLExpressExpressionEvaluator` 骨架，为 v2.0 切换到 QLExpress 引擎预留扩展点，支持按配置选择表达式引擎。

## 1. 设计目标

- **引擎可切换**：通过 `pmis.literule.evaluator=aviator|qlexpress` 配置项选择表达式引擎，无需修改业务代码
- **接口复用**：`QLExpressExpressionEvaluator` 实现 `ExpressionEvaluator` 接口，与 `AviatorExpressionEvaluator` 保持 API 兼容
- **骨架先行**：1.4.0 仅提供编译通过的骨架，所有方法抛出 `UnsupportedOperationException`，提示用户使用 Aviator
- **平滑过渡**：v2.0 引入 QLExpress 依赖后补全实现，业务方仅需切换配置项即可迁移
- **功能对齐**：未来 QLExpress 实现需对齐 Aviator 的沙箱模式、编译缓存、详细校验三大能力

## 2. 为什么选择 QLExpress

| 维度 | Aviator（当前默认） | QLExpress（计划） |
|------|---------------------|-------------------|
| 语法 | 自定义 DSL，接近函数式 | 接近 Java，学习成本低 |
| 控制流 | 不支持 if/else、for | 支持 if/else、for 等控制流 |
| 性能 | 编译缓存，执行效率高 | 略低于 Aviator，但功能更强 |
| 沙箱 | 内置 Feature 开关，可禁用危险类 | 需手动限制 `addFunction` / 反射访问 |
| 适用场景 | 纯表达式求值，性能敏感 | 复杂业务逻辑，需要控制流 |

QLExpress 适合需要 if/else 分支判断的复杂规则，例如"若金额大于 100 万且租户为金融行业则触发 RED，否则触发 YELLOW"。

## 3. 核心类

| 类 | 路径 | 职责 |
|----|------|------|
| `ExpressionEvaluator` | `literule.expr` | 表达式求值器接口（Aviator / QLExpress 共用） |
| `AviatorExpressionEvaluator` | `literule.expr` | Aviator 实现（默认，1.1.0 起） |
| `QLExpressExpressionEvaluator` | `literule.expr` | QLExpress 骨架实现（1.4.0 起，方法未实现） |

## 4. 接口实现（骨架）

```java
package com.njydsz.pmis.literule.expr;

import com.njydsz.pmis.literule.api.RuleContext;
import lombok.extern.slf4j.Slf4j;

/**
 * QLExpress 表达式求值器实现（骨架）
 *
 * <p>当前为骨架实现，所有方法抛出 UnsupportedOperationException。
 * 计划在 v2.0 引入 QLExpress 依赖后补全实现，通过
 * pmis.literule.evaluator=aviator|qlexpress 切换。
 *
 * @since 1.4.0
 */
@Slf4j
public class QLExpressExpressionEvaluator implements ExpressionEvaluator {

    private static final String NOT_IMPLEMENTED_MSG =
            "QLExpress 求值器尚未实现，请使用 AviatorExpressionEvaluator";

    // TODO: 待 v2.0 引入 com.alibaba:QLExpress 依赖后实现以下方法

    @Override
    public boolean evalBoolean(String expression, RuleContext context) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED_MSG);
    }

    @Override
    public <T> T eval(String expression, RuleContext context) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED_MSG);
    }

    @Override
    public boolean validate(String expression) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED_MSG);
    }

    @Override
    public ExpressionValidationResult validateDetailed(String expression) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED_MSG);
    }
}
```

## 5. 切换配置（计划）

`application.yml`：

```yaml
pmis:
  literule:
    enabled: true
    evaluator: aviator   # 默认 aviator；v2.0 起支持 qlexpress
```

| 配置项 | 默认 | 说明 |
|--------|------|------|
| `evaluator` | `aviator` | 表达式引擎选择：`aviator` / `qlexpress` |

`LiteRuleAutoConfiguration` 计划按配置项装配对应 Bean（v2.0 实现）：

```java
@Bean
@ConditionalOnProperty(prefix = "pmis.literule", name = "evaluator",
        havingValue = "qlexpress", matchIfMissing = false)
public ExpressionEvaluator qlexpressExpressionEvaluator() {
    return new QLExpressExpressionEvaluator();
}

@Bean
@ConditionalOnProperty(prefix = "pmis.literule", name = "evaluator",
        havingValue = "aviator", matchIfMissing = true)
public ExpressionEvaluator aviatorExpressionEvaluator() {
    return new AviatorExpressionEvaluator();
}
```

## 6. v2.0 实现要点

补全 `QLExpressExpressionEvaluator` 时需对齐 Aviator 实现的三大能力：

### 6.1 编译缓存

参考 `AviatorExpressionEvaluator#compile` 的 `ConcurrentHashMap<String, Expression>` 方案，缓存 QLExpress 编译结果：

```java
private final ConcurrentHashMap<String, InstructionSet> cache = new ConcurrentHashMap<>();

private InstructionSet compile(String expression) {
    return cache.computeIfAbsent(expression, key ->
            runner.parseInstructionSet(key));
}
```

### 6.2 沙箱模式

参考 `AviatorExpressionEvaluator#configureSandbox`，限制 QLExpress 的危险能力：

- 禁用反射访问（`Class.forName` / `Method.invoke`）
- 禁用 `System.exit` / `Runtime.getRuntime` / `ProcessBuilder`
- 禁用文件 I/O（`FileInputStream` / `Files`）
- 通过 `ExpressRunner.addFunction` 白名单方式注入允许的函数

### 6.3 详细校验

override `validateDetailed`，解析 QLExpress 异常中的行列号，组装 `ExpressionValidationResult`：

```java
@Override
public ExpressionValidationResult validateDetailed(String expression) {
    // 解析 QLExpress 异常，提取错误类型与行列号
    // 组装 ExpressionValidationResult（参考 AviatorExpressionEvaluator#validateDetailed）
}
```

## 7. 限制与后续演进

### 7.1 当前限制

1. **方法未实现**：1.4.0 所有方法抛出 `UnsupportedOperationException`，不可直接使用
2. **无 QLExpress 依赖**：`pom.xml` 未引入 `com.alibaba:QLExpress`，骨架仅依赖接口
3. **未装配 Bean**：`LiteRuleAutoConfiguration` 未按配置装配 QLExpress 求值器
4. **无沙箱实现**：QLExpress 沙箱方案需 v2.0 设计，与 Aviator 沙箱行为可能不一致

### 7.2 后续演进路径

- **v2.0 引入依赖**：`pom.xml` 增加 `com.alibaba:QLExpress:3.x` 依赖（可选 scope）
- **v2.0 补全实现**：实现四个方法，对齐 Aviator 的编译缓存、沙箱、详细校验
- **v2.0 配置装配**：`LiteRuleAutoConfiguration` 按 `pmis.literule.evaluator` 装配对应 Bean
- **v2.1 性能对比**：提供 Aviator vs QLExpress 性能基准测试，辅助业务方选择引擎
- **v2.2 表达式迁移工具**：自动将 Aviator 表达式转换为 QLExpress 语法（如 `seq.list` → `Arrays.asList`）
