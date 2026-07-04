# K8S · Kubernetes 部署

> Kustomize 形态的 K8S 部署资产,`base/` 公共 + `overlays/{dev,sit,uat,prod}/` 多环境差异
> 适用:生产 / 准生产(K8S 集群)
> 状态:**基础骨架就绪,可直接 `kubectl apply -k` 部署**

---

## 目录

1. [目录结构](#1-目录结构)
2. [快速开始](#2-快速开始)
3. [中间件策略](#3-中间件策略)
4. [多环境差异对照](#4-多环境差异对照)
5. [镜像构建与推送](#5-镜像构建与推送)
6. [生产环境加固](#6-生产环境加固)
7. [常见问题](#7-常见问题)
8. [待办与未来规划](#8-待办与未来规划)
9. [相关链接](#9-相关链接)

---

## 1. 目录结构

```
k8s/
├── base/                          # 公共 base(7 微服务 + namespace + configmap + secret)
│   ├── kustomization.yaml         # 镜像版本在这里集中维护
│   ├── namespace.yaml             # pmis 命名空间
│   ├── configmap-common.yaml      # 公共环境变量(Spring Cloud Nacos 等)
│   ├── secret-db.yaml             # 数据库密码(明文,生产请换 Sealed Secrets)
│   ├── gateway.yaml               # 9000  + Service
│   ├── system.yaml                # 9001
│   ├── userinfo.yaml              # 9002
│   ├── project.yaml               # 9003
│   ├── cronjob.yaml               # 9004
│   ├── workflow.yaml              # 9005
│   └── agent.yaml                 # 9006
└── overlays/
    ├── dev/                       # 开发(单实例 + DEBUG 日志 + namePrefix=dev-)
    ├── sit/                       # 系统集成(2 副本)
    ├── uat/                       # 用户验收(2-3 副本)
    └── prod/                      # 生产(多副本 + HPA + PDB + 严格资源)
```

---

## 2. 快速开始

### 2.1 前置条件

| 工具 | 版本 | 说明 |
|---|---|---|
| kubectl | ≥ 1.27 | K8S CLI |
| kustomize | ≥ 5.0 | 内置于 `kubectl apply -k` |
| K8S 集群 | ≥ 1.27 | 任意发行版(EKS / AKS / 阿里云 ACK / 自建) |
| metrics-server | latest | **PROD HPA 依赖** |
| 镜像仓库 | — | 推送 7 个 `ydsz-pmis/{gateway,system,...}` 镜像 |

### 2.2 一键部署

```bash
# 部署到开发环境
kubectl apply -k deploy/k8s/overlays/dev

# 部署到生产环境
kubectl apply -k deploy/k8s/overlays/prod

# 验证
kubectl -n pmis get pods
kubectl -n pmis get svc
kubectl -n pmis get hpa        # 仅 prod 有

# 卸载
kubectl delete -k deploy/k8s/overlays/prod
```

### 2.3 端口访问

| 服务 | Cluster Port | 访问方式 |
|---|---|---|
| gateway | 9000 | ClusterIP(配 Ingress) |
| system | 9001 | 仅内部调用 |
| userinfo | 9002 | 仅内部调用 |
| project | 9003 | 仅内部调用 |
| cronjob | 9004 | 仅内部调用 |
| workflow | 9005 | 仅内部调用 |
| agent | 9006 | 仅内部调用 |

**生产建议**:通过 Ingress → `gateway:9000` 暴露,不直接 NodePort 暴露后端。

---

## 3. 中间件策略

当前 K8S 模板**只包含 PMIS 7 个微服务**的部署,**不含 8 大中间件**。中间件需另行处理:

| 方案 | 适用 | 说明 |
|---|---|---|
| **云厂商托管** | 生产(推荐) | RDS / 阿里云 Redis / 阿里云 RocketMQ / 阿里云 ES |
| **Helm chart** | 自建 K8S | bitnami / 官方 chart |
| **复用本仓库 docker/** | 测试集群 | Docker Compose 不直接适用于 K8S,需转换 |

---

## 4. 多环境差异对照

| 维度 | dev | sit | uat | prod |
|---|---|---|---|---|
| 命名空间 | pmis-dev | pmis-sit | pmis-uat | pmis |
| 名称前缀 | dev- | sit- | uat- | — |
| gateway 副本 | 1 | 2 | 2 | **3 (HPA 3-10)** |
| project 副本 | 1 | 2 | 3 | **4 (HPA 4-12)** |
| agent 副本 | 1 | 1 | 1 | **2 (HPA 2-6)** |
| 镜像 tag | v1.3.0-SNAPSHOT | v1.3.0-rc.1 | v1.3.0-rc.2 | v1.3.0 |
| 日志级别 | DEBUG | INFO | INFO | INFO |
| 资源限制 | 弱 | 弱 | 中 | 强 |
| PDB | — | — | — | ✓ |
| HPA | — | — | — | ✓ |

---

## 5. 镜像构建与推送

### 5.1 修改镜像仓库地址

`base/kustomization.yaml` 默认 `ydsz-pmis/{gateway,system,...}`,需改为你的仓库:

```yaml
images:
  - name: ydsz-pmis/gateway
    newName: registry.cn-hangzhou.aliyuncs.com/your-org/ydsz-pmis-gateway
    newTag: v1.3.0
```

或者直接在 overlay 中覆盖:

```yaml
# overlays/prod/kustomization.yaml
images:
  - name: ydsz-pmis/gateway
    newName: registry.cn-hangzhou.aliyuncs.com/your-org/ydsz-pmis-gateway
    newTag: v1.3.0
```

### 5.2 构建并推送(7 个后端服务 + 1 个前端)

仓库根目录提供统一的多阶段 Dockerfile + 批量构建脚本：

```bash
# 方式 1：批量构建所有 7 个后端服务 + 前端（推荐）
bash deploy/scripts/build-images.sh v1.3.0 ydsz-pmis
# 或 PowerShell
.\deploy\scripts\build-images.ps1 -Tag v1.3.0 -Registry ydsz-pmis

# 方式 2：构建并推送到私有仓库
bash deploy/scripts/build-images.sh v1.3.0 registry.cn-hangzhou.aliyuncs.com/your-org
PUSH=true bash deploy/scripts/build-images.sh v1.3.0 registry.cn-hangzhou.aliyuncs.com/your-org

# 方式 3：单服务手动构建
docker build -t ydsz-pmis/gateway:v1.3.0 \
  --build-arg MODULE_NAME=ydsz-pmis-gateway \
  --build-arg APP_PORT=9000 \
  -f ydsz-pmis-backend/Dockerfile ydsz-pmis-backend/
```

**镜像构建特性**（对齐阿里/字节容器化规范）:
- 多阶段构建（Maven builder → JRE alpine runtime）
- 非 root 用户（pmis:65532）
- tini 作为 PID 1，正确处理 SIGTERM
- JVM 容器化参数（-XX:+UseContainerSupport + MaxRAMPercentage）
- BuildKit 缓存挂载加速构建
- Actuator 健康检查

详细说明见:
- [后端 Dockerfile](../../ydsz-pmis-backend/Dockerfile)
- [前端 Dockerfile](../../ydsz-pmis-frontend/Dockerfile)
- [批量构建脚本](../scripts/build-images.sh)

---

## 6. 生产环境加固

以下项**未在当前模板中**,部署到生产前必须补:

- [ ] **Secret 加密**:`base/secret-db.yaml` 当前是明文,生产用 [sealed-secrets](https://github.com/bitnami-labs/sealed-secrets) 或 [external-secrets](https://external-secrets.io/)
- [ ] **Ingress**:用 Ingress(nginx / traefik)对外暴露 gateway,不要 NodePort
- [ ] **NetworkPolicy**:限制 Pod 间通信,只允许必要的服务调用
- [ ] **ServiceAccount**:每个服务用独立 SA,配合 RBAC 最小权限
- [ ] **PodSecurityContext**:以非 root 运行,只读根文件系统
- [ ] **ResourceQuota + LimitRange**:命名空间级别资源约束
- [ ] **ImagePullPolicy = IfNotPresent** + 固定 tag(已配)
- [ ] **mTLS**:服务间通信加密(Istio / Linkerd)
- [ ] **证书管理**:cert-manager 自动签发 TLS 证书

---

## 7. 常见问题

| 现象 | 原因 | 解决 |
|---|---|---|
| `kustomize: command not found` | 旧版 kubectl | 升级到 ≥ 1.27 或独立装 kustomize |
| `error: unable to recognize "..."`: no matches for kind "Deployment" | 集群版本太低 | 升级 K8S 至 ≥ 1.27 |
| Pod 一直 `Pending` | 资源不足 / 节点选择器不匹配 | `kubectl describe pod` 看事件 |
| Pod `CrashLoopBackOff` | 应用启动失败 | `kubectl logs` + `kubectl describe` |
| HPA 报 `unable to fetch metrics` | metrics-server 未装 | `kubectl apply -f metrics-server.yaml` |
| `ImagePullBackOff` | 镜像仓库认证失败 | 配置 `imagePullSecrets` |

---

## 8. 待办与未来规划

- [x] 镜像构建 Dockerfile（已提供，见 `ydsz-pmis-backend/Dockerfile` 与 `ydsz-pmis-frontend/Dockerfile`）
- [x] Helm Chart（已提供，见 `deploy/helm/ydsz-pmis/`）
- [x] 冒烟测试脚本（已提供，见 `deploy/scripts/smoke-test.sh/.ps1`）
- [ ] Ingress 模板(各集群规范不同,留空)
- [ ] NetworkPolicy 模板
- [ ] cert-manager 集成
- [ ] ArgoCD Application manifest
- [ ] 中间件 Helm chart(可选)

---

## 9. 相关链接

- [deploy/ 总入口](../README.md)
- [common/](../common/README.md) · 共享配置(中间件 K8S 化时参考)
- [docker/](../docker/README.md) · 测试用容器编排(11 容器)
- [ubuntu/](../ubuntu/README.md) · [windows/](../windows/README.md) · 原生部署
- 8 中间件详细步骤见 [`../README.md §4`](../README.md#4-8-大中间件) + 各子目录 § 故障排查
