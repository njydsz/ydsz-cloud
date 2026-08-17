# ydsz-system-app

系统管理模块移动端 App 基座，封装预留移动端接口能力。

## 模块定位

- 仅在 `ydsz.platform.mode=app` 时激活
- 依赖 `ydsz-common-app` 获得 App 端基础能力
- 提供系统管理模块 App 端健康检查、OpenAPI 配置
- 预留 `controller/app/` 包用于 `@AppApi` 控制器

## 依赖关系

```
ydsz-system-app
  ├── ydsz-common-app     (App 公共基座)
  ├── ydsz-system-api     (系统管理 API 契约)
  └── ydsz-system-domain  (系统管理领域层)
```
