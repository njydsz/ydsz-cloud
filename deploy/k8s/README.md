# K8S 部署 · Kustomize

> Kubernetes 部署资产,使用 Kustomize 管理多环境(base + overlays)
> 适用:生产环境 / 准生产环境(若有 K8S 集群)
> 形态:骨架已就绪,可直接 `kubectl apply -k` 部署

---

## 目录结构

```
deploy/k8s/
├── base/                  # 公共 base(7 服务 + namespace + configmap + secret)
│   ├── kustomization.yaml
│   ├── namespace.yaml
│   ├── configmap-common.yaml
│   ├── secret-db.yaml
│   ├── gateway.yaml       # 9000
│   ├── system.yaml        # 9001
│   ├── userinfo.yaml      # 9002
│   ├── project.yaml       # 9003
│   ├── cronjob.yaml       # 9004
│   ├── workflow.yaml      # 9005
│   └── agent.yaml         # 9006
├── overlays/
│   ├── dev/               # 开发(单实例 + DEBUG 日志)
│   ├── sit/               # 系统集成测试(2 副本)
│   ├── uat/               # 用户验收(2-3 副本)
│   └── prod/              # 生产(多副本 + HPA + PDB + 严格资源)
```

## 前置条件

| 工具 | 版本 | 用途 |
|---|---|---|
| kubectl | ≥ 1.27 | K8S CLI |
| kustomize | ≥ 5.0 | 内置于 kubectl `apply -k` |
| K8S 集群 | ≥ 1.27 | 任意发行版(EKS/AKS/自建) |
| metrics-server | latest | PROD HPA 依赖 |
| 镜像仓库 | — | 推送 `ydsz-pmis/{gateway,system,...}` 7 个镜像 |

> 注:`base/kustomization.yaml` 中 `images.newTag` 占位为 `IMAGE_TAG`,overlay 已设具体版本(如 `v1.3.0-SNAPSHOT`),实际打包需修改。

## 中间件

当前 K8S 模板只包含 **PMIS 7 个微服务** 的部署。**8 大中间件**(PostgreSQL/Redis/Nacos/MinIO/Seata/RocketMQ/XXL-Job/ES)推荐:

| 方案 | 适用 | 说明 |
|---|---|---|
| **云厂商托管** | 生产 | RDS / 阿里云 Redis / 阿里云 RocketMQ / 阿里云 ES |
| **Helm chart** | 自建 K8S | bitnami / 官方 chart |
| **复用本仓库的 docker/** | 测试集群 | Docker Compose 不直接适用于 K8S,需转换为 Deployment |

## 部署命令

### 1. 构建并推送镜像(每个微服务)

```bash
# 在 ydsz-pmis-backend 目录
mvn -pl ydsz-pmis-gateway -am clean package -DskipTests
docker build -t <REGISTRY>/ydsz-pmis/gateway:v1.3.0 -f ydsz-pmis-gateway/Dockerfile .
docker push <REGISTRY>/ydsz-pmis/gateway:v1.3.0
# 重复 7 次
```

### 2. 修改镜像地址

`base/kustomization.yaml` 的 `images.newName` 改为你的镜像仓库:

```yaml
images:
  - name: ydsz-pmis/gateway
    newName: registry.cn-hangzhou.aliyuncs.com/your-org/ydsz-pmis-gateway
    newTag: v1.3.0
```

### 3. 一键部署

```bash
# 开发
kubectl apply -k deploy/k8s/overlays/dev

# 生产
kubectl apply -k deploy/k8s/overlays/prod

# 验证
kubectl -n pmis get pods
kubectl -n pmis get svc
```

### 4. 卸载

```bash
kubectl delete -k deploy/k8s/overlays/prod
```

## 端口映射

| 服务 | Cluster Port | NodePort (示例) | 用途 |
|---|---|---|---|
| gateway | 9000 | 30000 | 外部入口(配 Ingress) |
| system | 9001 | — | 内部 |
| userinfo | 9002 | — | 内部 |
| project | 9003 | — | 内部 |
| cronjob | 9004 | — | 内部 |
| workflow | 9005 | — | 内部 |
| agent | 9006 | — | 内部 |

> 生产建议通过 **Ingress + gateway Service(9000)** 暴露,不直接 NodePort 暴露后端服务。

## 多环境差异对照

| 维度 | dev | sit | uat | prod |
|---|---|---|---|---|
| 命名空间 | pmis-dev | pmis-sit | pmis-uat | pmis |
| 副本数(gateway) | 1 | 2 | 2 | 3 (HPA 3-10) |
| 副本数(project) | 1 | 2 | 3 | 4 (HPA 4-12) |
| 镜像 tag | SNAPSHOT | rc.1 | rc.2 | v1.3.0 |
| 日志级别 | DEBUG | INFO | INFO | INFO |
| 资源限制 | 弱 | 弱 | 中 | 强 |
| PDB | — | — | — | ✓ |
| HPA | — | — | — | ✓ |
| 名称前缀 | dev- | sit- | uat- | — |

## 安全提示

1. **Secret 加密**:`base/secret-db.yaml` 用的是明文,生产请用 [sealed-secrets](https://github.com/bitnami-labs/sealed-secrets) 或 [external-secrets](https://external-secrets.io/)
2. **NetworkPolicy**:建议加上,限制 pod 间通信
3. **ServiceAccount**:每个服务用独立 SA,配合 RBAC
4. **ImagePullPolicy**:`IfNotPresent` 配合固定 tag 部署

## 状态

- [x] base 完整(7 服务 + 公共资源)
- [x] overlays/dev/sit/uat/prod 已就位
- [x] prod 包含 HPA + PDB
- [ ] Ingress(由各集群运维提供)
- [ ] NetworkPolicy(待补)
- [ ] 中间件 K8S 化(走云厂商或 Helm)
- [ ] CI/CD 集成 ArgoCD(可后续做)
