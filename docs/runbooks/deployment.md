# 部署操作手册

## 1. 首次部署

### 前置条件
- [ ] K8s 集群已就绪
- [ ] Nacos 命名空间 `ydsz` 已创建
- [ ] Helm 3.x 已安装
- [ ] Container Registry 可访问

### 步骤

1. **初始化数据库**
   ```bash
   # 连接 PostgreSQL，执行初始化 SQL
   psql -h <PG_HOST> -U ydsz -d ydsz_pmis -f deploy/sql/schema/V1.0.0__init.sql
   ```

2. **部署基础设施**
   ```bash
   # 从 docker-compose.dev.yml 确认所有基础设施健康
   NAMESPACE=ydsz-prod
   kubectl create namespace $NAMESPACE --dry-run=client -o yaml | kubectl apply -f -
   ```

3. **Helm 部署**
   ```bash
   helm install ydsz-backend ./deploy/helm/ydsz-backend \
     --namespace ydsz-prod \
     --values deploy/helm/ydsz-backend/values-prod.yaml \
     --set image.tag=<VERSION> \
     --wait --timeout 15m
   ```

4. **验证部署**
   ```bash
   # 检查所有 Pod 就绪
   kubectl get pods -n ydsz-prod

   # 验证网关健康
   curl -sf http://<gateway-ip>:9000/actuator/health

   # 验证 Swagger 文档
   curl -sf http://<gateway-ip>:9000/v3/api-docs
   ```

---

## 2. 滚动更新

### 自动滚动更新（CI/CD）
通过 GitHub Actions 触发，自动完成构建 → 镜像推送 → K8s 滚动更新。

### 手动滚动更新
```bash
# 更新镜像版本
helm upgrade ydsz-backend ./deploy/helm/ydsz-backend \
  --namespace ydsz-prod \
  --set image.tag=<NEW_VERSION> \
  --wait --timeout 10m

# 监控滚动状态
kubectl rollout status deployment/ydsz-gateway -n ydsz-prod
```

---

## 3. 回滚操作

### 快速回滚（上一版本）
```bash
# Helm 回滚到上一个 revision
helm rollback ydsz-backend -n ydsz-prod

# 回滚到指定 revision
helm rollback ydsz-backend <REVISION> -n ydsz-prod
```

### 指定版本回滚
```bash
helm upgrade ydsz-backend ./deploy/helm/ydsz-backend \
  --namespace ydsz-prod \
  --set image.tag=<PREVIOUS_VERSION> \
  --wait --timeout 10m
```

### 回滚后验证
```bash
# 检查 Pod 镜像版本
kubectl get pods -n ydsz-prod -o jsonpath='{.spec.containers[*].image}'

# 检查服务健康
curl -sf http://<gateway-ip>:9000/actuator/health | jq .status
```

---

## 4. 数据库变更

1. **备份当前数据库**
   ```bash
   pg_dump -h <PG_HOST> -U ydsz -d ydsz_pmis --no-owner --no-privileges > backup_$(date +%Y%m%d_%H%M%S).sql
   ```

2. **执行变更脚本**
   ```bash
   psql -h <PG_HOST> -U ydsz -d ydsz_pmis -f deploy/sql/schema/V1.x.x__<change_name>.sql
   ```

3. **验证变更**
   ```bash
   psql -h <PG_HOST> -U ydsz -d ydsz_pmis -c "\dt ydsz_*" | wc -l
   ```
