<!--
  ===========================================================================
  文件名: api-versioning.md
  路径:   docs/api/api-versioning.md
  作用:   PMIS API 版本管理规范：URL Path Versioning、废弃策略、兼容性约束、迁移指南
  适用:   所有 REST API 提供方（后端 Controller）与消费方（前端、第三方）
  关联:   ../standards/api-spec.md#13-版本控制
  ===========================================================================
-->

# API 版本管理规范

> 文档版本: V1.0 | 编制日期: 2026-07-01 | 最近更新: 2026-07-03
> 适用: PMIS 全栈 API（14 模块）
> 策略: URL Path Versioning（业界主流，便于网关路由）

> 📌 本规范是 API 演进过程中的**契约守则**，所有破坏性变更必须遵循"先标记后下线"原则。

YDSZ PMIS 采用 URL Path Versioning 策略，所有 API 以 `/api/v1/` 为前缀。

### 版本升级规则

| 变更类型 | 是否需要新版本 | 示例 |
|---------|--------------|------|
| 新增端点 | 否 | 新增 GET /api/v1/execution/search/projects |
| 新增可选参数 | 否 | GET /api/v1/project/list 新增 sort 参数 |
| 新增响应字段 | 否 | 响应新增 updatedAt 字段 |
| 删除端点 | 是 | 删除 GET /api/v1/old-endpoint → 新增 GET /api/v2/... |
| 修改参数语义 | 是 | status 参数从 string 改为 enum |
| 修改响应结构 | 是 | 从 {data: []} 改为 {data: {records: []}} |
| 改变认证方式 | 是 | 从 Basic Auth 改为 Bearer Token |

### 废弃策略

- 旧版本 API 在新版本发布后**至少保留 6 个月**
- 废弃 API 返回 `Deprecation` 和 `Sunset` 响应头：
  ```
  Deprecation: true
  Sunset: Sat, 31 Dec 2026 00:00:00 GMT
  Link: </api/v2/project/list>; rel="successor-version"
  ```
- 废弃前 1 个月通过系统通知提醒所有 API 消费方

## 2. 当前版本

- **v1** — 2026-07-02 发布，当前活跃版本

## 3. 向后兼容性约束

v1 版本 API 必须遵守以下兼容性约束：

1. **不删除现有端点** — 标记 deprecated 而非删除
2. **不改变参数名** — 新增参数必须是可选的
3. **不缩小响应字段范围** — 可以新增字段，不删除字段
4. **不改变错误码语义** — 404 保持 404，不改为 400
5. **不改变认证/授权模型** — 保持 Bearer Token + 权限码

## 4. OpenAPI 文档标注

在 Swagger 注解中标注废弃接口：
```java
@Deprecated
@Operation(summary = "查询项目列表（已废弃，请使用 /api/v2/project/list）",
           deprecated = true)
@GetMapping("/list")
public Result<PageResult<ProjectVO>> listProjects(@RequestParam PageQuery query) {
    // ...
}
```

## 5. 版本迁移指南

每次主版本升级时，需提供：
1. **变更清单** — 新增/修改/删除的端点
2. **迁移指南** — 每个破坏性变更的替代方案
3. **示例代码** — 前端/移动端/第三方集成的代码示例
4. **过渡期安排** — 双版本并行时间表

## 6. 迁移指南模板

> 实际升级时复制此模板到 `docs/api/migration/v1-to-v2.md`

```markdown
# API v1 → v2 迁移指南

## 升级时间表

| 阶段 | 日期 | 内容 |
|------|------|------|
| 公告期 | YYYY-MM-DD | 发布迁移指南、邮件通知所有消费方 |
| 双版本期 | YYYY-MM-DD | v1 + v2 并行，至少 6 个月 |
| 废弃期 | YYYY-MM-DD | v1 接口添加 Deprecation: true 响应头 |
| 下线期 | YYYY-MM-DD | v1 接口返回 410 Gone |

## 破坏性变更清单

| 接口 | 变更类型 | 替代方案 |
|------|----------|----------|
| GET /api/v1/project/list | 删除 | GET /api/v2/projects |
| ... | ... | ... |

## 自动化迁移脚本

    # 批量替换前端 API 路径
    find ydsz-pmis-frontend/src -name "*.ts" -exec sed -i 's|/api/v1/project|/api/v2/projects|g' {} \;

## FAQ

| 问题 | 答案 |
|------|------|
| v1 还能用多久？ | 发布新版本后至少 6 个月 |
| 是否影响性能？ | 无影响，v1/v2 走相同服务实例 |
| 如何回滚？ | 切回旧版镜像，URL 不变 |
```

> 上面的 `自动化迁移脚本` 段落使用 4 空格缩进避免 markdown 高亮错乱。

## 7. 前端版本切换最佳实践

```typescript
// src/config/api-version.ts
export const API_VERSION = 'v2' as const

// src/api/project/index.ts
import { API_VERSION } from '@/config/api-version'
const BASE = `/api/${API_VERSION}/projects`
```

> 前端通过统一 `API_VERSION` 常量控制版本，**禁止** 在调用处硬编码 `/api/v1/`。

## 8. 监控与告警

- v1 接口访问量监控：每日汇总 Top 10 调用方
- 访问量 < 1% 持续 1 个月 → 评估下线
- 关键客户仍依赖 v1 → 主动通知 + 协助迁移

## 9. 相关文档

- 接口规范：[`../standards/api-spec.md`](../standards/api-spec.md)
- API 总览：[`README.md`](README.md)

## 10. 变更记录

| 日期 | 版本 | 变更人 | 变更内容 |
|------|------|--------|----------|
| 2026-07-03 | 1.1 | 架构组 | 顶部 banner、新增 §6-§10 迁移模板、前端切换、监控、变更记录 |
| 2026-07-01 | 1.0 | 架构组 | 初始版本 |
