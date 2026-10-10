# ydsz-message-app

消息中心模块移动端 App 基座，封装预留移动端接口能力。

## 模块定位

- 仅在 `ydsz.platform.mode=app` 时激活
- 依赖 `ydsz-common-app` 获得 App 端基础能力
- 提供消息中心模块 App 端健康检查、OpenAPI 配置
- 预留 `controller/app/` 包用于 `@AppApi` 控制器

## 依赖关系

```
ydsz-message-app
  ├── ydsz-common-app      (App 公共基座)
  ├── ydsz-message-api     (消息中心 API 契约)
  └── ydsz-message-domain  (消息中心领域层)
```
