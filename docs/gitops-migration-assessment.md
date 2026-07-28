# P3-3: GitOps 迁移评估

## 现状分析

### 当前 CD 方式
- **工具**: GitHub Actions + kubectl/argo rollouts CLI
- **配置存储**: GitHub workflow YAML + K8s YAML in `deploy/`
- **部署方式**: `kubectl set image` / `kubectl argo rollouts set image`
- **状态管理**: K8s 集群为唯一真相源，Git 仅存储期望状态

### 痛点
1. **配置漂移**: K8s 集群实际状态与 Git 中声明的状态可能不一致
2. **回滚依赖人工**: 回滚需要手动执行 `kubectl argo rollouts undo`
3. **多环境管理**: staging/production 配置散落在 workflow YAML 中
4. **审计追踪不足**: 部署操作记录在 GitHub Actions 日志中，不可结构化查询

## GitOps 迁移方案

### 推荐工具: Argo CD

| 维度 | Argo CD | Flux CD |
|------|---------|---------|
| UI | ✅ Web UI | ❌ CLI only |
| 多集群 | ✅ 原生支持 | ✅ 支持 |
| Helm | ✅ 原生支持 | ✅ 支持 |
| Kustomize | ✅ 原生支持 | ✅ 支持 |
| 健康检查 | ✅ 内置 | ❌ 需自定义 |
| 回滚 | ✅ Web UI 一键 | ❌ git revert |
| 告警 | ✅ Notifications | ✅ Alertmanager |
| 社区活跃度 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ |

### 迁移路线图（3 阶段）

#### 阶段一：Argo CD 部署 + 应用注册（第 1-2 周）
1. 在 K8s 集群安装 Argo CD
2. 将 `deploy/k8s/` 目录注册为 Argo CD Application
3. 配置 Argo CD 与 Git 仓库的同步
4. 配置自动同步策略（prune + self-heal）

#### 阶段二：CD 流水线改造（第 3-4 周）
1. `cd-deploy.yml` 从 `kubectl set image` 改为更新 Git 中的 image tag
2. Argo CD 检测到 Git 变更后自动同步到 K8s
3. 保留 Argo Rollouts 金丝雀策略
4. 配置 Argo CD Notifications（钉钉/飞书 webhook）

#### 阶段三：多环境管理（第 5-6 周）
1. 使用 Kustomize overlays 管理 staging/production 差异
2. 引入 ApplicationSet 实现多环境自动注册
3. 配置 RBAC 权限矩阵
4. 配置 SSO 集成

### 预期收益

| 指标 | 现状 | 目标 |
|------|------|------|
| 部署到生产时间 | 15-30 分钟 | 5-10 分钟 |
| 回滚时间 | 5-10 分钟 | < 1 分钟 |
| 配置漂移检测 | 手动 | 自动 |
| 审计追踪 | GitHub Actions 日志 | Argo CD History |
| 多环境管理 | workflow YAML | Kustomize overlays |

### 风险与缓解

| 风险 | 缓解措施 |
|------|---------|
| Argo CD 单点故障 | 多副本部署 + 定期备份 |
| 自动同步误操作 | 配置 manual sync for production |
| Git 仓库不可用 | Argo CD 缓存 24h，期间不影响运行 |

## 结论

**推荐在 P0-P2 完成后启动 GitOps 迁移**，预计 6 周完成。Argo CD 是当前最成熟的 GitOps 工具，与项目已有的 Argo Rollouts 无缝集成。
