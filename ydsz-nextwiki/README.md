# ydsz-nextwiki

> 网盘知识库服务（Next Wiki）— 文件存储 / 文档解析 / 全文搜索 / 在线预览 / 版本控制 / 分享协作

## 模块定位

| 属性 | 值 |
|---|---|
| **类型** | 部署单元（独立启动） |
| **端口** | **9003**（主服务，按构建顺序 4/10）；**8081**（移动端 app 模块） |
| **服务名** | `ydsz-nextwiki` |
| **构建顺序** | 4/10 |
| **数据库** | PostgreSQL |
| **依赖** | Nacos、PostgreSQL、Redis、MinIO |
| **公共依赖** | common-web / common-file / common-search / common-jdbc / common-lock / common-redis / common-auth / common-cache / common-thread / common-audit / common-tenant / common-event / common-notify / common-docs / common-sentry / common-safe / common-domain / common-json / common-util |

> **端口提示**：本模块 Web 控制台默认 `9003`，与 `ydsz-userinfo-app`（移动端入口，同为 `9003`）默认端口相同。两者通常不会同机部署（Web 控制台 vs 移动端入口），若需同机运行，须通过 Nacos `ydsz-nextwiki-{env}.yaml` / `ydsz-userinfo-{env}.yaml` 将其中一个改为其他端口。

## 核心职责

本模块是 YDSZ 的**一体化网盘知识库平台**，融合文件存储、文档解析、全文搜索、在线预览、版本控制、分享协作。

| 能力 | 说明 |
|---|---|
| **文件管理** | 上传（SHA-256 秒传）、下载（断点续传）、复制、移动、重命名、删除、拖拽排序 |
| **目录树** | 创建/移动/重命名/递归路径更新 |
| **版本控制** | 版本历史、回滚、版本对比（diff）、最多保留 20 版本 |
| **分享与 ACL** | 分享链接（密码/过期/次数限制/定向分享）、ACL 权限（读/写/删/分享/下载） |
| **搜索** | 文件名+路径+标签+内容全文搜索、索引同步、重建、搜索历史、热门搜索、高级语法搜索（基于 common-search） |
| **预览** | Office→PDF（LibreOffice）、图片缩略图、动态水印、异步预览生成、预览类型识别 |
| **配额** | 用户/租户/项目级配额管理、按文件类型配额 |
| **回收站** | 30 天保留、恢复、永久删除、自动清理、批量恢复 |
| **标签** | 创建/绑定/推荐 |
| **批量操作** | 批量删除/移动（同步+异步）、ZIP 导入（炸弹防护）、文件夹打包下载 |
| **分片上传** | 大文件分片上传、断点续传、已上传分片查询（基于 common-file） |
| **AI 摘要** | TextRank 本地模式 + LLM 模式、关键词提取、文档分类 |
| **在线编辑** | WOPI 协议（OnlyOffice/Collabora 集成）、WOPI Token 验证 |
| **文件评论** | 评论/回复/批注/解决标记、@ 提及通知 |
| **文件锁定** | Check-out/Check-in 防并发编辑（status 字段隔离） |
| **收藏夹/最近访问** | 快捷访问入口、收藏管理、排序、计数 |
| **空间管理** | 知识库空间（Space）聚合根、空间成员角色（RBAC）、空间配额、空间归档 |
| **文档模板** | 预定义空间结构模板、自定义模板、快速创建空间 |
| **存储直传** | Presigned URL 预签名上传/下载（直连对象存储） |
| **存储分析** | 存储概览、按类型统计、大文件 Top-N 识别 |
| **安全** | ClamAV 病毒扫描、OCR 文字识别、CDN 集成、动态水印、SQL 防火墙、SQL 审计 |
| **可观测性** | 健康检查、Micrometer 指标、审计日志、分布式锁、缓存命中率、Sentry 告警 |

## DDD 分层结构

```
ydsz-nextwiki/
├── pom.xml
├── ydsz-nextwiki-api/                 # API 层：Feign Client + DTO
├── ydsz-nextwiki-domain/              # 领域层：领域服务 + Repository 接口 + VO/DTO/事件/枚举
│   └── src/main/java/com/njydsz/nextwiki/domain/
│       ├── dto/                       # 数据传输对象（FileNodeDTO / FileVersionDTO / SpaceMemberDTO / SpaceTemplateDTO 等）
│       ├── vo/                        # 视图对象（17 个：FileNodeVO / FileVersionVO / FileAclVO / FileCommentVO / FileTagVO / FileStatVO / SearchIndexVO / SearchResultVO / ShareLinkVO / ShareAccessLogVO / ShareRecipientVO / SpaceVO / StorageQuotaVO / TagVO / TrashItemVO / UserFavoriteVO / UserRecentVO）
│       ├── query/                     # 查询对象（FileNodeQuery / FileVersionQuery / SearchQuery / SearchIndexQuery / FileAclQuery）
│       ├── repository/                # 仓储接口（16 个）
│       ├── service/                   # 领域服务（10 个：FolderDomainService / FileVersionDomainService / QuotaDomainService / SearchDomainService / ShareLinkDomainService / ShareAccessLogDomainService / SpaceDomainService / TagDomainService / TrashDomainService / FilePermissionDomainService）
│       ├── event/                     # 领域事件（3 个：FileOperatedEvent / AuditEvent / FileVersionSnapshotEvent）
│       └── enums/                     # 枚举（NextwikiEnums / NextwikiExceptionCode）
├── ydsz-nextwiki-infra/               # 基础设施层：DO 实体 + Mapper + 仓储实现
│   └── src/main/java/com/njydsz/nextwiki/infra/
│       ├── entity/                    # 持久化实体（17 个：FileNode / FileVersion / FileAcl / FileComment / FileTag / SearchIndex / ShareLink / ShareAccessLog / ShareRecipient / Space / SpaceMember / SpaceTemplate / StorageQuota / Tag / TrashItem / UserFavorite / UserRecent）
│       ├── mapper/                    # MyBatis Mapper（16 个）
│       ├── repository/                # 仓储实现（16 个）
│       └── converter/                 # MapStruct 转换器（DO ↔ VO / DTO）
├── ydsz-nextwiki-server/              # 应用层（命名沿 server）：应用服务 + 配置 + 缓存 + 监听器 + 定时任务 + WebSocket
├── ydsz-nextwiki-web/                 # Web 层：Controller + 启动类（端口 9003）
│   └── src/main/java/com/njydsz/nextwiki/web/
│       ├── NextwikiApplication.java   # 启动类（位于 web 根包下）
│       └── controller/                # 21 个 Controller
│           ├── FileController.java          # /api/v1/nextwiki/files
│           ├── FileChunkController.java     # /api/v1/nextwiki/files/chunk
│           ├── FileBatchController.java     # /api/v1/nextwiki/files/batch/*（同步+异步任务）
│           ├── FileLockController.java      # /api/v1/nextwiki/files/{nodeId}/lock
│           ├── FileCommentController.java   # /api/v1/nextwiki/comments
│           ├── DownloadController.java      # /api/v1/nextwiki/download
│           ├── ShareController.java         # /api/v1/nextwiki/shares
│           ├── SearchController.java        # /api/v1/nextwiki/search
│           ├── TrashController.java         # /api/v1/nextwiki/trash
│           ├── QuotaController.java         # /api/v1/nextwiki/quota
│           ├── TagController.java           # /api/v1/nextwiki/tags
│           ├── BatchImportController.java   # /api/v1/nextwiki/import
│           ├── AnalysisController.java      # /api/v1/nextwiki/analysis
│           ├── PreviewController.java       # /api/v1/nextwiki/preview
│           ├── SpaceController.java         # /api/v1/nextwiki/spaces
│           ├── SpaceTemplateController.java # /api/v1/nextwiki/templates
│           ├── UserFavoriteController.java  # /api/v1/nextwiki/favorites
│           ├── UserRecentController.java    # /api/v1/nextwiki/recent
│           ├── WopiController.java          # /api/v1/nextwiki/wopi
│           ├── ai/AiController.java         # /api/v1/nextwiki/ai（文档摘要/状态）
│           └── storage/PresignedUrlController.java # /api/v1/nextwiki/storage（预签名上传/下载）
└── ydsz-nextwiki-app/                 # 移动端 App 模块：端口 8081，独立启动入口
    └── src/main/java/com/njydsz/nextwiki/app/
        ├── NextwikiAppApplication.java # 移动端启动类
        ├── config/                    # 自动配置（NextwikiAppAutoConfiguration / NextwikiAppProperties）
        ├── health/                    # 健康指标（NextwikiAppHealthIndicator）
        └── openapi/                   # OpenAPI 配置（NextwikiAppOpenApiConfiguration）
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
| `GET /api/v1/nextwiki/files/chunk/{uploadId}/uploaded-chunks` | 查询已上传分片列表（断点续传） |
| `POST /api/v1/nextwiki/files/{nodeId}/copy` | 复制文件 |
| `PUT /api/v1/nextwiki/files/sort` | 批量排序（拖拽排序） |
| `POST /api/v1/nextwiki/files/folders` | 创建文件夹 |
| `POST /api/v1/nextwiki/files/{nodeId}/lock` | 锁定文件 (P0-R3：使用 status 字段) |
| `POST /api/v1/nextwiki/files/{nodeId}/unlock` | 解锁文件 |
| `GET /api/v1/nextwiki/files/list` | 列出目录内容（支持排序/过滤/分页） |
| `PUT /api/v1/nextwiki/files/{nodeId}/move` | 移动文件/文件夹 |
| `PUT /api/v1/nextwiki/files/{nodeId}/rename` | 重命名文件/文件夹 |
| `DELETE /api/v1/nextwiki/files/{nodeId}` | 删除文件（移入回收站） |
| `GET /api/v1/nextwiki/files/{nodeId}/versions` | 获取版本历史 |
| `POST /api/v1/nextwiki/files/{nodeId}/versions/{ver}/rollback` | 版本回滚 |
| `GET /api/v1/nextwiki/files/{nodeId}/versions/diff` | 对比版本差异（文本文件，行粒度） |
| `POST /api/v1/nextwiki/files/batch/delete` | 批量删除（同步） |
| `POST /api/v1/nextwiki/files/batch/move` | 批量移动（同步） |
| `POST /api/v1/nextwiki/files/batch/async-delete` | 异步批量删除（大批量 > 10，返回任务 ID） |
| `POST /api/v1/nextwiki/files/batch/async-move` | 异步批量移动 |
| `GET /api/v1/nextwiki/files/batch/task/{taskId}` | 查询异步批量任务状态 |
| `PUT /api/v1/nextwiki/files/{nodeId}/star` | 切换文件星标状态 |

### 下载 / 分享 / 搜索 / 回收站 / 配额 / 标签 / 批量导入 / AI 分析

| 端点 | 作用 |
|---|---|
| `POST /api/v1/nextwiki/download/{nodeId}` | 下载文件（支持 Range 断点续传） |
| `POST /api/v1/nextwiki/download/folder/{folderId}` | 打包下载文件夹（ZIP） |
| `POST /api/v1/nextwiki/download/{nodeId}/signed-url` | 生成签名下载 URL（时效+IP 绑定） |
| `GET /api/v1/nextwiki/download/signed/{sign}` | 通过签名 URL 下载文件 |
| `POST /api/v1/nextwiki/shares` | 创建分享链接（支持定向分享） |
| `POST /api/v1/nextwiki/shares/verify` | 验证分享访问（限流 50 QPS） |
| `DELETE /api/v1/nextwiki/shares/{shareId}` | 撤销分享 |
| `GET /api/v1/nextwiki/shares/my` | 查询我的分享列表 |
| `GET /api/v1/nextwiki/shares/received` | 查询我收到的分享 |
| `GET /api/v1/nextwiki/shares/{shareId}/logs` | 查询分享访问日志 |
| `GET /api/v1/nextwiki/shares/{shareId}/recipients` | 查询分享目标用户 |
| `POST /api/v1/nextwiki/search` | 综合搜索（POST，多维度筛选） |
| `POST /api/v1/nextwiki/search/rebuild` | 重建全量索引 |
| `GET /api/v1/nextwiki/search/suggest` | 搜索自动补全建议 |
| `GET /api/v1/nextwiki/search/did-you-mean` | "您是不是要找"纠错建议 |
| `GET /api/v1/nextwiki/search/advanced` | 高级语法搜索（字段限定/布尔运算/短语匹配） |
| `GET /api/v1/nextwiki/search/history` | 获取搜索历史（最近 20 条，保留 30 天） |
| `DELETE /api/v1/nextwiki/search/history` | 清除搜索历史 |
| `GET /api/v1/nextwiki/search/hot` | 获取热门搜索排行榜（Top 10） |
| `GET /api/v1/nextwiki/trash/list` | 查询回收站 |
| `POST /api/v1/nextwiki/trash/{trashItemId}/restore` | 恢复 |
| `POST /api/v1/nextwiki/trash/batch-restore` | 批量恢复 |
| `DELETE /api/v1/nextwiki/trash/{trashItemId}` | 永久删除 |
| `DELETE /api/v1/nextwiki/trash/empty` | 清空回收站 |
| `GET /api/v1/nextwiki/quota/info` | 查询配额（user/tenant/project） |
| `POST /api/v1/nextwiki/quota/set` | 设置配额（管理员操作） |
| `POST /api/v1/nextwiki/tags` | 创建标签 |
| `GET /api/v1/nextwiki/tags` | 查询所有标签 |
| `POST /api/v1/nextwiki/tags/bind` | 为文件绑定标签 |
| `GET /api/v1/nextwiki/tags/file/{fileNodeId}` | 查询文件标签 |
| `GET /api/v1/nextwiki/tags/recommend/{fileNodeId}` | 智能推荐标签 |
| `POST /api/v1/nextwiki/import/batch-upload` | 批量上传 |
| `POST /api/v1/nextwiki/import/zip` | ZIP 导入 |
| `GET /api/v1/nextwiki/analysis/overview` | 存储概览 |
| `GET /api/v1/nextwiki/analysis/by-type` | 按文件类型统计 |
| `GET /api/v1/nextwiki/analysis/top-large-files` | 大文件 Top-N |
| `POST /api/v1/nextwiki/analysis/summary` | 生成文档 AI 摘要（限流 50 QPS） |
| `POST /api/v1/nextwiki/ai/summary` | AI 文件智能摘要 |
| `GET /api/v1/nextwiki/ai/status` | 查询 AI 服务可用状态（含支持的文件类型） |
| `POST /api/v1/nextwiki/storage/presigned-upload` | 生成预签名上传 URL（直传对象存储） |
| `POST /api/v1/nextwiki/storage/presigned-download` | 生成预签名下载 URL |

### 文件评论

| 端点 | 作用 |
|---|---|
| `GET /api/v1/nextwiki/comments/file/{fileNodeId}` | 查询文件评论列表 |
| `POST /api/v1/nextwiki/comments` | 添加评论/回复（支持 @ 提及） |
| `DELETE /api/v1/nextwiki/comments/{commentId}` | 删除评论（软删除） |
| `POST /api/v1/nextwiki/comments/{commentId}/resolve` | 标记评论已解决 |

### 文档预览

| 端点 | 作用 |
|---|---|
| `POST /api/v1/nextwiki/preview/{fileNodeId}/generate` | 异步生成预览（限流 50 QPS） |
| `GET /api/v1/nextwiki/preview/supported?suffix=pdf` | 检查文件是否支持预览 |
| `GET /api/v1/nextwiki/preview/type?suffix=pdf` | 获取预览类型（pdf/image/text/video/audio/code/none） |

### 收藏夹 / 最近访问

| 端点 | 作用 |
|---|---|
| `GET /api/v1/nextwiki/favorites` | 查询收藏列表 |
| `POST /api/v1/nextwiki/favorites/{nodeId}` | 添加收藏 |
| `DELETE /api/v1/nextwiki/favorites/{nodeId}` | 取消收藏 |
| `GET /api/v1/nextwiki/favorites/{nodeId}/is-favorited` | 检查是否已收藏 |
| `POST /api/v1/nextwiki/favorites/{nodeId}/sort` | 更新收藏排序 |
| `GET /api/v1/nextwiki/favorites/count` | 查询收藏数量 |
| `GET /api/v1/nextwiki/recent` | 查询最近访问列表 |
| `POST /api/v1/nextwiki/recent/{nodeId}` | 记录访问 |
| `DELETE /api/v1/nextwiki/recent` | 清空最近访问 |
| `DELETE /api/v1/nextwiki/recent/{nodeId}` | 删除单条访问记录 |
| `GET /api/v1/nextwiki/recent/count` | 查询最近访问记录数量 |

### 空间管理

| 端点 | 作用 |
|---|---|
| `GET /api/v1/nextwiki/spaces` | 查询空间列表 |
| `POST /api/v1/nextwiki/spaces` | 创建空间 |
| `GET /api/v1/nextwiki/spaces/{spaceId}` | 获取空间详情 |
| `PUT /api/v1/nextwiki/spaces/{spaceId}` | 更新空间 |
| `POST /api/v1/nextwiki/spaces/{spaceId}/archive` | 归档空间 |
| `DELETE /api/v1/nextwiki/spaces/{spaceId}` | 删除空间（逻辑删除） |
| `POST /api/v1/nextwiki/spaces/{spaceId}/members` | 添加成员 |
| `DELETE /api/v1/nextwiki/spaces/{spaceId}/members/{userId}` | 移除成员 |
| `GET /api/v1/nextwiki/spaces/{spaceId}/members` | 成员列表 |

### 空间模板

| 端点 | 作用 |
|---|---|
| `GET /api/v1/nextwiki/templates` | 查询模板列表（支持按分类过滤） |
| `GET /api/v1/nextwiki/templates/{templateId}` | 获取模板详情 |
| `POST /api/v1/nextwiki/templates` | 创建自定义模板 |
| `PUT /api/v1/nextwiki/templates/{templateId}` | 更新模板 |
| `DELETE /api/v1/nextwiki/templates/{templateId}` | 删除模板 |
| `POST /api/v1/nextwiki/templates/{templateId}/use` | 使用模板创建空间 |

### WOPI 在线编辑

| 端点 | 作用 |
|---|---|
| `GET /api/v1/nextwiki/wopi/files/{fileId}` | CheckFileInfo（返回文件元信息） |
| `GET /api/v1/nextwiki/wopi/files/{fileId}/contents` | GetFile（获取文件内容） |
| `POST /api/v1/nextwiki/wopi/files/{fileId}/contents` | PutFile（保存编辑器内容，支持锁持有者校验） |
| `POST /api/v1/nextwiki/wopi/files/{fileId}/lock` | Lock（锁定文件防并发） |
| `POST /api/v1/nextwiki/wopi/files/{fileId}/unlock` | Unlock（解锁文件） |

## 数据库

实体 `@TableName` 共映射 **17 张表**。DDL 以 SQL 脚本形式维护在 `ydsz-nextwiki-infra/src/main/resources/db/`（含 V1 初始建表 + V2~V6 增量脚本），**需手动执行初始化**——项目规范禁止 Flyway / Liquibase 等迁移框架，不存在自动迁移。

| 表名 | 实体类 | 用途 |
|---|---|---|
| `ydsz_wiki_file_node` | `FileNode` | 文件/目录节点聚合根 |
| `ydsz_wiki_file_version` | `FileVersion` | 文件版本历史 |
| `ydsz_wiki_file_acl` | `FileAcl` | ACL 权限控制 |
| `ydsz_wiki_file_comment` | `FileComment` | 文件评论 |
| `ydsz_wiki_file_tag` | `FileTag` | 文件-标签关联 |
| `ydsz_wiki_search_index` | `SearchIndex` | 搜索索引 |
| `ydsz_wiki_share_link` | `ShareLink` | 分享链接 |
| `ydsz_wiki_share_access_log` | `ShareAccessLog` | 分享访问日志 |
| `ydsz_wiki_share_recipient` | `ShareRecipient` | 分享接收人 |
| `ydsz_wiki_space` | `Space` | 知识库空间聚合根 |
| `ydsz_wiki_space_member` | `SpaceMember` | 空间成员（RBAC） |
| `ydsz_wiki_space_template` | `SpaceTemplate` | 空间模板 |
| `ydsz_wiki_storage_quota` | `StorageQuota` | 存储配额 |
| `ydsz_wiki_tag` | `Tag` | 标签定义 |
| `ydsz_wiki_trash_item` | `TrashItem` | 回收站 |
| `ydsz_wiki_user_favorite` | `UserFavorite` | 用户收藏夹 |
| `ydsz_wiki_user_recent` | `UserRecent` | 用户最近访问 |

## 配置项

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `nextwiki.upload.max-file-size` | `524288000` | 最大文件大小（500MB） |
| `nextwiki.upload.allowed-types` | （空） | 允许的文件类型 |
| `nextwiki.upload.conflict-strategy` | `KEEP_BOTH` | 同名冲突策略 |
| `nextwiki.upload.chunk-temp-dir` | `${java.io.tmpdir}/nextwiki-chunk` | 分片上传临时目录 |
| `nextwiki.preview.libreoffice-path` | `soffice` | LibreOffice 可执行文件路径 |
| `nextwiki.preview.temp-dir` | `${java.io.tmpdir}/nextwiki-preview` | 预览临时目录 |
| `nextwiki.thumbnail.temp-dir` | `${java.io.tmpdir}/nextwiki-thumbnail` | 缩略图临时目录 |
| `nextwiki.ai.llm-enabled` | `false` | 是否启用 LLM 摘要 |
| `nextwiki.ai.llm-api-url` | （空） | LLM API 地址 |
| `nextwiki.ai.llm-api-key` | （空） | LLM API Key |
| `nextwiki.cdn.enabled` | `false` | 是否启用 CDN |
| `nextwiki.cdn.provider` | `aliyun` | CDN 服务商 |
| `nextwiki.cdn.domain` | （空） | CDN 域名 |
| `nextwiki.cdn.access-key` | （空） | CDN Access Key |
| `nextwiki.cdn.secret-key` | （空） | CDN Secret Key |
| `nextwiki.virus-scan.enabled` | `false` | 是否启用病毒扫描 |
| `nextwiki.virus-scan.host` | `localhost` | ClamAV 主机地址 |
| `nextwiki.virus-scan.port` | `3310` | ClamAV 端口 |
| `nextwiki.ocr.enabled` | `false` | 是否启用 OCR |
| `nextwiki.ocr.provider` | `tesseract` | OCR 服务商 |
| `nextwiki.ocr.tesseract-path` | `tesseract` | Tesseract 可执行文件路径 |
| `nextwiki.ocr.language` | `chi_sim+eng` | OCR 识别语言 |
| `nextwiki.download.rate-limit-per-minute` | `30` | 用户维度下载限流（次/分钟） |
| `nextwiki.download.ip-rate-limit-per-minute` | `100` | IP 维度下载限流（次/分钟） |
| `nextwiki.download.signed-url-expire-seconds` | `3600` | 签名 URL 过期时间（秒） |
| `nextwiki.wopi.editor-url` | （空） | 在线编辑器 URL |
| `nextwiki.wopi.access-token` | （空） | WOPI Token 验证密钥 |

## 启动

### 主服务（Web 层）

```bash
cd ydsz-cloud
mvn -pl ydsz-common -am install -DskipTests
mvn -pl ydsz-nextwiki/spring-boot:run -DskipTests
# 或在 ydsz-nextwiki-web 目录下
mvn spring-boot:run
```

### 移动端 App 模块

```bash
cd ydsz-nextwiki/ydsz-nextwiki-app
mvn spring-boot:run
# 启动端口: 8081
```

## 技术栈

- Spring Boot 4.x + Spring Cloud Alibaba (Nacos Discovery + Config)
- MyBatis-Plus (Spring Boot 4.x 专用 starter)
- MapStruct（DO ↔ VO/DTO 对象转换）
- Redis（分布式锁、限流、缓存）
- LibreOffice（文档预览转换）
- ClamAV（病毒扫描）
- Tesseract（OCR 文字识别）
- Micrometer + Prometheus（监控指标）
- Spring Boot Actuator（健康检查）
- WebSocket（实时通知、批量任务进度推送）
- 冷数据归档（定时任务迁移低频文件）
- SQL 防火墙 + SQL 审计（安全加固）
- Sentry（告警收敛 + SLA 监控）
- springdoc-openapi（Swagger UI / OpenAPI 3）

## 安全机制

| 层级 | 措施 |
|---|---|
| **接口鉴权** | `@AuthApiPermission` 注解 + NEXTWIKI_* 权限码校验 |
| **幂等防重** | `@Idempotent` 注解（5s TTL），覆盖所有写操作 |
| **审计日志** | `@Audit` 注解异步落库（操作人/类型/动作/内容） |
| **限流** | `@RateLimit` 注解（分享验证 50 QPS、AI 摘要 50 QPS、预览生成 50 QPS） |
| **SQL 防火墙** | 拦截 DDL（DROP/TRUNCATE）、无 WHERE 写操作、多语句注入、GRANT/REVOKE |
| **SQL 审计** | 写操作落盘备查（可配置是否记录 SELECT） |
| **WOPI Token** | `X-WOPI-Authorization` 请求头验证（防未授权访问） |
| **签名 URL** | HMAC 签名 + 时效 + IP 绑定（防链接外传） |
| **文件锁定** | Check-out/Check-in 悲观锁（防并发编辑） |
| **多租户** | TenantContextHolder 隔离租户数据 |

## 常见问题

### Q1：文件上传失败 "文件过大"

检查 `nextwiki.upload.max-file-size`（默认 500MB）以及 Spring 的 `spring.servlet.multipart.max-file-size` / `max-request-size`（默认 500MB，在 ydsz-nextwiki-web/application.yml 中配置）。

### Q2：Office 文件预览失败

1. 检查 `nextwiki.preview.libreoffice-path` 是否指向有效的 `soffice` 可执行文件
2. LibreOffice 进程需要执行权限（Linux: `chmod +x`）
3. 临时目录 `nextwiki.preview.temp-dir` 确保可写

### Q3：搜索索引不同步

文件操作后搜索索引未更新，可调用 `POST /api/v1/nextwiki/search/rebuild` 全量重建索引（仅管理员权限）。

### Q4：WOPI 在线编辑无法保存

1. 检查 `nextwiki.wopi.editor-url` 是否配置
2. WOPI 协议要求 OnlyOffice/Collabora 服务可访问 nextwiki 后端
3. 文件被锁定时，只有锁持有者可通过 `X-WOPI-Lock` 头保存
4. 检查 `nextwiki.wopi.access-token` 是否匹配（P1-R5 修复后增加 Token 验证）

### Q5：收藏夹 / 最近访问端点返回 404

`UserFavoriteController` 与 `UserRecentController` 从 1.0.0 起独立部署，需确认 nextwiki 服务版本 >= 1.1.0。收藏排序更新使用 `POST /favorites/{nodeId}/sort`（非 PUT），注意 HTTP 方法。

### Q6：批量下载/移动任务状态查询

大批量操作（> 10 个节点）建议使用异步接口 `POST /files/batch/async-delete` 或 `POST /files/batch/async-move`，返回任务 ID 后通过 `GET /files/batch/task/{taskId}` 轮询进度。

---

> 本模块是 YDSZ 的**文件存储与知识协作中心**，复用 common-file（存储抽象）/ common-search（全文检索）/ common-lock（分布式锁）/ common-cache（缓存）/ common-thread（线程池）/ common-audit（审计）/ common-event（领域事件）/ common-tenant（多租户）公共能力。
> 严禁在本模块中重新实现文件存储/搜索/锁/缓存/审计逻辑。
