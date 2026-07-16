# YDSZ PMIS 前端工程

> 南京云顶数字科技有限公司 · 项目运营管理系统 - 前端

## 技术栈

- **核心框架**: Vue 3.5 + TypeScript 5
- **构建工具**: Vite 5
- **UI 组件**: Element Plus 2.8 + vxe-table 4
- **状态管理**: Pinia 2
- **路由**: Vue Router 4
- **HTTP**: Axios 1.7
- **可视化**: ECharts 5
- **工具**: lodash-es、dayjs、@vueuse/core
- **测试**: Vitest 2 + @vue/test-utils
- **规范**: ESLint 8 + Prettier 3 + Husky 9 + commitlint 19

## 目录结构

```
ydsz-frontend/
├── public/                     # 静态资源
├── src/
│   ├── api/                    # 接口请求层
│   ├── assets/                 # 静态资源
│   ├── components/             # 通用组件
│   │   ├── common/             # 基础组件
│   │   └── business/           # 业务组件
│   ├── composables/            # 组合式函数
│   ├── config/                 # 全局配置
│   ├── directives/             # 自定义指令
│   ├── hooks/                  # 通用 Hook
│   ├── layout/                 # 布局
│   ├── locales/                # 国际化
│   ├── plugins/                # 插件注册
│   ├── router/                 # 路由
│   ├── store/                  # Pinia 状态
│   ├── styles/                 # 全局样式
│   ├── types/                  # 类型
│   ├── utils/                  # 工具
│   ├── views/                  # 页面
│   ├── App.vue
│   └── main.ts
├── .env.development
├── .env.production
├── .eslintrc.cjs
├── .prettierrc.json
├── index.html
├── package.json
├── tsconfig.json
└── vite.config.ts
```

## 本地开发

```bash
# 安装依赖
pnpm install

# 启动开发服务器 (默认 http://localhost:5173)
pnpm dev

# 类型检查
pnpm type-check

# 代码格式化
pnpm format

# 代码检查
pnpm lint

# 单元测试
pnpm test

# 测试覆盖率
pnpm test:coverage

# 生产构建
pnpm build:prod
```

## 环境要求

- Node.js >= 20.0.0
- pnpm >= 8.0.0
- 浏览器: Chrome 100+, Edge 100+, Firefox 100+

## 后端接口

本地默认对接后端网关：`http://localhost:9000`

具体配置在 `.env.development` 中。
