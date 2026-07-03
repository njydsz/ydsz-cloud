"""
PMIS 规则引擎 HTTP 客户端

封装规则管理 REST API，提供同步调用方法。
"""

import json
from typing import Any, Optional
from urllib.request import Request, urlopen
from urllib.error import HTTPError, URLError

from .models import RuleDefinition, RuleResult, RuleHealthScore, RuleRecommendation


class PmisRuleClient:
    """PMIS 规则引擎 Python SDK 客户端"""

    def __init__(
        self,
        base_url: str = "http://localhost:8080",
        api_prefix: str = "/api/v1/rules",
        token: str = "",
        timeout: int = 30,
    ):
        """
        初始化客户端

        :param base_url: 后端服务地址
        :param api_prefix: 规则 API 前缀
        :param token: Bearer Token（JWT）
        :param timeout: 请求超时秒数
        """
        self.base_url = base_url.rstrip("/")
        self.api_prefix = api_prefix.rstrip("/")
        self.token = token
        self.timeout = timeout

    def _request(self, method: str, path: str, body: Any = None) -> dict:
        """发送 HTTP 请求"""
        url = f"{self.base_url}{self.api_prefix}{path}"
        headers = {"Content-Type": "application/json"}
        if self.token:
            headers["Authorization"] = f"Bearer {self.token}"

        data = None
        if body is not None:
            data = json.dumps(body).encode("utf-8")

        req = Request(url, data=data, headers=headers, method=method)
        try:
            with urlopen(req, timeout=self.timeout) as resp:
                raw = resp.read().decode("utf-8")
                return json.loads(raw) if raw else {}
        except HTTPError as e:
            body_text = e.read().decode("utf-8", errors="replace")
            raise RuntimeError(f"HTTP {e.code}: {body_text}") from e
        except URLError as e:
            raise RuntimeError(f"网络错误: {e.reason}") from e

    # ---------- 规则管理 ----------

    def list_rules(self) -> list:
        """查询全部规则"""
        resp = self._request("GET", "")
        return [RuleDefinition.from_dict(r) for r in resp.get("data", [])]

    def get_rule(self, rule_code: str) -> Optional[RuleDefinition]:
        """查询单条规则"""
        resp = self._request("GET", f"/{rule_code}")
        data = resp.get("data")
        return RuleDefinition.from_dict(data) if data else None

    def create_rule(self, rule: RuleDefinition) -> RuleDefinition:
        """创建规则"""
        resp = self._request("POST", "", body=rule.to_dict())
        return RuleDefinition.from_dict(resp.get("data", {}))

    def delete_rule(self, rule_code: str) -> bool:
        """删除规则"""
        self._request("DELETE", f"/{rule_code}")
        return True

    def toggle_rule(self, rule_code: str, enabled: bool) -> bool:
        """启用/禁用规则"""
        self._request("PUT", f"/{rule_code}/toggle", body={"enabled": enabled})
        return True

    # ---------- 规则评估 ----------

    def evaluate(self, context: dict) -> list:
        """
        评估规则（触发后返回结果列表）

        :param context: 规则上下文（变量名 → 值）
        :return: 触发的规则结果列表
        """
        resp = self._request("POST", "/dry-run", body=context)
        return [RuleResult.from_dict(r) for r in resp.get("data", [])]

    def dry_run(self, context: dict) -> list:
        """Dry-run 仿真（返回全部规则结果含未触发）"""
        resp = self._request("POST", "/dry-run", body=context)
        return [RuleResult.from_dict(r) for r in resp.get("data", [])]

    def validate_expression(self, expression: str) -> dict:
        """校验表达式"""
        resp = self._request("POST", "/validate-expression", body={"expression": expression})
        return resp.get("data", {})

    # ---------- AI 增强 ----------

    def nl2rule(self, natural_language: str) -> Optional[RuleDefinition]:
        """自然语言转规则"""
        resp = self._request("POST", "/ai/nl2rule", body={"naturalLanguage": natural_language})
        data = resp.get("data")
        return RuleDefinition.from_dict(data) if data else None

    def describe_rule(self, rule_code: str) -> Optional[str]:
        """AI 生成规则描述"""
        resp = self._request("GET", f"/{rule_code}/ai/describe")
        return resp.get("data")

    def optimize_expression(self, rule_code: str) -> Optional[str]:
        """AI 表达式优化建议"""
        resp = self._request("GET", f"/{rule_code}/ai/optimize")
        return resp.get("data")

    def health_score(self, rule_code: str) -> Optional[RuleHealthScore]:
        """规则健康度评分"""
        resp = self._request("GET", f"/{rule_code}/ai/health")
        data = resp.get("data")
        return RuleHealthScore.from_dict(data) if data else None

    def health_score_batch(self) -> list:
        """批量健康度评分"""
        resp = self._request("GET", "/ai/health-batch")
        return [RuleHealthScore.from_dict(r) for r in resp.get("data", [])]

    def recommend(self, rule_code: str) -> list:
        """规则推荐"""
        resp = self._request("GET", f"/{rule_code}/ai/recommend")
        return [RuleRecommendation.from_dict(r) for r in resp.get("data", [])]

    # ---------- 规则集市场 ----------

    def list_packs(self) -> list:
        """查询规则集市场"""
        resp = self._request("GET", "/packs")
        return resp.get("data", [])

    def install_pack(self, pack_code: str, version: str = "") -> dict:
        """安装规则集"""
        path = f"/packs/{pack_code}/install"
        body = {"version": version} if version else None
        resp = self._request("POST", path, body=body)
        return resp.get("data", {})

    # ---------- 统计 ----------

    def get_stats(self) -> dict:
        """获取规则引擎统计"""
        resp = self._request("GET", "/stats")
        return resp.get("data", {})
