"""
PMIS 规则引擎数据模型
"""

from dataclasses import dataclass, field
from typing import Any, Optional


@dataclass
class RuleDefinition:
    """规则定义"""
    code: str = ""
    name: str = ""
    category: str = ""
    category_path: str = ""
    owner: str = ""
    description: str = ""
    condition_expression: str = ""
    severity_expression: str = ""
    default_severity: str = "YELLOW"
    title_template: str = ""
    description_template: str = ""
    priority: int = 100
    enabled: bool = True
    scope: str = ""
    version: int = 1
    status: str = "PUBLISHED"
    canary_ratio: float = 0.0

    @classmethod
    def from_dict(cls, d: dict) -> "RuleDefinition":
        return cls(
            code=d.get("code", ""),
            name=d.get("name", ""),
            category=d.get("category", ""),
            category_path=d.get("categoryPath", ""),
            owner=d.get("owner", ""),
            description=d.get("description", ""),
            condition_expression=d.get("conditionExpression", ""),
            severity_expression=d.get("severityExpression", ""),
            default_severity=d.get("defaultSeverity", "YELLOW"),
            title_template=d.get("titleTemplate", ""),
            description_template=d.get("descriptionTemplate", ""),
            priority=d.get("priority", 100),
            enabled=d.get("enabled", True),
            scope=d.get("scope", ""),
            version=d.get("version", 1),
            status=d.get("status", "PUBLISHED"),
            canary_ratio=d.get("canaryRatio", 0.0),
        )

    def to_dict(self) -> dict:
        return {
            "code": self.code,
            "name": self.name,
            "category": self.category,
            "categoryPath": self.category_path,
            "owner": self.owner,
            "description": self.description,
            "conditionExpression": self.condition_expression,
            "severityExpression": self.severity_expression,
            "defaultSeverity": self.default_severity,
            "titleTemplate": self.title_template,
            "descriptionTemplate": self.description_template,
            "priority": self.priority,
            "enabled": self.enabled,
            "scope": self.scope,
            "version": self.version,
            "status": self.status,
            "canaryRatio": self.canary_ratio,
        }


@dataclass
class RuleResult:
    """规则评估结果"""
    rule_code: str = ""
    rule_name: str = ""
    triggered: bool = False
    severity: str = ""
    title: str = ""
    description: str = ""
    current_value: Any = None
    threshold: Any = None

    @classmethod
    def from_dict(cls, d: dict) -> "RuleResult":
        return cls(
            rule_code=d.get("ruleCode", ""),
            rule_name=d.get("ruleName", ""),
            triggered=d.get("triggered", False),
            severity=d.get("severity", ""),
            title=d.get("title", ""),
            description=d.get("description", ""),
            current_value=d.get("currentValue"),
            threshold=d.get("threshold"),
        )


@dataclass
class RuleHealthScore:
    """规则健康度评分"""
    rule_code: str = ""
    rule_name: str = ""
    score: float = 0.0
    level: str = ""
    hit_rate_score: float = 0.0
    error_rate_score: float = 0.0
    complexity_score: float = 0.0
    coverage_score: float = 0.0
    total_evaluations: int = 0
    hit_count: int = 0
    hit_rate: float = 0.0
    error_rate: float = 0.0
    suggestions: list = field(default_factory=list)

    @classmethod
    def from_dict(cls, d: dict) -> "RuleHealthScore":
        return cls(
            rule_code=d.get("ruleCode", ""),
            rule_name=d.get("ruleName", ""),
            score=d.get("score", 0.0),
            level=d.get("level", ""),
            hit_rate_score=d.get("hitRateScore", 0.0),
            error_rate_score=d.get("errorRateScore", 0.0),
            complexity_score=d.get("complexityScore", 0.0),
            coverage_score=d.get("coverageScore", 0.0),
            total_evaluations=d.get("totalEvaluations", 0),
            hit_count=d.get("hitCount", 0),
            hit_rate=d.get("hitRate", 0.0),
            error_rate=d.get("errorRate", 0.0),
            suggestions=d.get("suggestions", []),
        )


@dataclass
class RuleRecommendation:
    """规则推荐结果"""
    suggested_code: str = ""
    suggested_name: str = ""
    suggested_expression: str = ""
    suggested_severity: str = ""
    rationale: str = ""
    score: float = 0.0
    type: str = ""

    @classmethod
    def from_dict(cls, d: dict) -> "RuleRecommendation":
        return cls(
            suggested_code=d.get("suggestedCode", ""),
            suggested_name=d.get("suggestedName", ""),
            suggested_expression=d.get("suggestedExpression", ""),
            suggested_severity=d.get("suggestedSeverity", ""),
            rationale=d.get("rationale", ""),
            score=d.get("score", 0.0),
            type=d.get("type", ""),
        )
