# PMIS Alertmanager 告警通知中枢

PMIS 监控告警的通知路由中枢,接收 Prometheus 触发的告警,按严重级别路由到不同通知渠道(电话语音 / 钉钉 / 邮件),并通过抑制规则避免告警风暴。

## 文件说明

| 文件 | 作用 |
|------|------|
| `alertmanager.yml` | 主配置:路由树、抑制规则、接收器定义 |
| `dingtalk-template.tmpl` | 钉钉通知消息模板(markdown 格式) |
| `../prometheus/rules/pmis-alerts.yml` | 告警规则(PrometheusRule CRD,12 条规则) |

## 告警分级路由

对应 `pmis-alerts.yml` 中 `labels.severity`(值为大写 `P0`/`P1`/`P2`):

| 级别 | 通知渠道 | 聚合窗口 | 重复间隔 | 典型告警 |
|------|----------|----------|----------|----------|
| **P0** | 电话语音 + 钉钉 | 0s(立即) | 30m | 服务下线、DB 连接池耗尽、DB 连接失败 |
| **P1** | 钉钉 | 5m | 2h | 5xx 错误率激增、JVM OOM、Full GC 频繁 |
| **P2** | 邮件 | 30m | 6h | 性能劣化、资源水位、RocketMQ 堆积 |

## 抑制规则

避免连锁告警刷屏:

1. **服务下线抑制 5xx 告警**:`PmisServiceDown` 触发时,抑制同一 `job` 的 `PmisHighErrorRate`(实例不可达必然导致请求失败)
2. **DB 连接失败抑制依赖告警**:`PmisDbConnectionFailed` 触发时,抑制同一 `job` 的 `PmisDbPoolExhausted`/`PmisBusinessMetricsStale`/`PmisHighErrorRate`
3. **高级别抑制低级别**:P0/P1 触发时抑制同一 `alertname`+`job` 的 P2 告警

## 分组策略

- 按 `alertname` + `job` 分组,同一告警同一服务聚合为一条通知
- 全局 `group_wait: 30s` / `group_interval: 5m` / `repeat_interval: 4h`

## 部署前必做:替换占位符

`alertmanager.yml` 中以下占位符需在部署前通过环境变量注入实际值:

| 环境变量 | 说明 | 默认占位符 |
|----------|------|------------|
| `DINGTALK_WEBHOOK_URL` | 钉钉自定义机器人 webhook | `http://dingtalk-webhook:8060/dingtalk/webhook1/send` |
| `VOICE_WEBHOOK_URL` | 电话语音通知服务 webhook(阿里云语音/容联云) | `http://voice-alert:8060/api/call` |
| `SMTP_SMARTHOST` | SMTP 服务器地址 | `smtp.qiye.aliyun.com:465` |
| `SMTP_FROM` | 发件人邮箱 | `pmis-alert@ydsz.cn` |
| `SMTP_USER` | SMTP 账号 | `pmis-alert@ydsz.cn` |
| `SMTP_PASSWORD` | SMTP 密码 | `CHANGE_ME` |
| `ALERT_EMAIL_TO` | 告警收件人邮箱 | `pmis-sre@ydsz.cn` |

## 部署方式

### 方式一:Docker Compose(开发环境)

`deploy/docker/docker-compose.dev.yml` 已配置 `alertmanager` 服务,自动挂载本目录文件:

```bash
# 启动(需先设置环境变量)
export DINGTALK_WEBHOOK_URL="https://oapi.dingtalk.com/robot/send?access_token=实际token"
export VOICE_WEBHOOK_URL="https://voice-alert.example.com/api/call"
export SMTP_PASSWORD="实际邮箱密码"
docker compose -f deploy/docker/docker-compose.dev.yml up -d alertmanager
```

访问 Web UI: http://localhost:9093

### 方式二:Kubernetes(生产环境)

使用 kube-prometheus-stack Helm Chart 部署的 Alertmanager,通过 Secret 挂载配置:

```bash
# 1. 创建 Secret(用 envsubst 渲染占位符后)
envsubst < deploy/monitoring/alertmanager/alertmanager.yml > /tmp/alertmanager.yml
kubectl create secret generic alertmanager-config \
  --from-file=alertmanager.yml=/tmp/alertmanager.yml \
  -n monitoring

# 2. 创建 ConfigMap(钉钉模板)
kubectl create configmap alertmanager-templates \
  --from-file=deploy/monitoring/alertmanager/dingtalk-template.tmpl \
  -n monitoring

# 3. 在 Prometheus CR 中引用该 Secret
# (kube-prometheus-stack 默认已挂载 alertmanager-config Secret)
```

### 方式三:验证配置

部署前用 `amtool` 校验配置语法:

```bash
amtool check-config deploy/monitoring/alertmanager/alertmanager.yml
```

## 通知渠道接入

### 钉钉机器人

1. 在钉钉群创建自定义机器人,获取 webhook URL 与加签密钥
2. 设置环境变量 `DINGTALK_WEBHOOK_URL`
3. 消息模板见 `dingtalk-template.tmpl`(markdown 格式,P0 告警 @ 全员)

### 电话语音通知

P0 告警通过 webhook 调用电话语音通知服务,推荐方案:

- **阿里云语音通知**:配置语音通知模板,webhook 触发呼叫值班手机
- **容联云通讯**:通过 API 发起语音呼叫
- 自建服务:接收 Alertmanager webhook payload,调用运营商 API 呼叫

### 邮件通知

配置企业邮箱 SMTP(默认阿里云企业邮箱),P2 告警发送至 `ALERT_EMAIL_TO`。

## 相关文件

- 告警规则: [`../prometheus/rules/pmis-alerts.yml`](../prometheus/rules/pmis-alerts.yml)
- Prometheus 配置: [`../prometheus/prometheus.yml`](../prometheus/prometheus.yml)
- ServiceMonitor(抓取配置): [`../../k8s/base/servicemonitor.yaml`](../../k8s/base/servicemonitor.yaml)
