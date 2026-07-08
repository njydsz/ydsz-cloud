---
alwaysApply: true
scene: code
---

# 禁止行内全限定类名（FQN）用法

代码中不允许出现行内 FQN（Fully Qualified Name）导入，必须使用标准 `import` 语句后在代码中直接引用简单类名。

## 覆盖范围

以下场景禁止使用行内 FQN，必须先 `import` 再用简单类名：

1. **类型引用**：变量声明、返回类型、参数类型、泛型类型参数
2. **`.class` 字面量**：如 `com.njydsz.pmis.common.exception.BizException.class` → `BizException.class`
3. **注解**：如 `@org.springframework.boot.autoconfigure.condition.ConditionalOnBean(...)` → `@ConditionalOnBean(...)`
4. **静态方法调用**：如 `org.junit.jupiter.api.Assertions.assertThrows(...)` → `assertThrows(...)`（配合 `import static`）
5. **`new` 表达式**：如 `new com.njydsz.pmis.literule.expr.QLExpressExpressionEvaluator()` → `new QLExpressExpressionEvaluator()`
6. **`instanceof` 检查**：如 `x instanceof com.njydsz.pmis.common.exception.BizException` → `x instanceof BizException`

## 唯一例外

- **字符串字面量**中的 FQN（如反射类名 `"com.njydsz.pmis.literule.core.MicrometerRuleMetrics"`）可保留完整路径。
- **javadoc `{@link FQN}` 引用**可保留完整路径，但推荐在已 import 的情况下使用简单类名。

## 示例

错误（禁止）：
```java
// .class 字面量
assertThatThrownBy(() -> service.execute("INVALID", ctx()))
    .isInstanceOf(com.njydsz.pmis.common.exception.BizException.class);

// 注解
@org.springframework.boot.autoconfigure.condition.ConditionalOnBean(RuleConfigProvider.class)

// 静态方法调用
org.junit.jupiter.api.Assertions.assertThrows(BizException.class, () -> service.send(req));

// new 表达式
return new com.njydsz.pmis.literule.expr.QLExpressExpressionEvaluator();
```

正确：
```java
import com.njydsz.pmis.common.exception.BizException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.njydsz.pmis.literule.expr.QLExpressExpressionEvaluator;

// .class 字面量
assertThatThrownBy(() -> service.execute("INVALID", ctx()))
    .isInstanceOf(BizException.class);

// 注解
@ConditionalOnBean(RuleConfigProvider.class)

// 静态方法调用
assertThrows(BizException.class, () -> service.send(req));

// new 表达式
return new QLExpressExpressionEvaluator();
```
