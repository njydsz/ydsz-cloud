<!--
  ===========================================================================
  文件名: README.md
  路径:   docs/_shared/README.md
  作用:   PMIS 文档共享资源目录说明：字体、ECharts、Mermaid 等
  适用:   docs/pmis-prd-v3.html、docs/standards/ 等所有 HTML/Markdown 文档
  ===========================================================================
-->

# docs/_shared 共享资源目录

> 本目录集中存放 PMIS 文档体系（PRD、API、运维、报告等）共用的静态资源，避免在每份文档中重复引入。

## 目录结构

```
docs/_shared/
├── README.md           # 本文件
├── fonts/              # 文档专用字体
│   ├── JetBrainsMono-Bold.ttf
│   ├── JetBrainsMono-Regular.ttf
│   ├── Outfit-Bold.ttf
│   └── Outfit-Regular.ttf
└── js/                 # 文档专用 JS 库（CDN 备份/离线使用）
    ├── echarts.min.js  # Apache ECharts 5.x，用于雷达图/柱状图等可视化
    └── mermaid.min.js  # Mermaid，用于流程图/时序图/类图
```

## 使用方式

### 在 HTML 文档中引用

```html
<!-- 相对路径引用，避免 CDN 不稳定 -->
<script src="../_shared/js/echarts.min.js"></script>
<script src="../_shared/js/mermaid.min.js"></script>
```

### 在 Markdown 文档中

GitHub 渲染时建议通过 `pmis-prd-v3.html` 转换后查看，Mermaid 代码块需配合 `docs/_shared/js/mermaid.min.js`。

## 字体说明

| 字体 | 用途 | License |
|------|------|---------|
| Outfit Regular/Bold | 标题/正文 | Open Font License（OFL） |
| JetBrainsMono Regular/Bold | 代码/等宽 | Apache 2.0 |

## JS 库版本

| 库 | 版本 | 来源 |
|----|------|------|
| ECharts | 5.x | https://echarts.apache.org/ |
| Mermaid | 10.x | https://mermaid.js.org/ |

## 维护

- 资源升级必须经过 PMIS 文档组 review，避免破坏 PRD 渲染
- 新增资源请同步更新本 README
- 字体/JS 库均为开源/可商用 License

## 变更记录

| 日期 | 版本 | 变更人 | 变更内容 |
|------|------|--------|----------|
| 2026-07-03 | 1.0 | 文档组 | 初始化 README，登记字体/JS 资源 |
