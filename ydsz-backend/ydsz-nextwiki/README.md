# ydsz-nextwiki 网盘知识库服务

## 模块概述

ydsz-nextwiki 是一个融合文件存储、文档解析、全文搜索、在线预览、版本控制、分享协作的一体化网盘知识库平台。

## 架构

采用 DDD 五层架构：

```
ydsz-nextwiki/
├── ydsz-nextwiki-api        # API 契约层（DTO、VO、接口）
├── ydsz-nextwiki-domain     # 领域层（实体、值对象、领域服务、仓储接口）
├── ydsz-nextwiki-infra      # 基础设施层（仓储实现、Mapper）
├── ydsz-nextwiki-server     # 应用层（应用服务、配置、健康检查、监听器）
└── ydsz-nextwiki-web        # Web 层（Controller、配置文件）
```

## 核心能力

| 能力 | 描述 |
|------|------|
| 文件管理 | 上传（SHA-256 秒传）、下载（断点续传）、复制、移动、重命名、删除 |
| 目录树 | 创建/移动/重命名/递归路径更新 |
| 版本控制 | 版本历史、回滚、最多保留 20 版本 |
| 分享与ACL | 分享链接（密码/过期/次数限制）、ACL 权限（读/写/删/分享/下载） |
| 搜索 | 文件名+路径+标签+内容全文搜索、索引同步、重建 |
| 预览 | Office→PDF（LibreOffice）、图片缩略图、直接预览 |
| 配额 | 用户/租户/项目级配额管理 |
| 回收站 | 30天保留、恢复、永久删除、自动清理 |
| 标签 | 创建/绑定/推荐 |
| 批量操作 | 批量上传、ZIP 导入（炸弹防护）、文件夹打包下载 |
| 分片上传 | 大文件分片上传、断点续传 |
| AI 摘要 | TextRank 本地模式 + LLM 模式 |
| 在线编辑 | WOPI 协议（OnlyOffice/Collabora 集成） |
| 文件评论 | 评论/回复/批注 |
| 文件锁定 | Check-out/Check-in 防并发编辑 |
| 安全 | ClamAV 病毒扫描、OCR 文字识别、CDN 集成 |
| 可观测性 | 健康检查、Micrometer 指标、审计日志、分布式锁 |

## API 端点

### 文件操作
- `POST /api/v1/nextwiki/files/upload` — 上传文件
- `POST /api/v1/nextwiki/files/chunk/init` — 初始化分片上传
- `POST /api/v1/nextwiki/files/chunk/{uploadId}/{chunkNumber}` — 上传分片
- `POST /api/v1/nextwiki/files/chunk/{uploadId}/complete` — 完成分片上传
- `DELETE /api/v1/nextwiki/files/chunk/{uploadId}` — 取消分片上传
- `GET /api/v1/nextwiki/files/chunk/{uploadId}/uploaded-chunks` — 查询已上传分片
- `POST /api/v1/nextwiki/files/{nodeId}/copy` — 复制文件
- `POST /api/v1/nextwiki/files/batch-move` — 批量移动
- `POST /api/v1/nextwiki/files/batch-delete` — 批量删除
- `POST /api/v1/nextwiki/files/folder` — 创建文件夹
- `POST /api/v1/nextwiki/files/{nodeId}/move` — 移动文件
- `POST /api/v1/nextwiki/files/{nodeId}/rename` — 重命名文件
- `DELETE /api/v1/nextwiki/files/{nodeId}` — 删除文件
- `PUT /api/v1/nextwiki/files/{nodeId}/star` — 切换星标
- `POST /api/v1/nextwiki/files/{nodeId}/lock` — 锁定文件
- `POST /api/v1/nextwiki/files/{nodeId}/unlock` — 解锁文件

### 下载
- `POST /api/v1/nextwiki/download/{nodeId}` — 下载文件（支持 Range 断点续传）
- `POST /api/v1/nextwiki/download/folder/{folderId}` — 打包下载文件夹
- `POST /api/v1/nextwiki/download/signed-url/{nodeId}` — 生成签名下载 URL

### 分享
- `POST /api/v1/nextwiki/share` — 创建分享链接
- `POST /api/v1/nextwiki/share/verify` — 验证分享访问
- `DELETE /api/v1/nextwiki/share/{shareId}` — 撤销分享
- `GET /api/v1/nextwiki/share/list` — 查询分享列表

### 搜索
- `GET /api/v1/nextwiki/search` — 搜索文件
- `POST /api/v1/nextwiki/search/rebuild` — 重建索引

### 回收站
- `GET /api/v1/nextwiki/trash/list` — 查询回收站
- `POST /api/v1/nextwiki/trash/{trashItemId}/restore` — 恢复
- `POST /api/v1/nextwiki/trash/batch-restore` — 批量恢复
- `DELETE /api/v1/nextwiki/trash/{trashItemId}` — 永久删除
- `DELETE /api/v1/nextwiki/trash/empty` — 清空回收站

### 配额
- `GET /api/v1/nextwiki/quota/info` — 查询配额
- `POST /api/v1/nextwiki/quota/set` — 设置配额

### 标签
- `POST /api/v1/nextwiki/tags` — 创建标签
- `GET /api/v1/nextwiki/tags` — 查询所有标签
- `POST /api/v1/nextwiki/tags/bind` — 绑定标签
- `GET /api/v1/nextwiki/tags/file/{fileNodeId}` — 查询文件标签

### 批量导入
- `POST /api/v1/nextwiki/import/batch-upload` — 批量上传
- `POST /api/v1/nextwiki/import/zip` — ZIP 导入

### 分析与AI
- `GET /api/v1/nextwiki/analysis/overview` — 存储概览
- `GET /api/v1/nextwiki/analysis/by-type` — 按类型统计
- `GET /api/v1/nextwiki/analysis/top-large-files` — 大文件 Top-N
- `POST /api/v1/nextwiki/analysis/summary` — 生成文档摘要

### 文件评论
- `GET /api/v1/nextwiki/comments/file/{fileNodeId}` — 查询评论
- `POST /api/v1/nextwiki/comments` — 添加评论
- `DELETE /api/v1/nextwiki/comments/{commentId}` — 删除评论
- `POST /api/v1/nextwiki/comments/{commentId}/resolve` — 标记已解决

### WOPI 在线编辑
- `GET /api/v1/nextwiki/wopi/files/{fileId}` — CheckFileInfo
- `GET /api/v1/nextwiki/wopi/files/{fileId}/contents` — GetFile
- `POST /api/v1/nextwiki/wopi/files/{fileId}/contents` — PutFile
- `POST /api/v1/nextwiki/wopi/files/{fileId}/lock` — Lock
- `POST /api/v1/nextwiki/wopi/files/{fileId}/unlock` — Unlock

## 配置项

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `nextwiki.upload.max-file-size` | 524288000 | 最大文件大小（500MB） |
| `nextwiki.upload.allowed-types` | (空) | 允许的文件类型 |
| `nextwiki.upload.conflict-strategy` | KEEP_BOTH | 同名冲突策略 |
| `nextwiki.preview.libreoffice-path` | soffice | LibreOffice 路径 |
| `nextwiki.ai.llm-enabled` | false | 是否启用 LLM 摘要 |
| `nextwiki.cdn.enabled` | false | 是否启用 CDN |
| `nextwiki.virus-scan.enabled` | false | 是否启用病毒扫描 |
| `nextwiki.ocr.enabled` | false | 是否启用 OCR |
| `nextwiki.download.rate-limit-per-minute` | 30 | 下载限流 |
| `nextwiki.wopi.editor-url` | (空) | 在线编辑器 URL |

## 技术栈

- Spring Boot 3.x + Spring Cloud (Nacos)
- MyBatis-Plus
- Redis（分布式锁、限流、缓存）
- LibreOffice（文档预览）
- ClamAV（病毒扫描）
- Tesseract（OCR）
- Micrometer（监控指标）
- WebSocket（实时通知）
