# ADR-003: SSR / Pre-rendering 方案评估

- **状态**: 已决定（2026-08-04）
- **决策者**: ydsz-team
- **优先级**: P3-2（评估后可选实施）

## 背景

对标 industry 竞品（qiankun、Garfish、Micro-app）发现部分大厂在微前端基座引入 SSR 或预渲染以优化 FCP/LCP。自研 micro-kernel 基于 ESM 原生运行时（浏览器端 dynamic import），默认纯 CSR。

## 现状性能保障

| 措施 | 作用 | 状态 |
|------|------|------|
| `injectAppLoading` | 应用启动前展示 loading 屏（避免白屏） | ✅ 已实现 |
| `Speculation Rules API` | Chrome 浏览器原生 prefetch，悬停/空闲时预加载子应用 ESM | ✅ 已实现（P0-2） |
| `keepAlive + LRU` | 切回已访问子应用时直接复用 DOM，无需重新 mount | ✅ 已实现（P2-1） |
| `vite warmup` | dev 模式预热入口文件 | ✅ 已实现 |

## 方案对比

### A. 全量 SSR（Nuxt / Vite SSR）

- **成本**: 高。micro-kernel 改造为同构运行时，每个子应用需导出 SSR 兼容的 `mountToString()` 生命周期。主应用 router、Pinia、element-plus 均需 SSR 适配。
- **收益**: 仅首屏 FCP 改善约 100–200ms（本子应用 ESM 加载本身 > 500ms）。
- **副作用**: 内存占用、hydration mismatch、微前端沙箱无法跨端复用。

### B. Prerender App Shell（仅渲染主应用框架壳）

- **成本**: 低。在 index.html 模板中内联关键 CSS（框架骨架样式），Vue 启动前先看到侧边栏 + 顶栏。子应用区域保持 loading skeleton。
- **收益**: 首屏框架立即可见（FCP 改善 50–100ms），子应用仍 CSR。
- **副作用**: 几乎无。

### C. 维持现状（不实施）

- **成本**: 零。
- **收益**: 无。

## 决策

**推荐方案 C（维持现状）**，原因：

1. 管理后台无 SEO 需求，SSR 的 SEO 收益不适用
2. 已实施的三层保障（loading + prefetch + keepAlive）基本覆盖首屏体验
3. 全量 SSR 改造成本高且引入 hydration 复杂度
4. App Shell Prerender 可通过 HTML 模板优化（无代码侵入）在未来按需追加

## 条件重评

若出现以下信号，可重议 App Shell Prerender：

- FCP > 1.5s 的监控告警持续出现
- 用户反馈首屏"等待感"显著强于同类管理后台
- 浏览器原生 Speculation Rules 覆盖不足（非 Chrome 用户占比 > 40%）

## 参考

- [Speculation Rules API](https://developer.mozilla.org/en-US/docs/Web/API/Speculation_Rules_API)
- [Vite SSR Guide](https://vitejs.dev/guide/ssr.html)
- [qiankun SSR 讨论](https://github.com/umijs/qiankun/issues/1306)
