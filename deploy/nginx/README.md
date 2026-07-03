# Nginx 反向代理与限流（批次 19 补全）

PMIS 生产环境 Nginx 1.25 的反向代理、限流、HTTPS 配置。

## 目录结构

```
deploy/nginx/
├── nginx.conf           # 主配置（事件/连接/限流/上游）
├── conf.d/pmis.conf     # 站点配置（443 路由 + 14 个上游微服务）
├── certbot-cron         # certbot 续期定时任务
└── README.md            # 本文件
```

## 7 个微服务路由 (2026-07-03 服务合并后)

| 路径前缀 | 上游服务 | 端口 | 备注 |
|----------|----------|------|------|
| `/` | pmis_gateway | 9000 | 网关（统一鉴权） |
| `/api/v1/file/` `/api/v1/audit/` `/api/v1/notification/` `/api/v1/message/` | pmis_system_cluster | 9001 | 系统基础服务（file + config + audit + notification + message 合并） |
| `/api/v1/user/` `/api/v1/auth/` `/api/v1/role/` `/api/v1/dept/` | pmis_userinfo_cluster | 9002 | 用户信息中心（user + auth 合并） |
| `/api/v1/project/` `/api/v1/execution/` `/api/v1/finance/` | pmis_project_cluster | 9003 | 项目 + 执行 + 财务（合并） |
| `/api/v1/cronjob/` `/api/v1/job/` | pmis_cronjob_cluster | 9004 | 调度（XXL-JOB） |
| `/api/v1/workflow/` | pmis_workflow_cluster | 9005 | 工作流 |
| `/api/v1/agent/` | pmis_agent_cluster | 9006 | AI Agent |
| `/ws/` | pmis_gateway | 9000 | WebSocket（1h 长连接） |
| `/static/` | 本地 | — | 前端静态资源 |
| `/health` | — | — | 健康检查（不计入限流） |

## 关键配置

### HTTPS
- TLS 1.2/1.3 only
- 6 套 ECDHE 套件（禁用 CBC / RC4 / 3DES）
- OCSP Stapling
- HSTS preload

### 限流
- 单 IP：100 r/s（限速）/ 1000 r/s（突发）
- 全局：10000 r/s
- 单 IP 连接数：50

### 转发
- `X-Real-IP` / `X-Forwarded-For` / `X-Forwarded-Proto`
- `X-Request-ID`（链路追踪）
- WebSocket Upgrade 支持（1h 超时）

### 安全 Header
- HSTS preload
- CSP（self + 内联）
- X-Frame-Options SAMEORIGIN
- X-Content-Type-Options nosniff
- Referrer-Policy strict-origin

## 部署

```bash
# 1. 安装 Nginx 1.25
apt install nginx=1.25.*

# 2. 部署配置
cp deploy/nginx/nginx.conf /etc/nginx/nginx.conf
cp deploy/nginx/conf.d/pmis.conf /etc/nginx/conf.d/pmis.conf

# 3. 生成 DH params
openssl dhparam -out /etc/nginx/dhparam.pem 2048

# 4. 申请 certbot 证书
certbot certonly --nginx -d pmis.example.com --email ops@ydsz-pmis.cn

# 5. 安装续期任务
cp deploy/nginx/certbot-cron /etc/cron.d/certbot-renew

# 6. 重启并验证
nginx -t
systemctl reload nginx
curl -I https://pmis.example.com/health
```

## 验证

```bash
# 限流测试
for i in $(seq 1 200); do curl -s https://pmis.example.com/health & done
# 期望：约 100 个 200，其余 429

# HTTPS 安全 Header
curl -I https://pmis.example.com/
# 期望：Strict-Transport-Security / X-Frame-Options / X-Content-Type-Options

# 上游健康度
curl -s http://10.0.1.11:9000/actuator/health
```

## 性能基线

| 指标 | 目标值 |
|------|--------|
| 单实例 TPS | 5000+ r/s |
| P99 延迟 | < 50ms（静态）/ < 200ms（API） |
| 长连接 | WebSocket 10k+ 并发 |
