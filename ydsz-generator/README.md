# ydzs-generator — 代码生成器聚合 BOM

> 基于数据库表结构自动生成 DDD 分层 CRUD 全栈代码

## 模块定位

`ydsz-generator` 是 Ydsz Cloud 的代码生成引擎，通过连接目标数据库读取表/列/索引元数据，结合 Apache Velocity 模板一键生成**符合云顶编码规范**的 DDD 分层 Java 代码与 Vue 前端代码。支持单表生成与批量全库生成两种模式，生成的代码可直接复制到业务模块中使用。

## 模块结构

```
ydsz-generator/
├── ydzz-generator-domain/    # 领域层：表/列元数据模型 + Repository 接口
├── ydzz-generator-infra/     # 基础设施层：Repository 实现 + Mapper
├── ydzz-generator-server/    # 应用服务层：CodeGenService + TableMetadataService + EntityReverseService
├── ydzz-generator-web/       # Web 层：REST Controller + 启动类 GeneratorWebApplication (:9090)
├── ydzz-generator-app/       # CLI 层：GeneratorCliApplication（命令行工具形态，待扩展）
└── ydzz-generator-api/       # API 层：对外契约（当前预留）
```

## 端口与访问

| 项目 | 值 |
|------|-----|
| 服务端口 | `9090` |
| context-path | `/gen` |
| 应用名 | `ydsz-generator` |

## REST API

| 方法 | 路径 | Controller | 说明 |
|------|------|------------|------|
| GET | `/gen/tables` | TableMetaController | 查询数据源下的表清单 |
| GET | `/gen/tables/{tableName}/columns` | TableMetaController | 查询表的列元数据 |
| POST | `/gen/generate` | CodeGenController | 单表代码生成 |
| POST | `/gen/generate/all` | CodeGenController | 全库批量代码生成 |
| POST | `/gen/datasource` | DatasourceController | 新增数据源配置 |
| GET | `/gen/datasource` | DatasourceController | 查询数据源列表 |
| POST | `/gen/reverse` | ReverseController | 数据库逆向工程 |
| GET | `/gen/template` | TemplateController | 查询模板列表 |
| POST | `/gen/template` | TemplateController | 新增/编辑模板 |
| GET | `/gen/history` | HistoryController | 查询生成历史 |
| POST | `/gen/import-export/import` | ImportExportController | 模板导入 |
| GET | `/gen/import-export/export/{groupId}` | ImportExportController | 模板导出 |

## Velocity 产物清单（16 种）

代码生成器通过以下 16 个 Velocity 模板产出完整的 DDD 分层代码：

### Java 后端（14 种产物）

| 模板文件 | 产物 | 目标路径 |
|----------|------|----------|
| `entity.vm` | Entity 实体类 | `domain/entity/` |
| `mapper.vm` | MyBatis Mapper 接口 + XML | `infra/mapper/` |
| `repository.vm` | Repository 接口 + 实现 | `domain/repository/` + `infra/repository/` |
| `service.vm` | Service 接口 | `server/service/` |
| `serviceImpl.vm` | Service 实现 | `server/service/impl/` |
| `controller.vm` | REST Controller | `web/controller/` |
| `dto.vm` | DTO 数据传输对象 | `domain/dto/` |
| `vo.vm` | VO 视图对象 | `domain/vo/` |
| `query.vm` | Query 查询对象 | `domain/query/` |
| `converter.vm` | MapStruct Converter | `domain/converter/` |
| `assembler.vm` | MapStruct Assembler | `api/assembler/` |
| `enum.vm` | 枚举类 | `domain/enums/` |
| `feign.vm` | Feign Client 接口 | `api/` |
| `fallbackFactory.vm` | Feign FallbackFactory | `api/fallback/` |

### Vue 前端（2 种产物）

| 模板文件 | 产物 | 目标路径 |
|----------|------|----------|
| `vue/api.vm` | API 请求层 | `api/` |
| `vue/index.vm` | 页面视图 | `views/` |

## 核心 Service

| Service | 职责 |
|---------|------|
| `TableMetadataService` | 连接数据源读取表/列/索引元数据 |
| `EntityReverseService` | 数据库逆向工程（正则预编译提取命名约定） |
| `CodeGenService` | 代码生成核心引擎（模板渲染 + 文件输出） |
| `DatasourceService` | 数据源配置 CRUD |
| `TemplateService` | 代码模板管理 |
| `TemplateGroupService` | 模板分组管理 |
| `TemplateImportExportService` | 模板导入导出（ZipInputStream/ZipOutputStream） |
| `GenHistoryService` | 代码生成历史记录 |

## 配置项

| 配置键 | 默认值 | 说明 |
|--------|--------|------|
| `generator.output-dir` | `./generated` | 代码输出根目录 |
| `generator.default-author` | `ydsz-generator` | 默认作者（Javadoc `@author`） |
| `generator.default-package` | `com.njydsz` | 默认基础包名 |
| `generator.template-group` | `default` | 默认模板分组 |
| `generator.conflict-strategy` | `SKIP` | 冲突策略（SKIP / OVERRIDE / MERGE） |
| `generator.table-prefix` | `t_,tab_` | 表前缀（生成类名时去除） |
| `generator.batch-timeout-minutes` | `5` | 批量生成单表超时 |
| `generator.thread-pool.core-size` | `0`（CPU 核数） | 异步生成线程池核心线程数 |
| `generator.thread-pool.max-size` | `16` | 异步生成线程池最大线程数 |
| `generator.thread-pool.queue-capacity` | `64` | 异步生成线程池排队容量 |

## 启动方式

```bash
# Web 模式（推荐，提供 REST API + Knife4j 文档）
cd ydzz-generator/ydsz-generator-web
mvn spring-boot:run

# 访问 Knife4j 文档：http://localhost:9090/gen/doc.html
```

## 设计要点

- **DDD 分层合规**：generated 产物严格遵循 `api` / `domain` / `infra` / `server` / `web` 六层分离，依赖方向单向收敛
- **MapStruct 装配器**：自动生成 Assembler 与 Converter，遵循 YDIZ-DDD-004 规范
- **模板分组**：支持多套模板分组（默认 `default`），可按项目切换代码风格
- **冲突保护**：默认 SKIP 策略避免覆盖已有文件，可通过配置改为 OVERRIDE/MERGE
- **历史追溯**：每次代码生成记录操作快照，支持回溯对比

## 版本

随父 POM 统一版本管理，当前 `26.09.01`。
