# 冒烟测试与灾备目录（批次 19 补全）

PMIS 生产环境上线前必备的端到端验证产物。

## 目录结构

```
deploy/smoke-test/
├── run.sh         # 全链路冒烟测试（14 微服务 + DB + Redis + Nginx）
├── feign-test.sh  # 跨服务 Feign 调用链验证（含 fallback 降级）
├── dr-sop.md      # 灾备演练 SOP（4 类场景 + 自动化脚本）
└── README.md      # 本文件
```

## 快速使用

### 1. 部署后冒烟测试

```bash
chmod +x run.sh feign-test.sh

# 默认连 localhost
./run.sh

# 指定环境
./run.sh https://staging.pmis.example.com
./run.sh http://10.0.1.11:9000
```

### 2. 跨服务调用链验证

```bash
./feign-test.sh http://localhost
```

### 3. 灾备演练

参见 [dr-sop.md](./dr-sop.md)。**每季度一次**，记录到 `dr-record.xlsx`。

## run.sh 覆盖范围

| 类别 | 检查项 |
|------|--------|
| 网关 | pmis-gateway 路由 |
| 12 个微服务 | 各自的 /actuator/health |
| 业务接口 | 立项分页、合同查询 |
| 依赖 | PostgreSQL / Redis / Nacos |
| Nginx | 自检 /health |
| HTTPS | 证书有效期（生产） |

## feign-test.sh 覆盖范围

| 编号 | 调用链 | 验证点 |
|------|--------|--------|
| 1 | 立项→合同 | project→contract Feign |
| 2 | 合同→发票 | contract→invoice Feign |
| 3 | 立项→工时 | initiation→time-entry Feign |
| 4 | 立项→AI 评估 | project→agent Feign |
| 5 | 立项→通知 | 事件驱动（异步） |
| 6 | 立项→审计 | 异步审计 |
| 7 | 立项→驾驶舱 | 多服务聚合 |
| 8 | Fallback 降级 | 服务挂掉时返回 0 |
| 9 | TraceId 透传 | X-Request-ID 一致性 |

## 通过标准

- ✅ **冒烟测试**：14 个微服务全部 200，无 5xx
- ✅ **Feign 测试**：9 个调用链全部成功，fallback 接口在依赖挂掉时返回 0
- ✅ **灾备演练**：RTO ≤ 30min，RPO ≤ 5min

## 与 CI/CD 集成

```yaml
# Jenkins / GitLab CI 流水线
stages:
  - build
  - deploy-staging
  - smoke-test    # 部署到 staging 后自动跑
  - chaos-test    # 高级：故障注入
  - promote-prod

smoke-test:
  stage: smoke-test
  script:
    - sleep 30  # 等待服务启动
    - ./deploy/smoke-test/run.sh http://staging.pmis.example.com
    - ./deploy/smoke-test/feign-test.sh http://staging.pmis.example.com
  only:
    - develop
    - main
```

## 故障排查速查

| 现象 | 排查命令 |
|------|----------|
| 健康检查 503 | `curl /actuator/health` 查看具体组件 |
| Feign 调用 500 | `tail -f /var/log/pmis/project.log` |
| 数据库慢 | `pmis=# SELECT * FROM pg_stat_activity WHERE state='active';` |
| Redis 槽丢失 | `redis-cli --cluster fix redis-1:6379` |
| 限流 429 | `nginx -T | grep limit_req` |
