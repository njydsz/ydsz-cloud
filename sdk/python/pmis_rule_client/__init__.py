"""
PMIS 规则引擎 Python SDK

提供规则管理、评估、AI 增强、健康度评分等 API 的 Python 客户端。

使用示例：
    from pmis_rule_client import PmisRuleClient

    client = PmisRuleClient(base_url="http://localhost:8080")
    rules = client.list_rules()
    result = client.evaluate({"amount": 1000, "level": 3})
"""

from .client import PmisRuleClient
from .models import RuleDefinition, RuleResult, RuleHealthScore, RuleRecommendation

__version__ = "1.0.0"
__all__ = [
    "PmisRuleClient",
    "RuleDefinition",
    "RuleResult",
    "RuleHealthScore",
    "RuleRecommendation",
]
