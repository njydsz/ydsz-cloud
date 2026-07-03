# CLI 工具

PMIS 规则引擎命令行工具，基于 `argparse` 子命令模式，支持规则管理/评估/AI 增强/规则集市场。

## 安装

```bash
cd sdk/python
pip install -e .
```

安装后全局可用 `pmis-rule-cli` 命令。

## 全局参数

```bash
pmis-rule-cli [全局参数] <子命令> [子命令参数]
```

| 参数 | 环境变量 | 默认值 | 说明 |
|------|---------|--------|------|
| `--base-url` | `PMIS_BASE_URL` | `http://localhost:9000` | PMIS 网关地址 |
| `--token` | `PMIS_TOKEN` | 空 | JWT 令牌 |
| `--timeout` | - | 15 | 请求超时（秒） |

::: tip 环境变量
推荐通过环境变量配置 `PMIS_BASE_URL` 和 `PMIS_TOKEN`，避免每次输入。
:::

## 子命令

### list - 列出全部规则

```bash
pmis-rule-cli list
```

输出示例：

```
CODE                NAME                CATEGORY   SEVERITY  STATUS     ENABLED
EVM_RED_ALERT       EVM红色预警          finance    RED       PUBLISHED  true
EVM_YELLOW_ALERT    EVM黄色预警          finance    YELLOW    PUBLISHED  true
BENCH_IDLE          Bench闲置预警        resource   YELLOW    PUBLISHED  true
```

### get - 查看规则详情

```bash
pmis-rule-cli get EVM_RED_ALERT
```

### evaluate - 评估规则

```bash
# 评估全部规则
pmis-rule-cli evaluate --fact evmRedCount=5 --fact evmYellowCount=3

# 评估指定规则
pmis-rule-cli evaluate --rule EVM_RED_ALERT --fact evmRedCount=5
```

输出示例：

```
[EVM_RED_ALERT] triggered=true severity=RED
  Title: EVM 红色预警阈值触发
  Current: evmRedCount=5
  Threshold: >= 3
  Elapsed: 12ms
```

### nl2rule - 自然语言转规则

```bash
pmis-rule-cli nl2rule "当 EVM 红色预警数大于等于 3 时触发红色严重告警"
```

输出示例：

```json
{
  "code": "AI_GEN_xxx",
  "conditionExpression": "evmRedCount >= 3",
  "defaultSeverity": "RED",
  "description": "当 EVM 红色预警数大于等于 3 时触发"
}
```

### health - 规则健康度评分

```bash
# 单条评分
pmis-rule-cli health EVM_RED_ALERT

# 批量评分
pmis-rule-cli health --batch
```

输出示例：

```
EVM_RED_ALERT: 85.5 [GOOD]
  命中率: 90.0 | 错误率: 100.0 | 复杂度: 75.0 | 覆盖率: 80.0
  建议:
    - 命中率 8.0% 处于健康区间
    - 表达式复杂度适中
```

### recommend - 规则推荐

```bash
pmis-rule-cli recommend EVM_RED_ALERT
```

输出示例：

```
[FIELD_COMPLETION] score=0.85
  建议编码: EVM_RED_ALERT_V2
  原因: 高频字段 evmYellowCount 未在源规则中出现
  建议表达式: evmRedCount >= 3 && evmYellowCount >= 5

[VARIANT] score=0.60
  建议编码: EVM_RED_ALERT_LOOSE
  原因: 命中率 <1%，建议生成宽松阈值变体
  建议表达式: evmRedCount > 2
```

### install-pack - 安装规则集

```bash
# 安装最新版本
pmis-rule-cli install-pack FINANCE_PACK

# 安装指定版本
pmis-rule-cli install-pack FINANCE_PACK --version 1.0.0
```

输出示例：

```
安装 FINANCE_PACK v1.0.0 成功
  导入: 15 条
  跳过: 2 条
  详情:
    + FINANCE_001
    + FINANCE_002
    ...
```

## 使用示例

### CI/CD 集成

在 CI 流水线中校验规则表达式：

```bash
# 校验表达式
pmis-rule-cli evaluate --rule EVM_RED_ALERT --fact evmRedCount=5 \
  && echo "规则校验通过" \
  || exit 1
```

### 批量健康检查

```bash
# 导出全部规则健康度报告
pmis-rule-cli health --batch > health-report.txt
```

### 快速生成规则

```bash
# 从自然语言生成规则，保存到文件
pmis-rule-cli nl2rule "当项目预算超支 20% 时触发黄色预警" > new-rule.json
```
