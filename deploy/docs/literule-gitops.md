# LiteRule GitOps 工作流（P2-11 规则即代码）

> 将规则配置纳入 Git 版本管理，通过 CI 自动校验与 Webhook 发布，实现"规则即代码"。

## 工作流概览

```
┌─────────────┐    定时拉取     ┌──────────────┐    提交     ┌────────────────┐
│  规则引擎 DB │ ────────────→ │ YAML 导出接口 │ ─────────→ │  Git 仓库      │
└─────────────┘   /export.yaml └──────────────┘            └────────────────┘
                                                                  │
                                                                  │ PR 审核
                                                                  ▼
┌─────────────┐   Webhook      ┌──────────────┐    合并     ┌────────────────┐
│  规则引擎 DB │ ←──────────── │  /import     │ ←───────── │  CI 校验+发布   │
└─────────────┘                └──────────────┘            └────────────────┘
```

## 1. 导出规则到 Git

```bash
# 定时从规则引擎导出 YAML（建议每小时一次）
curl -s http://project-service:8080/execution/rules/export.yaml > rules.yaml

# 提交到 Git
git add rules.yaml
git commit -m "chore(rules): sync rule definitions $(date +%Y%m%d-%H%M)"
git push origin main
```

## 2. CI 校验（GitHub Actions / GitLab CI 示例）

`.github/workflows/rule-validate.yml`：

```yaml
name: Rule Validate
on:
  pull_request:
    paths:
      - 'rules.yaml'
jobs:
  validate:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: 校验规则冲突
        run: |
          curl -s -X GET http://rule-engine:8080/execution/rules/conflicts | jq .
      - name: 回归测试
        run: |
          curl -s -X POST http://rule-engine:8080/execution/rules/test-cases/batch-run \
            -H "Content-Type: application/json" -d '{"ids":[]}' | jq .
```

## 3. 合并后自动发布

PR 审核通过并合并到 main 后，通过 Webhook 触发导入：

```bash
curl -X POST http://rule-engine:8080/execution/rules/import \
  -H "Content-Type: application/json" \
  -H "X-Operator: gitops-ci" \
  -d @<(python3 scripts/yaml_to_json.py rules.yaml)
```

## 4. 审计与回滚

- **审计**：所有规则变更通过 Git commit 记录，含变更人、变更说明、diff
- **回滚**：`git revert <commit>` 后重新触发 Webhook 即可回滚规则
- **版本对齐**：规则的 `version` 字段与 Git tag 对应，便于追溯

## 环境隔离

| 环境 | Git 分支 | 行为 |
|------|---------|------|
| 开发 | `dev` | 自由修改，不触发 CI 校验 |
| 预发 | `release` | PR 审核 + 冲突检测 + 回归测试 |
| 生产 | `main` | PR 审核 + 全量校验 + Webhook 自动发布 |
