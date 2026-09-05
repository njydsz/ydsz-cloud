-- =====================================================
-- ydzs-generator 代码生成器数据库 V1.0 初始化脚本
-- 创建时间：2026-09-05
-- 编码：UTF-8 (无 BOM)
-- =====================================================

-- -----------------------------------------------------
-- 数据源配置表
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS gen_datasource (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  name VARCHAR(64) NOT NULL COMMENT '数据源名称（唯一标识）',
  jdbc_url VARCHAR(512) NOT NULL COMMENT 'JDBC URL',
  username VARCHAR(128) NOT NULL COMMENT '数据库用户名',
  password VARCHAR(512) NOT NULL COMMENT '数据库密码（AES 加密存储）',
  dialect VARCHAR(32) NOT NULL DEFAULT 'MYSQL' COMMENT '数据库方言（MYSQL/POSTGRESQL/ORACLE）',
  is_default TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否为默认数据源（0=否 1=是）',
  description VARCHAR(255) DEFAULT NULL COMMENT '数据源描述',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ds_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代码生成器数据源配置';

-- -----------------------------------------------------
-- 模板分组表
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS gen_template_group (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  name VARCHAR(64) NOT NULL COMMENT '分组名（唯一标识，如 default、mybatis-plus）',
  description VARCHAR(255) DEFAULT NULL COMMENT '分组描述',
  is_system TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否为系统分组（0=否 1=是，系统分组不可删除）',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序序号（升序）',
  is_active TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否激活为当前使用分组（0=否 1=是）',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tg_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代码生成器模板分组';

-- -----------------------------------------------------
-- 模板表
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS gen_template (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  group_id BIGINT NOT NULL COMMENT '关联模板分组 ID',
  file_name VARCHAR(128) NOT NULL COMMENT '文件名（如 entity.vm、vue/api.vm）',
  description VARCHAR(255) DEFAULT NULL COMMENT '模板用途描述',
  content MEDIUMTEXT NOT NULL COMMENT '模板内容（Velocity 语法）',
  is_folder TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否为虚拟文件夹标记（0=否 1=是）',
  parent_path VARCHAR(512) NOT NULL DEFAULT '' COMMENT '父路径（如 vue/ 表示前端子目录）',
  version INT NOT NULL DEFAULT 1 COMMENT '当前版本号',
  hash CHAR(32) DEFAULT NULL COMMENT '内容 MD5 哈希（版本对比用）',
  is_active TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用（0=否 1=是）',
  file_type VARCHAR(16) NOT NULL DEFAULT 'BACKEND' COMMENT '模板类型（BACKEND/FRONTEND）',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tmpl_group_file (group_id, file_name),
  KEY idx_tmpl_group (group_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代码生成器模板';

-- -----------------------------------------------------
-- 代码生成任务历史表
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS gen_history (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  module_name VARCHAR(64) NOT NULL COMMENT '模块名称',
  datasource_id BIGINT NOT NULL COMMENT '使用的数据源 ID',
  template_group_id BIGINT NOT NULL COMMENT '使用的模板分组 ID',
  table_count INT NOT NULL DEFAULT 0 COMMENT '涉及表数量',
  file_count INT NOT NULL DEFAULT 0 COMMENT '生成文件总数',
  status VARCHAR(16) NOT NULL DEFAULT 'RUNNING' COMMENT '执行状态（RUNNING/SUCCESS/PARTIAL/FAILED）',
  triggered_by VARCHAR(64) DEFAULT NULL COMMENT '触发人（用户名）',
  started_at DATETIME NOT NULL COMMENT '开始时间',
  finished_at DATETIME DEFAULT NULL COMMENT '完成时间',
  error_message TEXT DEFAULT NULL COMMENT '错误信息（失败时记录）',
  gen_params JSON DEFAULT NULL COMMENT '生成参数 JSON 快照',
  PRIMARY KEY (id),
  KEY idx_hist_status (status),
  KEY idx_hist_started (started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代码生成任务历史';

-- -----------------------------------------------------
-- 生成历史文件明细表
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS gen_history_file (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  history_id BIGINT NOT NULL COMMENT '所属任务 ID',
  file_path VARCHAR(512) NOT NULL COMMENT '生成文件路径',
  original_backup_path VARCHAR(512) DEFAULT NULL COMMENT '原文件备份路径（用于回滚）',
  file_hash CHAR(32) DEFAULT NULL COMMENT '文件内容 MD5 哈希',
  action VARCHAR(16) NOT NULL DEFAULT 'CREATED' COMMENT '文件操作类型（CREATED/UPDATED/UNCHANGED）',
  PRIMARY KEY (id),
  KEY idx_hf_history (history_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生成历史文件明细';

-- -----------------------------------------------------
-- 表元数据缓存表
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS gen_table_meta (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  datasource_id BIGINT NOT NULL COMMENT '关联数据源 ID',
  table_name VARCHAR(128) NOT NULL COMMENT '物理表名',
  comment VARCHAR(255) DEFAULT NULL COMMENT '表注释',
  alias_name VARCHAR(64) DEFAULT NULL COMMENT '用户自定义别名（用于类名生成）',
  module_name VARCHAR(64) DEFAULT NULL COMMENT '模块名称（用于包路径）',
  cached_at DATETIME NOT NULL COMMENT '缓存时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tm_ds_table (datasource_id, table_name),
  KEY idx_tm_datasource (datasource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表元数据缓存';

-- -----------------------------------------------------
-- 列元数据缓存表
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS gen_column_meta (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  table_meta_id BIGINT NOT NULL COMMENT '所属表元数据 ID',
  column_name VARCHAR(128) NOT NULL COMMENT '物理列名',
  data_type VARCHAR(64) NOT NULL COMMENT '物理数据类型',
  column_size INT DEFAULT NULL COMMENT '字段长度',
  nullable TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否可为空（0=否 1=是）',
  is_pk TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否为主键（0=否 1=是）',
  comment VARCHAR(255) DEFAULT NULL COMMENT '字段注释',
  override_java_type VARCHAR(64) DEFAULT NULL COMMENT '人工覆盖 Java 类型（为空则使用自动映射）',
  override_field_name VARCHAR(64) DEFAULT NULL COMMENT '人工覆盖字段名（为空则使用自动命名）',
  skip_dto TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否在 DTO 中跳过（0=否 1=是）',
  skip_vo TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否在 VO 中跳过（0=否 1=是）',
  skip_query TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否在 Query 中跳过（0=否 1=是）',
  extra_config JSON DEFAULT NULL COMMENT '扩展配置 JSON（枚举值、校验规则等）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_col_table_column (table_meta_id, column_name),
  KEY idx_col_table (table_meta_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='列元数据缓存（含人工覆盖配置）';
