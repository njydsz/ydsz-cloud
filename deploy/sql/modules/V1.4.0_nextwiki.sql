-- NextWiki 模块 V1.4.0 DDL（P2-R6）
-- 文件评论表
CREATE TABLE IF NOT EXISTS nw_file_comment (
    id              VARCHAR(32)   NOT NULL COMMENT '主键ID',
    file_node_id    VARCHAR(32)   NOT NULL COMMENT '关联的文件节点ID',
    content         TEXT          NOT NULL COMMENT '评论内容',
    parent_comment_id VARCHAR(32)  DEFAULT NULL COMMENT '父评论ID（回复）',
    resolved        TINYINT(1)    DEFAULT 0 COMMENT '是否已解决',
    position        VARCHAR(500)  DEFAULT NULL COMMENT '评论位置信息（JSON）',
    edited          TINYINT(1)    DEFAULT 0 COMMENT '是否被编辑过',
    revision        INT           DEFAULT 0 COMMENT '乐观锁版本号',
    deleted         TINYINT(1)    DEFAULT 0 COMMENT '逻辑删除标记',
    created_by      VARCHAR(32)   DEFAULT NULL COMMENT '创建人',
    created_at      DATETIME      DEFAULT NULL COMMENT '创建时间',
    updated_by      VARCHAR(32)   DEFAULT NULL COMMENT '更新人',
    updated_at      DATETIME      DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_file_node_id (file_node_id),
    INDEX idx_parent_comment_id (parent_comment_id),
    INDEX idx_created_by (created_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件评论表';

-- 审计日志表
CREATE TABLE IF NOT EXISTS nw_audit_log (
    id              VARCHAR(32)   NOT NULL COMMENT '主键ID',
    operation       VARCHAR(50)   NOT NULL COMMENT '操作类型',
    file_node_id    VARCHAR(32)   DEFAULT NULL COMMENT '文件节点ID',
    file_name       VARCHAR(255)  DEFAULT NULL COMMENT '文件名',
    node_type       VARCHAR(20)   DEFAULT NULL COMMENT '节点类型',
    storage_key     VARCHAR(500)  DEFAULT NULL COMMENT '存储对象键',
    bucket_name     VARCHAR(100)  DEFAULT NULL COMMENT '存储桶名称',
    operator_id     VARCHAR(32)   NOT NULL COMMENT '操作人ID',
    operated_at     DATETIME      NOT NULL COMMENT '操作时间',
    extra           VARCHAR(2000) DEFAULT NULL COMMENT '额外参数',
    result          VARCHAR(20)   DEFAULT 'success' COMMENT '操作结果',
    error_message   VARCHAR(1000) DEFAULT NULL COMMENT '失败原因',
    revision        INT           DEFAULT 0 COMMENT '乐观锁版本号',
    deleted         TINYINT(1)    DEFAULT 0 COMMENT '逻辑删除标记',
    created_by      VARCHAR(32)   DEFAULT NULL COMMENT '创建人',
    created_at      DATETIME      DEFAULT NULL COMMENT '创建时间',
    updated_by      VARCHAR(32)   DEFAULT NULL COMMENT '更新人',
    updated_at      DATETIME      DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_file_node_id (file_node_id),
    INDEX idx_operator_id (operator_id),
    INDEX idx_operated_at (operated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审计日志表';
