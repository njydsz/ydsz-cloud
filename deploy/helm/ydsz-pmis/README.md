# Helm Chart · YDSZ PMIS

> Helm Chart 形态的 K8s 部署资产，作为 `deploy/k8s/`（Kustomize）的替代方案
> 适用：参数化发布、多环境快速切换、CI/CD 流水线集成

---

## 1. 目录结构

```
helm/ydsz-pmis/
├── Chart.yaml              # Chart 元数据（v1.0.0）
├── values.yaml             # 全局默认值
├── values-dev.yaml         # DEV 环境覆盖（1 副本 + DEBUG）
├── values-sit.yaml         # SIT 环境覆盖（2 副本 + INFO）
├── values-uat.yaml         # UAT 环境覆盖（2-3 副本 + PDB）
├── values-prod.yaml        # PROD 环境覆盖（多副本 + HPA + Ingress + TLS）
└── templates/
    ├── _helpers.tpl        # 模板辅助函数
    ├── deployment.yaml     # 后端 7 微服务 Deployment+Service（循环生成）
    ├── frontend.yaml       # 前端 Deployment+Service（可选）
    ├── configmap.yaml      # 公共环境变量
    ├── secret.yaml         # 数据库密码（生产用 external-secrets 替换）
    ├── ingress.yaml        # gateway + frontend Ingress
    ├── hpa.yaml            # HorizontalPodAutoscaler
    ├── pdb.yaml            # PodDisruptionBudget
    ├── serviceaccount.yaml # ServiceAccount
    └── NOTES.txt           # 部署完成后的提示
```

---

## 2. 快速开始

### 2.1 前置条件

| 工具 | 版本 | 说明 |
|---|---|---|
| helm | ≥ 3.13 | Helm CLI |
| kubectl | ≥ 1.27 | K8S CLI |
| K8S 集群 | ≥ 1.27 | 任意发行版 |
| metrics-server | latest | **HPA 依赖** |

### 2.2 部署到开发环境

```bash
# 创建命名空间
kubectl create namespace pmis-dev --dry-run=client -o yaml | kubectl apply -f -

# 部署
helm install pmis deploy/helm/ydsz-pmis -n pmis-dev \
  -f deploy/helm/ydsz-pmis/values-dev.yaml

# 验证
kubectl -n pmis-dev get pods
kubectl -n pmis-dev get svc
helm -n pmis-dev status pmis
```

### 2.3 部署到生产环境

```bash
# 部署（生产配置含 HPA + PDB + Ingress + TLS）
helm install pmis deploy/helm/ydsz-pmis -n pmis \
  -f deploy/helm/ydsz-pmis/values-prod.yaml

# 升级
helm upgrade pmis deploy/helm/ydsz-pmis -n pmis \
  -f deploy/helm/ydsz-pmis/values-prod.yaml \
  --set global.imageTag=v1.3.1

# 回滚
helm rollback pmis 1 -n pmis

# 卸载
helm uninstall pmis -n pmis
```

---

## 3. 多环境差异对照

| 维度 | dev | sit | uat | prod |
|---|---|---|---|---|
| 镜像 tag | v1.0.0-SNAPSHOT | v1.0.0-rc.1 | v1.0.0-rc.2 | **v1.0.0** |
| springProfile | dev | sit | uat | prod |
| 日志级别 | DEBUG | INFO | INFO | INFO |
| gateway 副本 | 1 | 2 | 2 | **3 (HPA 3-10)** |
| project 副本 | 1 | 2 | 3 | **4 (HPA 4-12)** |
| agent 副本 | 1 | 1 | 1 | **2 (HPA 2-6)** |
| PDB | — | — | ✓ | ✓ |
| HPA | — | — | — | ✓ |
| Ingress + TLS | — | — | — | ✓ |
| 前端 | — | — | — | ✓ |

---

## 4. 自定义配置

### 4.1 覆盖镜像仓库

```bash
helm install pmis deploy/helm/ydsz-pmis -n pmis \
  -f deploy/helm/ydsz-pmis/values-prod.yaml \
  --set global.imageRegistry=registry.cn-hangzhou.aliyuncs.com/your-org \
  --set global.imageTag=v1.0.0
```

### 4.2 覆盖单个服务副本数

```bash
helm upgrade pmis deploy/helm/ydsz-pmis -n pmis \
  -f deploy/helm/ydsz-pmis/values-prod.yaml \
  --set services.project.replicas=6
```

### 4.3 禁用某个服务

```bash
helm install pmis deploy/helm/ydsz-pmis -n pmis \
  -f deploy/helm/ydsz-pmis/values-dev.yaml \
  --set services.agent.enabled=false
```

### 4.4 启用前端

```bash
helm upgrade pmis deploy/helm/ydsz-pmis -n pmis \
  -f deploy/helm/ydsz-pmis/values-prod.yaml \
  --set frontend.enabled=true
```

---

## 5. 与 Kustomize 的关系

| 维度 | Helm Chart | Kustomize (`deploy/k8s/`) |
|---|---|---|
| 形态 | 参数化模板 | 静态清单 + patch |
| 适用 | 多环境快速切换 | GitOps（ArgoCD/Flux） |
| 安装 | `helm install` | `kubectl apply -k` |
| 自定义 | `--set` 或 values 文件 | edit YAML patch |
| 回滚 | `helm rollback` | `kubectl rollout undo` |

**推荐**：
- 开发/测试环境用 **Helm**（参数化方便）
- 生产环境用 **Kustomize + ArgoCD**（GitOps 可审计）

---

## 6. 模板渲染验证（dry-run）

```bash
# 渲染但不部署
helm template pmis deploy/helm/ydsz-pmis -n pmis \
  -f deploy/helm/ydsz-pmis/values-prod.yaml > /tmp/pmis-rendered.yaml

# Lint 检查
helm lint deploy/helm/ydsz-pmis -f deploy/helm/ydsz-pmis/values-prod.yaml
```

---

## 7. 生产环境加固清单

部署到生产前必须确认：

- [ ] **Secret 加密**：用 [external-secrets](https://external-secrets.io/) 或 [sealed-secrets](https://github.com/bitnami-labs/sealed-secrets) 替换明文密码
- [ ] **TLS 证书**：用 [cert-manager](https://cert-manager.io/) 自动签发
- [ ] **NetworkPolicy**：`networkPolicy.enabled=true`
- [ ] **ResourceQuota + LimitRange**：命名空间级别资源约束
- [ ] **ImagePullSecrets**：私有仓库认证
- [ ] **镜像 tag 固定**：不要用 `latest` 或 `SNAPSHOT`
- [ ] **PDB 启用**：`podDisruptionBudget.enabled=true`
- [ ] **HPA 启用**：核心服务（gateway/project/agent）
- [ ] **备份策略**：数据库定期备份 + 演练

---

## 8. 相关链接

- [deploy/ 总入口](../../README.md)
- [Kustomize 部署](../k8s/README.md)
- [镜像构建脚本](../scripts/build-images.sh)
- [冒烟测试脚本](../scripts/smoke-test.sh)
