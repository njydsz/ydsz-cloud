# YDSZ Micro Kernel DevTools — Chrome Extension

> Manifest V3 浏览器扩展，用于监控和调试 ydsz-pmis 微前端运行时（micro-kernel）

## 📦 安装与使用

### 开发模式加载

1. 打开 Chrome，访问 `chrome://extensions`
2. 开启右上角 **开发者模式**
3. 点击 **加载已解压的扩展程序**，选择本目录 `chrome/`
4. Extension 图标出现在 Chrome 工具栏

### 启用运行时桥接

DevTools Bridge 默认在以下条件启用：

| 条件 | 说明 |
|------|------|
| `import.meta.env.DEV === true` | 开发态自动启用 |
| `localStorage.getItem('micro-kernel:devtools') === '1'` | 手动在生产态启用 |

在生产态临时启用，打开浏览器 Console：
```js
localStorage.setItem('micro-kernel:devtools', '1'); location.reload();
```

## 🖥 功能

### Popup（工具栏图标点击）

- 快速查看内核状态（已连接 / 等待）
- 活跃应用名称
- KeepAlive 实例数
- 内存占用概览
- 一键跳转 DevTools Panel

### DevTools Panel（F12 → Micro Kernel 标签）

**4 项实时指标**
- 活跃应用
- KeepAlive 保活实例数
- JS 内存占用
- 已注册应用总数

**子应用状态列表**

| 列 | 含义 |
|----|------|
| 圆点颜色 | 状态（绿=已挂载 / 蓝=已加载 / 灰=未加载 / 黄=加载中 / 红=异常） |
| 应用名 | 子应用名称 ★ 表示当前活跃 |
| 状态 | NOT_LOADED / LOADING / LOADED / MOUNTED / UNMOUNTED |
| 沙箱类型 | snapshot / proxy / iframe |
| 耗时 | 最近一次 load 耗时（ms） |
| 操作 | 「卸载」「重载」按钮 |

**操作按钮**
- `刷新` — 清除注册表缓存 + 重新拉取全量状态
- `清缓存` — 清空浏览器 Cache Storage
- `健康检查` — 触发 `kernel.healthCheck()` 并展示 capabilities / metrics

**事件日志**
- 自动记录 `beforeLoad / afterLoad / beforeMount / afterMount / error` 等生命周期事件
- 最多 80 条，自动滚动
- 可折叠

## 📁 文件结构

```
chrome/
├── manifest.json         # MV3 配置
├── kernel-bridge.js      # 页面侧桥接器 → 推送事件/接收命令
├── content-script.js     # 内容脚本 → page↔background 路由
├── background.js         # Service Worker → 状态缓存/命令转发/连接管理
├── devtools/
│   ├── devtools.html     # DevTools 入口
│   ├── devtools.js       # 创建面板
│   ├── panel.html        # 面板 UI
│   └── panel.js          # 面板逻辑
└── popup/
    ├── popup.html        # 工具栏弹出
    └── popup.js          # 弹出逻辑
```

## 🔄 通信链路

```
micro-kernel 生命周期钩子
    │
    ▼
main/src/monitoring/devtools-bridge.ts  (主应用侧)
    │  window.__sendToExtension / postMessage
    ▼
chrome/content-script.js  (content script)
    │  chrome.runtime.sendMessage
    ▼
chrome/background.js  (Service Worker)
    │  chrome.runtime.onMessage
    ▼
chrome/devtools/panel.js  ←→  chrome/popup/popup.js
```

### 关键消息协议

| 方向 | type | 说明 |
|------|------|------|
| Page → Ext | `kernel:state:request` | 请求全量快照 |
| Page → Ext | `kernel:state:response` | 全量状态（apps / activeApp / keepAlive / memory / capabilities） |
| Page → Ext | `kernel:health:response` | 健康检查结果 |
| Page → Ext | `lifecycle:beforeLoad/afterLoad/beforeMount/afterMount/error` | 生命周期事件 |
| Page → Ext | `kernel:memory` | JS 内存信息 |
| Ext → Page | `kernel:unmount` | 卸载指定应用 |
| Ext → Page | `kernel:reload` | 重载指定应用 |
| Ext → Page | `kernel:clear-cache` | 清缓存 |
| Ext → Page | `kernel:health:request` | 触发健康检查 |
| Ext → Page | `kernel:refresh-registry` | 刷新远程注册表 |

## 🛠 主应用侧集成点

修改了以下主应用代码以支持 Extension：

| 文件 | 改动 |
|------|------|
| `main/src/bootstrap.ts` | 导入 `enableDevToolsBridge`，在 `enableMicroDevTools()` 之后启用桥接 |
| `main/src/monitoring/devtools-bridge.ts` | **新建** — 生命周期钩子封装、状态快照收集、Extension 命令处理 |
| `comm/effects/micro-kernel/src/kernel.ts` | 暴露 `getAllInstances/getAppInstance/healthCheck/refreshRegistry`，挂载 `window.__MICRO_KERNEL__` |
| `comm/effects/micro-kernel/src/index.ts` | 导出 CanaryManager、semver 工具 |
| `comm/effects/micro-runtime/src/package.json` | 子路径 exports 追加 `./semver`、`./namespaced-state` |

## 🔌 加载后可验证

1. 启动主应用（`pnpm dev`）
2. F12 打开 DevTools，切到 **Micro Kernel** 标签
3. 刷新页面，观察：
   - 子应用列表逐渐填充
   - `afterMount` 事件触发后活跃应用更新
   - 点击子应用行的 「卸载」「重载」按钮可以即时操作
4. 关闭 F12 再次打开，快照从 background 缓存恢复

## ⚠ 已知限制

- MV3 限制 Extension 无法主动打开 F12，需用户手动切换标签
- `chrome.runtime.connect` 在 Service Worker 不活跃时断开，下一次交互自动重连
- 心跳间隔 5s，Extension 短暂无响应后会自动恢复
