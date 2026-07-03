# PMIS Seata 分布式事务配置（AT 模式）
# --------------------------------------------------------------------------
# 用途：跨服务事务一致性保障（如：商机转立项 + 工时归集 + 利润快照 跨 3 个服务）
# 部署：deploy/seata/docker-compose.yml（与 Nacos 同服务器）
# 客户端：ydsz-pmis-project / ydsz-pmis-iam 等业务模块
# --------------------------------------------------------------------------

# 1. 服务端（deploy/seata 目录运行）
#    docker-compose up -d
#
# 2. 各业务模块 application.yml 启用：
#    seata:
#      enabled: true
#      application-id: ${spring.application.name}
#      tx-service-group: pmis-tx-group
#      registry:
#        type: nacos
#        nacos:
#          server-addr: 127.0.0.1:8848
#          namespace: pmis
#          group: SEATA_GROUP
#      config:
#        type: nacos
#        nacos:
#          server-addr: 127.0.0.1:8848
#          group: SEATA_GROUP
#          data-id: seata-client.properties
#
# 3. 业务代码使用 @GlobalTransactional
#    @GlobalTransactional(name = "convert-opportunity-to-initiation", rollbackFor = Exception.class)
#    public Long convertToInitiation(Long opportunityId) { ... }

seata:
  enabled: false  # 默认关闭，需要时在具体模块 application.yml 覆盖
  application-id: ${spring.application.name}
  tx-service-group: pmis-tx-group
  config:
    type: file
    file:
      name: file.conf
  registry:
    type: file
    file:
      name: file.conf

# AT 模式 undo_log 表（每个业务库都需要创建）
# CREATE TABLE undo_log (
#   id            BIGSERIAL PRIMARY KEY,
#   branch_id     BIGINT       NOT NULL,
#   xid           VARCHAR(100) NOT NULL,
#   context       VARCHAR(128) NOT NULL,
#   rollback_info BYTEA        NOT NULL,
#   log_status    INT          NOT NULL,
#   log_created   TIMESTAMP    NOT NULL,
#   log_modified  TIMESTAMP    NOT NULL,
#   CONSTRAINT ux_undo_log UNIQUE (xid, branch_id)
# );
