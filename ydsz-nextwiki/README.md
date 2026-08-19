# ydsz-nextwiki

> 网盘知识库服务（Next Wiki）— 文件存储 / 文档解析 / 全文搜索 / 在线预览 / 版本控制 / 分享协作

## 模块定位

| 属性 | 值 |
|---|---|
| **类型** | 部署单元（独立启动） |
| **端口** | **9003**（按构建顺序 4/10） |
| **服务名** | `ydsz-nextwiki` |
| **构建顺序** | 4/10 |
| **数据库** | PostgreSQL |
| **依赖** | Nacos、PostgreSQL、Redis、MinIO |
| **公共依赖** | common-web / common-file / common-search / common-jdbc / common-lock / common-redis |

## 核心职责

本模块是 YDSZ 的**一体化网盘知识库平台**，融合文件存储、文档解析、全文搜索、在线预览、版本控制、分享协作。

| 能力 | 说明 |
|---|---|
| **文件管理** | 上传（SHA-256 秒传）、下载（断点续传）、复制、移动、重命名、删除、拖拽排序 |
| **目录树** | 创建/移动/重命名/递归路径更新 |
| **版本控制** | 版本历史、回滚、最多保留 20 版本 |
| **分享与 ACL** | 分享链接（密码/过期/次数限制）、ACL 权限（读/写/删/分享/下载） |
| **搜索** | 文件名+路径+标签+内容全文搜索、索引同步、重建（基于 common-search） |
| **预览** | Office→PDF（LibreOffice）、图片缩略图、直接预览、动态水印 |
| **配额** | 用户/租户/项目级配额管理、按文件类型配额 |
| **回收站** | 30 天保留、恢复、永久删除、自动清理 |
| **标签** | 创建/绑定/推荐 |
| **批量操作** | 批量上传、ZIP 导入（炸弹防护）、文件夹打包下载 |
| **分片上传** | 大文件分片上传、断点续传（基于 common-file） |
| **AI 摘要** | TextRank 本地模式 + LLM 模式 |
| **在线编辑** | WOPI 协议（OnlyOffice/Collabora 集成） |
| **文件评论** | 评论/回复/批注 |
| **文件锁定** | Check-out/Check-in 防并发编辑 |
| **收藏夹/最近访问** | 快捷访问入口、收藏管理 |
| **空间管理** | 知识库空间（Space）聚合根、空间成员角色（RBAC）、空间配额 |
| **文档模板** | 预定义空间结构模板、自定义模板、快速创建空间 |
| **安全** | ClamAV 病毒扫描、OCR 文字识别、CDN 集成、动态水印 |
| **可观测性** | 健康检查、Micrometer 指标、审计日志、分布式锁、缓存命中率 |

## DDD 分层结构

```
ydsz-nextwiki/
├── pom.xml
├── ydsz-nextwiki-api/                 # API 层：Feign Client + DTO
├── ydsz-nextwiki-domain/              # 领域层：领域服务 + Repository 接口 + VO/DTO/事件/枚举
│   └── src/main/java/com/njydsz/nextwiki/domain/
│       ├── dto/                       # 数据传输对象（FileNodeDTO / FileVersionDTO / TrashItemDTO 等）
│       ├── vo/                        # 视图对象（FileNodeVO / SearchResultVO / ShareLinkVO 等 13 个）
│       ├── query/                     # 查询对象（FileNodeQuery / FileVersionQuery 等）
│       ├── repository/                # 仓储接口（11 个）
│       ├── service/                   # 领域服务（Folder/FileVersion/Quota/Search/Share/Tag/Trash/FilePermission/ShareAccessLog/ShareLink/StorageReference）
│       ├── event/                     # 领域事件（FileOperatedEvent / AuditEvent）
│       ├── enums/                     # 枚举（NextwikiEnums / NextwikiExceptionCode）
│       └── config/                    # 领域安全配置
├── ydsz-nextwiki-infra/               # 基础设施层：DO 实体 + Mapper + 仓储实现
│   └── src/main/java/com/njydsz/nextwiki/infra/
│       ├── entity/                    # 持久化实体（12 个 DO：FileNodeDO / FileVersionDO / FileAclDO 等）
│       ├── mapper/                    # MyBatis Mapper（10 个）
│       ├── repository/                # 仓储实现（11 个）
│       └── converter/                 # MapStruct 转换器（DO ↔ VO / DTO）
├── ydsz-nextwiki-server/              # 应用层（命名沿用 server）：应用服务 + 配置 + 缓存 + 监听器 + 定时任务
└── ydsz-nextwiki-web/                 # Web 层：Controller + 启动类
    └── src/main/java/com/njydsz/nextwiki/web/
        ├── NextwikiApplication.java   # 启动类（位于 web 根包下）
        └── controller/                # 17 个 Controller
            ├── FileController.java          # /api/v1/nextwiki/files
            ├── FileChunkController.java     # /api/v1/nextwiki/files/chunk
            ├── FileBatchController.java     # /api/v1/nextwiki/files/batch/*（异步任务）
            ├── FileLockController.java      # /api/v1/nextwiki/files/{nodeId}/lock
            ├── FileCommentController.java   # /api/v1/nextwiki/comments
            ├── DownloadController.java      # /api/v1/nextwiki/download
            ├── ShareController.java         # /api/v1/nextwiki/shares
            ├── SearchController.java        # /api/v1/nextwiki/search
            ├── TrashController.java         # /api/v1/nextwiki/trash
            ├── QuotaController.java         # /api/v1/nextwiki/quota
            ├── TagController.java           # /api/v1/nextwiki/tags
            ├── BatchImportController.java   # /api/v1/nextwiki/import
            ├── AnalysisController.java      # /api/v1/nextwiki/analysis
            ├── PreviewController.java       # /api/v1/nextwiki/preview
            ├── WopiController.java          # /api/v1/nextwiki/wopi
            ├── AiController.java            # /api/v1/nextwiki/ai（文档摘要/状态）
            └── storage/PresignedUrlController.java # /api/v1/nextwiki/storage（预签名上传/下载）
```

## 关键 Controller 端点

### 文件操作

| 端点 | 作用 |
|---|---|
| `POST /api/v1/nextwiki/files/upload` | 上传文件 |
| `POST /api/v1/nextwiki/files/chunk/init` | 初始化分片上传 |
| `POST /api/v1/nextwiki/files/chunk/{uploadId}/{chunkNumber}` | 上传分片（chunkNumber 从 1 开始） |
| `POST /api/v1/nextwiki/files/chunk/{uploadId}/complete` | 完成分片上传 |
| `DELETE /api/v1/nextwiki/files/chunk/{uploadId}` | 取消分片上传 |
| `POST /api/v1/nextwiki/files/{nodeId}/copy` | 复制文件 |
| `PUT /api/v1/nextwiki/files/sort` | 批量排序（拖拽排序） |
| `POST /api/v1/nextwiki/files/batch/move` | 批量移动（异步任务） |
| `POST /api/v1/nextwiki/files/batch/delete` | 批量删除（异步任务） |
| `POST /api/v1/nextwiki/files/folders` | 创建文件夹 |
| `POST /api/v1/nextwiki/files/{nodeId}/lock` | 锁定文件 |
| `POST /api/v1/nextwiki/files/{nodeId}/unlock` | 解锁文件 |

### 下载 / 分享 / 搜索 / 回收站 / 配额 / 标签 / 批量导入 / AI 分析

| 端点 | 作用 |
|---|---|
| `POST /api/v1/nextwiki/download/{nodeId}` | 下载文件（支持 Range 断点续传） |
| `POST /api/v1/nextwiki/download/folder/{folderId}` | 打包下载文件夹 |
| `POST /api/v1/nextwiki/shares` | 创建分享链接 |
| `POST /api/v1/nextwiki/shares/verify` | 验证分享访问 |
| `POST /api/v1/nextwiki/search` | 搜索文件（POST） |
| `POST /api/v1/nextwiki/search/rebuild` | 重建索引 |
| `GET /api/v1/nextwiki/search/suggest` `GET /api/v1/nextwiki/search/did-you-mean` | 搜索建议 / 纠错 |
| `GET /api/v1/nextwiki/trash/list` | 查询回收站 |
| `POST /api/v1/nextwiki/trash/{trashItemId}/restore` | 恢复 |
| `DELETE /api/v1/nextwiki/trash/empty` | 清空回收站 |
| `GET /api/v1/nextwiki/quota/info` | 查询配额 |
| `POST /api/v1/nextwiki/quota/set` | 设置配额 |
| `POST /api/v1/nextwiki/tags` | 创建标签 |
| `POST /api/v1/nextwiki/import/batch-upload` | 批量上传 |
| `POST /api/v1/nextwiki/import/zip` | ZIP 导入 |
| `GET /api/v1/nextwiki/analysis/overview` | 存储概览 |
| `POST /api/v1/nextwiki/analysis/summary` | 生成文档摘要 |
| `POST /api/v1/nextwiki/ai/summary` `GET /api/v1/nextwiki/ai/status` | AI 文档摘要 / 任务状态 |
| `POST /api/v1/nextwiki/storage/presigned-upload` `/presigned-download` | 预签名直传 / 直下 |

### 收藏夹 / 最近访问（S2-P1-06）

| 端点 | 作用 |
|---|---|
| `GET /api/v1/nextwiki/favorites` | 查询收藏列表 |
| `POST /api/v1/nextwiki/favorites/{nodeId}` | 添加收藏 |
| `DELETE /api/v1/nextwiki/favorites/{nodeId}` | 取消收藏 |
| `PUT /api/v1/nextwiki/favorites/{nodeId}/sort` | 更新收藏排序 |
| `GET /api/v1/nextwiki/recent` | 查询最近访问列表 |
| `POST /api/v1/nextwiki/recent/{nodeId}` | 记录访问 |
| `DELETE /api/v1/nextwiki/recent` | 清空最近访问 |

### 空间管理（S3-P2-01）

| 端点 | 作用 |
|---|---|
| `GET /api/v1/nextwiki/spaces` | 查询空间列表 |
| `POST /api/v1/nextwiki/spaces` | 创建空间 |
| `GET /api/v1/nextwiki/spaces/{spaceId}` | 获取空间详情 |
| `PUT /api/v1/nextwiki/spaces/{spaceId}` | 更新空间 |
| `DELETE /api/v1/nextwiki/spaces/{spaceId}` | 删除空间 |
| `POST /api/v1/nextwiki/spaces/{spaceId}/members` | 添加成员 |
| `DELETE /api/v1/nextwiki/spaces/{spaceId}/members/{userId}` | 移除成员 |
| `GET /api/v1/nextwiki/spaces/{spaceId}/members` | 成员列表 |

### 空间模板（S4-P3-02）

| 端点 | 作用 |
|---|---|
| `GET /api/v1/nextwiki/templates` | 查询模板列表 |
| `GET /api/v1/nextwiki/templates/{templateId}` | 获取模板详情 |
| `POST /api/v1/nextwiki/templates` | 创建自定义模板 |
| `PUT /api/v1/nextwiki/templates/{templateId}` | 更新模板 |
| `DELETE /api/v1/nextwiki/templates/{templateId}` | 删除模板 |
| `POST /api/v1/nextwiki/templates/{templateId}/use` | 使用模板创建空间 |

### WOPI 在线编辑

| 端点 | 作用 |
|---|---|
| `GET /api/v1/nextwiki/wopi/files/{fileId}` | CheckFileInfo |
| `GET /api/v1/nextwiki/wopi/files/{fileId}/contents` | GetFile |
| `POST /api/v1/nextwiki/wopi/files/{fileId}/contents` | PutFile |
| `POST /api/v1/nextwiki/wopi/files/{fileId}/lock` | Lock |
| `POST /api/v1/nextwiki/wopi/files/{fileId}/unlock` | Unlock |

## 配置项

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `nextwiki.upload.max-file-size` | `524288000` | 最大文件大小（500MB） |
| `nextwiki.upload.allowed-types` | （空） | 允许的文件类型 |
| `nextwiki.upload.conflict-strategy` | `KEEP_BOTH` | 同名冲突策略 |
| `nextwiki.preview.libreoffice-path` | `soffice` | LibreOffice 可执行文件路径 |
| `nextwiki.ai.llm-enabled` | `false` | 是否启用 LLM 摘要 |
| `nextwiki.cdn.enabled` | `false` | 是否启用 CDN |
| `nextwiki.virus-scan.enabled` | `false` | 是否启用病毒扫描 |
| `nextwiki.ocr.enabled` | `false` | 是否启用 OCR |
| `nextwiki.download.rate-limit-per-minute` | `30` | 下载限流 |
| `nextwiki.wopi.editor-url` | （空） | 在线编辑器 URL |

## 启动

```bash
cd ydsz-cloud
mvn -pl ydsz-common -am install -DskipTests
mvn -pl ydsz-nextwiki spring-boot:run
```

## 技术栈

- Spring Boot 4.x + Spring Cloud (Nacos)
- MyBatis-Plus
- Redis（分布式锁、限流、缓存）
- LibreOffice（文档预览）
- ClamAV（病毒扫描）
- Tesseract（OCR）
- Micrometer（监控指标）
- WebSocket（实时通知）
- 冷数据归档（定时任务迁移低频文件）

## 常见问题

### Q1：文件上传失败 "文件过大"

检查 `nextwiki.upload.max-file-size`（默认 500MB）以及 Spring 的 `spring.servlet.multipart.max-file-size` / `max-request-size`。

### Q2：Office 文件预览失败

1. 检查 `nextwiki.preview.libreoffice-path` 是否指向有效的 `soffice` 可执行文件
2. LibreOffice 进程需要执行权限（Linux: `chmod +x`）
3. 临时目录可写

### Q3：搜索索引不同步

文件操作后搜索索引未更新，可调用 `POST /api/v1/nextwiki/search/rebuild` 全量重建索引。

### Q4：WOPI 在线编辑无法保存

1. 检查 `nextwiki.wopi.editor-url` 是否配置
2. WOPI 协议要求 OnlyOffice/Collabora 服务可访问 nextwiki 后端
3. 文件被锁定时，只有锁持有者可保存

---

> 本模块是 YDSZ 的**文件存储与知识协作中心**，复用 common-file（存储抽象）/ common-search（全文检索）/ common-lock（分布式锁）公共能力。
> 严禁在本模块中重新实现文件存储/搜索/锁逻辑。
