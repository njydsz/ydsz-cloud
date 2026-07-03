# AI 增强端点

P2-15 引入的 AI 增强能力，基于 LLM 客户端抽象层，支持 OpenAI 兼容协议（OpenAI / DeepSeek / 通义千问 / Ollama）。

## 启用配置

```yaml
pmis:
  literule:
    ai:
      enabled: true                    # 启用 AI 增强（默认 false）
      llm-client: OPENAI_COMPATIBLE    # 客户端类型：MOCK / OPENAI_COMPATIBLE
      llm-api-url: https://api.openai.com/v1/chat/completions
      llm-api-key: sk-xxxx             # API Key
      llm-model: gpt-4o-mini           # 模型名
      llm-timeout-ms: 15000            # 调用超时
      llm-temperature: 0.2             # 温度（越低越稳定）
      health-hit-rate-weight: 0.30     # 健康度：命中率权重
      health-error-rate-weight: 0.30   # 健康度：错误率权重
      health-complexity-weight: 0.20   # 健康度：复杂度权重
      health-coverage-weight: 0.20     # 健康度：覆盖率权重
      health-complexity-threshold: 80  # 复杂度上限（token 数）
      recommend-top-n: 10              # 推荐结果最大条数
```

::: tip MOCK 客户端
默认使用 `MOCK` 客户端，无需 API Key 即可调试，根据输入关键词返回确定性 mock 结果。开发环境推荐先用 MOCK 验证流程，再切换到真实 LLM。
:::

## 1. 自然语言转规则（NL2Rule）

将自然语言描述转为结构化规则定义。

```http
POST /api/v1/rules/ai/nl2rule
Content-Type: application/json
Authorization: Bearer {token}

{
  "naturalLanguage": "当 EVM 红色预警数大于等于 3 时触发红色严重告警"
}
```

**响应**：

```json
{
  "code": 0,
  "data": {
    "code": "AI_GEN_xxx",
    "name": "EVM 红色预警阈值规则",
    "conditionExpression": "evmRedCount >= 3",
    "defaultSeverity": "RED",
    "description": "当 EVM 红色预警数大于等于 3 时触发"
  }
}
```

::: warning 降级策略
LLM 不可用时降级返回空壳 `RuleDefinition`（不抛异常），前端可引导用户手动填写。
:::

## 2. 生成规则描述

基于规则定义生成 1~3 句中文业务描述。

```http
GET /api/v1/rules/{ruleCode}/ai/describe
Authorization: Bearer {token}
```

## 3. 表达式优化建议

基于规则条件表达式生成优化建议文本。

```http
GET /api/v1/rules/{ruleCode}/ai/optimize
Authorization: Bearer {token}
```

## 4. 规则健康度评分

4 维加权评分模型，返回 0~100 总分 + 等级 + 建议。

```http
GET /api/v1/rules/{ruleCode}/ai/health
Authorization: Bearer {token}
```

**响应**：

```json
{
  "code": 0,
  "data": {
    "ruleCode": "EVM_RED_ALERT",
    "totalScore": 85.5,
    "level": "GOOD",
    "dimensions": {
      "hitRate": 90.0,
      "errorRate": 100.0,
      "complexity": 75.0,
      "coverage": 80.0
    },
    "stats": {
      "evaluations": 1500,
      "triggered": 120,
      "errors": 3
    },
    "suggestions": [
      "命中率 8.0% 处于健康区间",
      "表达式复杂度适中"
    ]
  }
}
```

### 评分维度

| 维度 | 权重 | 评分逻辑 |
|------|------|---------|
| 命中率 | 30% | 5%~30% 为健康区间 → 100；过低/过高扣分；样本 <30 按 100 算 |
| 错误率 | 30% | 0% → 100；50%+ → 0；线性插值 |
| 复杂度 | 20% | token 数 / 阈值；≤30% → 100；≥100% → 0 |
| 覆盖率 | 20% | 引用变量在声明变量中的占比 |

### 健康等级

| 等级 | 分数区间 | 含义 |
|------|---------|------|
| EXCELLENT | 90+ | 优秀 |
| GOOD | 75+ | 良好 |
| WARN | 60+ | 需关注 |
| BAD | <60 | 需优化 |

## 5. 批量健康度评分

对全部规则逐条评分。

```http
GET /api/v1/rules/ai/health-batch
Authorization: Bearer {token}
```

## 6. 规则推荐

基于 4 种启发式算法生成推荐规则列表。

```http
GET /api/v1/rules/{ruleCode}/ai/recommend
Authorization: Bearer {token}
```

**响应**：

```json
{
  "code": 0,
  "data": [
    {
      "type": "FIELD_COMPLETION",
      "score": 0.85,
      "suggestedRuleCode": "EVM_RED_ALERT_V2",
      "reason": "高频字段 evmYellowCount 未在源规则中出现",
      "suggestedExpression": "evmRedCount >= 3 && evmYellowCount >= 5"
    },
    {
      "type": "VARIANT",
      "score": 0.60,
      "suggestedRuleCode": "EVM_RED_ALERT_LOOSE",
      "reason": "命中率 <1%，建议生成宽松阈值变体",
      "suggestedExpression": "evmRedCount > 2"
    }
  ]
}
```

### 推荐类型

| 类型 | 触发条件 | 建议 |
|------|---------|------|
| FIELD_COMPLETION | 高频字段（≥3 规则引用）未在源规则中出现 | 补全字段 |
| PATTERN_DUPLICATION | 共享 ≥3 变量的规则 | 提示重叠 |
| VARIANT | 低命中率（<1%）规则 | 生成宽松阈值变体 |
| SPLIT_SUGGESTION | 高错误率（≥10%）且含 `&&` 的规则 | 拆分规则 |

## LLM 客户端架构

```
LLMClient（接口）
  ├── MockLLMClient          # 离线/测试，确定性响应
  └── OpenAICompatibleLLMClient  # OpenAI 兼容协议
        ├── OpenAI
        ├── DeepSeek
        ├── 通义千问
        └── Ollama（本地部署）
```

通过 `pmis.literule.ai.llm-client` 配置切换，无需修改代码。
