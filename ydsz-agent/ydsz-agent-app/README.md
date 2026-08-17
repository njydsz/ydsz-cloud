# ydzs-agent-app

Agent 模块移动端 App 基座，封装预留移动端接口能力。

## 模块定位

- 仅在 `ydsz.platform.mode=app` 时激活
- 依赖 `ydsz-common-app` 获得 App 端基础能力（鉴权、过滤器链、响应包装等）
- 提供 Agent 模块 App 端健康检查、OpenAPI 配置
- 预留 `controller/app/` 包用于 `@AppApi` 控制器

## 依赖关系

```
ydsz-agent-app
  ├── ydsz-common-app    (App 公共基座)
  ├── ydsz-agent-api     (Agent API 契约)
  └── ydsz-agent-domain  (Agent 领域层)
```
