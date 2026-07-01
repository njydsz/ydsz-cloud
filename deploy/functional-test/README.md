# PMIS API 契约测试（批次 19 集成 Newman）

> 本目录是 PMIS 14 微服务全量接口契约测试集合，基于 [OpenAPI 3 规范](../../docs/api/openapi-summary.json) 派生。

## 文件清单

| 文件 | 说明 |
|------|------|
| [pmis-contract.postman_collection.json](./pmis-contract.postman_collection.json) | Postman v2.1.0 集合，覆盖 7 大模块 30+ 端点 |
| [pmis-contract.postman_environment.json](./pmis-contract.postman_environment.json) | 环境变量（BASE_URL 等） |
| [run-api-tests.sh](./run-api-tests.sh) | 主入口脚本：先跑 OpenAPI 摘要契约验证，再调用 Newman 跑 Postman 集合 |
| [uat-checklist.md](./uat-checklist.md) | UAT 验收清单 220 用例 |

## 用法

### 1. 仅跑 curl/jq 版契约测试（不依赖 Newman）

```bash
PMIS_GATEWAY=http://localhost:9000 \
PMIS_TEST_USER=admin \
PMIS_TEST_PASSWORD=admin123 \
./run-api-tests.sh
```

### 2. 跑全量 Newman 契约测试（推荐）

```bash
# 安装 Newman
npm install -g newman

# 跑测
PMIS_GATEWAY=http://localhost:9000 \
./run-api-tests.sh
```

HTML 报告输出到 `/tmp/pmis-newman-report.html`。

### 3. 仅跑 Newman（跳过 curl 阶段）

```bash
newman run pmis-contract.postman_collection.json \
  -e pmis-contract.postman_environment.json \
  --env-var "BASE_URL=http://localhost:9000" \
  --reporters cli,html \
  --reporter-htmlexport /tmp/pmis-newman.html
```

## 覆盖端点

| 模块 | 端点数 | 关键路径 |
|------|--------|----------|
| auth | 4 | /auth/login /auth/refresh /auth/me /auth/logout |
| user | 4 | /user/page /user/dept/tree /user/role/page /user/permission/tree |
| project | 5 | /project/opportunity/page /project/initiation/page /project/contract/page /project/change/page /project/contract-template/page |
| execution | 7 | /execution/wbs-task/page /execution/time-entry/page /execution/invoice/page /execution/payment/page /execution/closure/page /execution/warranty/page /execution/ops-ticket/page |
| execution-报表 | 5 | /execution/cockpit/overview /cockpit/executive /cockpit/alerts /report/project-profit /report/lifecycle-ledger |
| agent | 4 | /agent/list /agent/orchestration/modes /agent/orchestration/agents /agent/page |
| workflow/notification/audit | 3 | /workflow/business/page /notification/page /audit/operation-log/page |
| **合计** | **32** | — |

## 验收依据

- [开发计划 11.1 节](../../开发计划.md) 功能验收：**15 个核心模块全部开发完成并通过功能测试**
- 本目录对应验收项：**全量接口契约测试（基于 OpenAPI）**

## CI 集成

在 GitLab CI / Jenkins 中加入：

```yaml
contract-test:
  stage: test
  image: node:20
  before_script:
    - npm install -g newman
  script:
    - cd deploy/functional-test
    - PMIS_GATEWAY=http://pmis-staging:9000 ./run-api-tests.sh
  artifacts:
    when: always
    paths:
      - /tmp/pmis-newman-report.html
    expire_in: 30 days
```
