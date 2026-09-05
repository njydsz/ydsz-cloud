# ydzs-generator DDD 完整平台重构规划

## 1. 目标定位

将 ydzs-generator 从「零依赖辅助工具」升级为「有状态的、可在线管理的代码生成平台」，支持：
- 多数据源管理（在线配置/测试连接）
- 模板在线 CRUD（Monaco 编辑器 + 版本管理）
- 模板分组（DDD/MybatisPlus/SpringDataMongoDB...）
- 列级配置覆盖（type/select/boolean 三种模式）
- 代码生成历史与回滚
- 异步任务执行（大表批量生成不阻塞）
- 实体类反向生成

## 2. 模块结构

```
ydsz-generator/                          (聚合 BOM — 无源码)
├── generator-domain/                    (领域层 — 纯接口与模型)
│   ├── entity/                          (实体 7 + 全部不可变)
│   ├── vo/                              (查询/展示值对象)
│   ├── enums/                           (枚举 4 个)
│   └── repository/                      (Repository 接口 7 个)
├── generator-infra/                     (基础设施层 — 持久化 + IO)
│   ├── repository/impl/                 (Repository 实现 7 个)
│   ├── mapper/                          (MyBatis Mapper 接口 7 个)
│   ├── po/                              (持久化对象 7 + 全部)
│   └── converter/                       (MapStruct Converter 3)
├── generator-server/                    (应用服务层 — 编排)
│   ├── service/                         (领域服务 8 个)
│   └── task/                            (异步任务 1)
└── generator-web/                       (接口层)
    ├── controller/                      (REST 6 个)
    ├── dto/                             (请求/响应 DTO 30+)
    ├── assembler/                       (MapStruct Assembler 8)
    └── config/                          (Web 各层)
```

## 3. 核心表设计

```sql
-- 数据源管理
CREATE TABLE gen_datasource (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(64) NOT NULL COMMENT '名称（唯一标识）',
  jdbc_url VARCHAR(512) NOT NULL COMMENT 'JDBC URL',
  username VARCHAR(128) NOT NULL,
  password VARCHAR(512) NOT NULL COMMENT '加密存储',
  dialect VARCHAR(32) NOT NULL COMMENT 'MYSQL/POSTGRESQL/ORACLE',
  is_default TINYINT(1) DEFAULT 0,
  description VARCHAR(255),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_ds_name (name)
);

-- 模板分组
CREATE TABLE gen_template_group (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(64) NOT NULL COMMENT '分组名（UNIQUE，如 default）',
  description VARCHAR(255),
  is_system TINYINT(1) DEFAULT 0 COMMENT '系统分组不可删除',
  sort_order INT DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_tg_name (name)
);

-- 模板
CREATE TABLE gen_template (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  group_id BIGINT NOT NULL COMMENT '关联 gen_template_group',
  file_name VARCHAR(128) NOT NULL COMMENT '文件名（如 entity.vm）',
  description VARCHAR(255),
  content MEDIUMTEXT NOT NULL COMMENT '模板内容',
  is_folder TINYINT(1) DEFAULT 0 COMMENT '虚拟文件夹标记',
  parent_path VARCHAR(512) DEFAULT '' COMMENT '父路径（如 vue/）',
  version INT DEFAULT 1,
  hash CHAR(32) COMMENT 'md5 内容哈希（版本对比）',
  is_active TINYINT(1) DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_tmpl_group_file (group_id, file_name)
);

-- 生成任务 / 历史
CREATE TABLE gen_history (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  module_name VARCHAR(64) NOT NULL,
  datasource_id BIGINT NOT NULL,
  template_group_id BIGINT NOT NULL,
  table_count INT NOT NULL,
  file_count INT NOT NULL,
  status VARCHAR(16) NOT NULL COMMENT 'SUCCESS/PARTIAL/FAILED/RUNNING',
  triggered_by VARCHAR(64),
  started_at DATETIME NOT NULL,
  finished_at DATETIME,
  error_message TEXT,
  gen_params JSON COMMENT '生成参数快照'
);

CREATE TABLE gen_history_file (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  history_id BIGINT NOT NULL,
  file_path VARCHAR(512) NOT NULL,
  original_backup_path VARCHAR(512) COMMENT '回滚使用',
  file_hash CHAR(32),
  action VARCHAR(16) COMMENT 'CREATED/UPDATED/UNCHANGED'
);

-- 表元数据缓存
CREATE TABLE gen_table_meta (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  datasource_id BIGINT NOT NULL,
  table_name VARCHAR(128) NOT NULL,
  comment VARCHAR(255),
  alias_name VARCHAR(64),
  module_name VARCHAR(64),
  cached_at DATETIME NOT NULL,
  UNIQUE KEY uk_tm_ds_table (datasource_id, table_name)
);

-- 列元数据缓存（含覆盖配置）
CREATE TABLE gen_column_meta (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  table_meta_id BIGINT NOT NULL,
  column_name VARCHAR(128) NOT NULL,
  data_type VARCHAR(64) NOT NULL,
  column_size INT,
  nullable TINYINT(1),
  is_pk TINYINT(1) DEFAULT 0,
  comment VARCHAR(255),
  -- 人工覆盖配置
  override_java_type VARCHAR(64) DEFAULT NULL,
  override_field_name VARCHAR(64) DEFAULT NULL,
  skip_dto TINYINT(1) DEFAULT 0,
  skip_vo TINYINT(1) DEFAULT 0,
  skip_query TINYINT(1) DEFAULT 0,
  extra_config JSON COMMENT '扩展配置（枚举值等）',
  UNIQUE KEY uk_col_table_column (table_meta_id, column_name)
);
```

## 4. 模块依赖方向

```
        ┌─────────────────────────────────────────┐
        │              generator-api              │
        └────────────────┬────────────────────────┘
                         │
        ┌────────────────▼────────────────────────┐
        │             generator-domain             │
        │（entity/vo/enums/repository接口）        │
        └────────────────┬────────────────────────┘
                         │
        ┌────────────────▼────────────────────────┐
        │             generator-infra              │
        │（repository实现/Mapping/PO/Converter）   │
        └──────────┬──────────────┬───────────────┘
                   │              │
        ┌──────────▼──┐    ┌──────▼──────────────┐
        │generator-   │    │  ydsz-common-*      │
        │server       │    │  ydsz-starter-db    │
        └──────────┬──┘    └─────────────────────┘
                   │
        ┌──────────▼──────────────────────────────┐
        │              generator-web               │
        │       CORS / Filter / Interceptor        │
        └─────────────────────────────────────────┘
```

5. 实施步骤
5.1 模块骨架 + POM
建 4 个子目录 + pom.xml，父 pom 添加 <modules>

5.2 Domain 层
实体 6 个 + VO 6 个 + 枚举 4 个

5.3 Infra 层
6 Repository 实现 + 6 Mapper + 6 PO + MapStruct 转换

5.4 Server 层
DatasourceService
TemplateService
TemplateGroupService
TableMetaService
ColumnMetaService
CodeGenService（编排）
TemplateImportExportService
EntityReverseService
CodeGenTask（异步）

5.5 Web 层
5 个 REST Controller + DTO 30+ + MapStruct Assembler

5.6 数据库
V1.0__init_schema.sql + data.sql

5.7 前端
table-selector / column-config / preview / diff-viewer
