# 表达式编辑器后端校验 API

> 适用于 1.4.0 起。LiteRule 提供结构化的表达式校验 API，返回错误类型、错误位置、错误描述以及引用的变量列表，供前端表达式编辑器渲染错误标记和自动补全提示。

## 1. 设计目标

- **结构化错误信息**：从原来的 `Result<Boolean>` 升级为 `ExpressionValidationResult`，包含错误类型、行列号、错误描述
- **多类型校验**：支持条件表达式、严重度表达式、模板表达式三种校验语义
- **变量引用提取**：从表达式中提取引用的变量名（基于正则，不依赖 VariableRegistry），用于前端"已使用变量"提示
- **沙箱拦截可见**：将 Aviator 沙箱拦截的 `SecurityException` 包装为 `SANDBOX_VIOLATION` 错误类型，前端可显式提示用户
- **批量校验**：一次性校验多条表达式（如保存规则时同时校验条件、严重度、标题模板）
- **引擎无关**：`ExpressionEvaluator.validateDetailed` 是 default 方法，未来切换到 QLExpress 等其他引擎时可保持 API 兼容

## 2. 核心类

| 类 | 路径 | 职责 |
|----|------|------|
| `ExpressionValidationResult` | `literule.expr` | 校验结果 DTO，含 ErrorType 枚举 |
| `ExpressionValidationService` | `literule.expr` | 高层校验服务，提供 condition/severity/template/batch 四种语义 |
| `ExpressionEvaluator.validateDetailed` | `literule.expr` | 接口 default 方法，允许具体实现类 override 提供详细错误 |
| `AviatorExpressionEvaluator.validateDetailed` | `literule.expr` | Aviator 实现，捕获异常并解析行列号 |

## 3. 错误类型枚举

```java
public enum ErrorType {
    OK,                      // 校验通过
    EMPTY,                   // 表达式为空或全为空白字符
    SYNTAX_ERROR,            // Aviator 语法错误（缺括号、运算符错误等）
    SANDBOX_VIOLATION,       // 沙箱拦截（包含危险函数或类访问）
    UNDEFINED_VARIABLE,      // 引用了未定义的变量（依赖 VariableRegistry，当前未启用）
    TEMPLATE_FORMAT_ERROR,   // 模板占位符未闭合（如 ${foo 缺 } ）
    UNKNOWN                  // 其他未知错误
}
```

## 4. REST API

### 4.1 单条表达式校验

**POST** `/api/v1/rules/validate-expression`

请求体：

```json
{
  "expression": "evmRedCount >= 3 && grossMargin < 0.05",
  "type": "condition"
}
```

| 字段 | 必填 | 说明 |
|------|------|------|
| `expression` | 是 | 表达式文本 |
| `type` | 否 | 校验类型：`condition`（默认）/ `severity` / `template` |

响应：

```json
{
  "code": 200,
  "data": {
    "valid": true,
    "errorType": "OK",
    "errorMessage": null,
    "errorLine": -1,
    "errorColumn": -1,
    "expression": "evmRedCount >= 3 && grossMargin < 0.05",
    "parseTimeMs": 12,
    "referencedVariables": ["evmRedCount", "grossMargin"]
  }
}
```

### 4.2 批量校验

**POST** `/api/v1/rules/validate-batch`

请求体：

```json
{
  "condition": "evmRedCount >= 3",
  "severity": "amount > 5000 ? 'RED' : 'YELLOW'",
  "titleTemplate": "项目 ${projectName} 红色预警"
}
```

响应：key=标签，value=校验结果（与请求结构对应）。

### 4.3 兼容旧端点

`GET /api/v1/rules/validate?expression=xxx` 仍保留，返回 `Result<Boolean>`，向后兼容。

## 5. 编程式调用

```java
@Autowired
private ExpressionValidationService validationService;

// 校验条件表达式
ExpressionValidationResult r1 = validationService.validateCondition("evmRedCount >= 3");
if (!r1.isValid()) {
    // 前端展示 r1.getErrorType() + r1.getErrorMessage()
}

// 校验严重度表达式（可选，为空时视为合法）
ExpressionValidationResult r2 = validationService.validateSeverity(null);
assert r2.isValid();

// 校验模板表达式
ExpressionValidationResult r3 = validationService.validateTemplate("项目 ${projectName} 预警");
// r3.getReferencedVariables() = ["projectName"]

// 批量校验
Map<String, String> exprs = Map.of(
    "condition", "a > 1",
    "severity", "b < 2 ? 'RED' : 'YELLOW'"
);
Map<String, ExpressionValidationResult> results = validationService.validateBatch(exprs);
```

## 6. 校验流程

### 6.1 条件/严重度表达式

```text
                 ┌──────────────────────────────┐
                 │  validateDetailed(expression) │
                 └──────────┬───────────────────┘
                            │
              ┌─────────────┴──────────────┐
              │ expression 为空？          │
              └─────────────┬──────────────┘
                            │ 是
                            ▼
                  返回 EMPTY 错误
                            │
                            │ 否
                            ▼
              ┌─────────────┴──────────────┐
              │ sandboxCheck(expression)  │
              └─────────────┬──────────────┘
                            │ 抛 SecurityException
                            ▼
                返回 SANDBOX_VIOLATION
                            │
                            │ 通过
                            ▼
              ┌─────────────┴──────────────┐
              │ instance.compile(expr)     │
              └─────────────┬──────────────┘
                            │ 抛异常
                            ▼
              解析异常 message 中的 "line N" / "column N"
              返回 SYNTAX_ERROR + 行列号
                            │
                            │ 通过
                            ▼
              extractVariables(expression)
              返回 OK + referencedVariables
```

### 6.2 模板表达式

```text
                 ┌──────────────────────────────┐
                 │  validateTemplate(template)  │
                 └──────────┬───────────────────┘
                            │
              ┌─────────────┴──────────────┐
              │ template 为空？           │
              └─────────────┬──────────────┘
                            │ 是
                            ▼
                  返回 OK（模板可选）
                            │
                            │ 否
                            ▼
              ┌─────────────┴──────────────┐
              │ 检测未闭合 ${...           │
              └─────────────┬──────────────┘
                            │ 检测到
                            ▼
              返回 TEMPLATE_FORMAT_ERROR
              "未闭合的占位符 ${ ... }"
                            │
                            │ 全部闭合
                            ▼
              提取所有 ${var} 中的变量名
              返回 OK + referencedVariables
```

## 7. 错误示例

### 7.1 语法错误

```json
请求: { "expression": "a > ", "type": "condition" }

响应:
{
  "valid": false,
  "errorType": "SYNTAX_ERROR",
  "errorMessage": "could not compile expression: a >",
  "errorLine": -1,
  "errorColumn": -1,
  "expression": "a > ",
  "parseTimeMs": 3,
  "referencedVariables": []
}
```

### 7.2 沙箱拦截

```json
请求: { "expression": "Runtime.getRuntime().exec('rm -rf /')", "type": "condition" }

响应:
{
  "valid": false,
  "errorType": "SANDBOX_VIOLATION",
  "errorMessage": "表达式包含危险操作，已被沙箱拦截: Runtime.getRuntime().exec('rm -rf /')",
  "parseTimeMs": 0,
  "referencedVariables": []
}
```

### 7.3 模板未闭合

```json
请求: { "expression": "项目 ${projectName 的状态", "type": "template" }

响应:
{
  "valid": false,
  "errorType": "TEMPLATE_FORMAT_ERROR",
  "errorMessage": "模板存在未闭合的占位符 ${ ... }，缺少 }",
  "parseTimeMs": 0,
  "referencedVariables": []
}
```

## 8. 单元测试

- 单元测试类：[ExpressionValidationServiceTest](file:///d:/Code/ydsz/ydsz-pmis/ydsz-pmis-backend/ydsz-pmis-literule/src/test/java/com/njydsz/pmis/literule/expr/ExpressionValidationServiceTest.java)
- 覆盖场景（20 个用例）：
  1. 合法条件表达式 → valid=true + referencedVariables 提取
  2. 空表达式 / null / 全空白 → EMPTY
  3. 缺右操作数 / 括号不平衡 → SYNTAX_ERROR
  4. Runtime.exec / Class.forName → SANDBOX_VIOLATION
  5. 严重度表达式为空 → valid=true（可选字段）
  6. 模板表达式合法 → valid=true + 占位符变量提取
  7. 模板无占位符 → valid=true + 空变量列表
  8. 模板未闭合 → TEMPLATE_FORMAT_ERROR
  9. 多个未闭合占位符 → TEMPLATE_FORMAT_ERROR
  10. 批量校验 + null 入参
  11. Aviator 关键字过滤（`true` 不被识别为变量）
  12. 驼峰变量名提取
  13. parseTimeMs 非负

## 9. 限制与后续演进

### 9.1 当前限制

1. **不做变量存在性校验**：`UNDEFINED_VARIABLE` 错误类型当前不会触发，依赖 VariableRegistry（P2-4）落地后才能实现
2. **错误行列号可能为 -1**：Aviator 异常消息中并不总是包含行列号信息，前端需要兜底处理（如直接展示 errorMessage 文本）
3. **不做运行时类型检查**：条件表达式返回非 boolean 时不会报错，由引擎运行时返回 false 兜底
4. **模板变量不做嵌套支持**：当前 `${a.b.c}` 会作为整体变量名 `a.b.c` 返回，不支持点号路径解析

### 9.2 后续演进路径

- **P2-4 变量空间元数据**：通过 `VariableRegistry` 提供变量定义，触发 `UNDEFINED_VARIABLE` 校验
- **P2-5 规则模板服务化**：模板校验叠加变量约束（如 `${projectName}` 必须为字符串类型）
- **QLExpress 切换**：实现 `QLExpressExpressionEvaluator.validateDetailed`，复用同一套 API
