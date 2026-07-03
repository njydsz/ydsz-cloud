# Python SDK

PMIS 规则引擎 Python SDK，基于 `urllib` 标准库实现，零第三方依赖。

## 安装

### 从源码安装

```bash
cd sdk/python
pip install -e .
```

### 直接使用

将 `sdk/python/pmis_rule_client` 目录加入 `PYTHONPATH` 即可直接 import。

## 快速开始

```python
from pmis_rule_client import PmisRuleClient, RuleDefinition

# 初始化客户端
client = PmisRuleClient(
    base_url="http://localhost:9000",
    token="your-jwt-token",
    timeout=15
)

# 查询全部规则
rules = client.list_rules()
for rule in rules:
    print(rule.code, rule.name, rule.condition_expression)

# 评估规则
results = client.evaluate({"evmRedCount": 5, "evmYellowCount": 3})
for r in results:
    if r.triggered:
        print(f"[{r.severity}] {r.rule_code}: {r.title}")
```

## 数据模型

### RuleDefinition

```python
@dataclass
class RuleDefinition:
    code: str                    # 规则编码
    name: str                    # 规则名
    category: str                # 分类
    description: str             # 描述
    condition_expression: str    # 条件表达式
    severity_expression: str     # 严重度表达式
    default_severity: str        # 默认严重度（INFO/YELLOW/RED）
    title_template: str          # 标题模板
    description_template: str    # 描述模板
    priority: int                # 优先级（0-100）
    scope: str                   # 影响范围
    status: str                  # 状态（DRAFT/REVIEW/PUBLISHED/ARCHIVED）
    enabled: bool                # 是否启用
    version: int                 # 版本号
```

### RuleResult

```python
@dataclass
class RuleResult:
    rule_code: str          # 规则编码
    rule_name: str          # 规则名
    triggered: bool         # 是否触发
    severity: str           # 严重度
    title: str              # 标题
    description: str        # 详细描述
    current_value: str      # 当前值
    threshold: str          # 阈值
    elapsed_ms: int         # 评估耗时
    canary: bool            # 是否灰度候选
```

### RuleHealthScore

```python
@dataclass
class RuleHealthScore:
    rule_code: str
    total_score: float      # 0-100
    level: str              # EXCELLENT/GOOD/WARN/BAD
    hit_rate_score: float
    error_rate_score: float
    complexity_score: float
    coverage_score: float
    suggestions: list       # 建议列表
```

### RuleRecommendation

```python
@dataclass
class RuleRecommendation:
    type: str               # FIELD_COMPLETION/PATTERN_DUPLICATION/VARIANT/SPLIT_SUGGESTION
    score: float            # 0-1
    suggested_rule_code: str
    reason: str
    suggested_expression: str
```

## API 方法

### 规则管理

```python
# 查询全部规则
rules = client.list_rules()

# 查询单条规则
rule = client.get_rule("EVM_RED_ALERT")

# 创建/更新规则
new_rule = RuleDefinition(
    code="NEW_RULE",
    name="新规则",
    condition_expression="value > 100",
    default_severity="YELLOW"
)
client.create_rule(new_rule, operator="admin", change_desc="初始化")

# 切换启停
client.toggle_rule("NEW_RULE", enabled=True, operator="admin")

# 删除规则
client.delete_rule("NEW_RULE", operator="admin")
```

### 规则评估

```python
# 评估规则
results = client.evaluate(
    facts={"evmRedCount": 5, "projectBudget": 100000},
    rule_code=None  # None 评估全部启用规则
)

# Dry-run 仿真（不产生副作用）
results = client.dry_run(
    facts={"evmRedCount": 5},
    rule_code="EVM_RED_ALERT"  # 可选，指定单条规则
)

# 校验表达式
is_valid = client.validate_expression("evmRedCount >= 3")
```

### AI 增强

```python
# 自然语言转规则
rule = client.nl2rule("当 EVM 红色预警数大于等于 3 时触发红色严重告警")

# 规则描述
desc = client.describe_rule("EVM_RED_ALERT")

# 表达式优化建议
suggestion = client.optimize_expression("EVM_RED_ALERT")

# 健康度评分
score = client.health_score("EVM_RED_ALERT")
print(f"{score.level}: {score.total_score}")

# 批量健康度评分
scores = client.health_score_batch()

# 规则推荐
recs = client.recommend("EVM_RED_ALERT")
for r in recs:
    print(f"[{r.type}] {r.reason}")
```

### 规则集市场

```python
# 列出全部规则集
packs = client.list_packs()

# 安装规则集
result = client.install_pack("FINANCE_PACK", version="1.0.0")
print(f"导入 {result.imported} 条，跳过 {result.skipped} 条")
```

### 执行统计

```python
stats = client.get_stats()
print(f"总评估 {stats.total_evaluations} 次")
print(f"总触发 {stats.total_triggered} 次")
print(f"总错误 {stats.total_errors} 次")
```

## 配置

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `base_url` | str | 必填 | PMIS 网关地址 |
| `token` | str | None | JWT 令牌 |
| `timeout` | int | 15 | 请求超时（秒） |

也支持环境变量：

```bash
export PMIS_BASE_URL=http://localhost:9000
export PMIS_TOKEN=your-jwt-token
```

```python
# 从环境变量初始化
client = PmisRuleClient.from_env()
```
