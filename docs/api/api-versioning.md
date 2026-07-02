# API 版本管理规范

## 1. 版本策略

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
