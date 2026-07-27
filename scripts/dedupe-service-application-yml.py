"""P2-5 公共配置下沉 Nacos：清理 5 个服务的 application.yml 重复配置。

现状：5 个服务（cronjob/project/workflow/literule/nextwiki）的本地 application.yml
与 Nacos 共享配置 deploy/common/nacos/ydsz-common.yaml 存在大量重复：
  - mybatis-plus.configuration.* （已在共享配置）
  - mybatis-plus.global-config.* （已在共享配置）
  - management.endpoints.web.exposure.include （已在共享配置）
  - management.endpoint.health.show-details （共享配置为 when-authorized 更安全，本地 always 覆盖了它）
  - springdoc.api-docs.path / springdoc.swagger-ui.path （已在共享配置）

治理动作：
1. 在 ydsz-common.yaml 增加 mybatis-plus.mapper-locations 默认值（所有服务共享）
2. 清理 5 个本地 application.yml 中的重复配置
3. 保留各服务的私有配置（ydsz.thread.pools / ydsz.literule / nextwiki / ydsz.event.outbox / ydsz.seata 等）
4. workflow 保留 mapper-locations override（额外加载 mapper/flow/**/*.xml）

约束：使用 UTF-8 编码读写，遵循 prefer-python-over-powershell 规则。
"""

from __future__ import annotations

import pathlib
import re

ROOT = pathlib.Path(r"d:/Code/ydsz/ydsz-pmis")

SHARED_CONFIG = ROOT / "deploy/common/nacos/ydsz-common.yaml"

LOCAL_CONFIGS = {
    "cronjob": ROOT / "ydsz-backend/ydsz-cronjob/ydsz-cronjob-web/src/main/resources/application.yml",
    "project": ROOT / "ydsz-backend/ydsz-project/ydsz-project-web/src/main/resources/application.yml",
    "workflow": ROOT / "ydsz-backend/ydsz-workflow/ydsz-workflow-web/src/main/resources/application.yml",
    "literule": ROOT / "ydsz-backend/ydsz-literule/ydsz-literule-web/src/main/resources/application.yml",
    "nextwiki": ROOT / "ydsz-backend/ydsz-nextwiki/ydsz-nextwiki-web/src/main/resources/application.yml",
}


def update_shared_config() -> bool:
    """在 ydsz-common.yaml 的 mybatis-plus 段增加 mapper-locations 默认值。"""
    content = SHARED_CONFIG.read_text(encoding="utf-8")

    # 检查是否已有 mapper-locations
    if "mapper-locations:" in content:
        return False

    # 在 mybatis-plus: 后插入 mapper-locations 作为第一项
    old = """# ---------- MyBatis-Plus 共享配置 ----------
mybatis-plus:
  configuration:"""

    new = """# ---------- MyBatis-Plus 共享配置 ----------
mybatis-plus:
  # Mapper XML 默认扫描路径（所有服务共用；workflow 在本地 application.yml 覆盖以追加 flow 路径）
  mapper-locations: classpath*:mapper/**/*.xml
  configuration:"""

    if old not in content:
        print("  [WARN] 未找到 ydsz-common.yaml 中的 mybatis-plus 段锚点，跳过")
        return False

    new_content = content.replace(old, new)
    SHARED_CONFIG.write_text(new_content, encoding="utf-8")
    return True


# 各服务的本地配置清理后的目标内容
# 仅保留服务私有配置：ydsz.* / nextwiki.* / server override / spring.servlet.multipart override / mapper-locations override
CLEAN_CONFIGS = {
    "cronjob": """# YDSZ 调度服务本地配置
# 公共配置（mybatis-plus / management / springdoc / datasource / redis）由 Nacos 共享配置
# ydsz-common.yaml 提供，此处仅保留服务私有配置

# P1-2: 统一线程池配置（common-thread）
# 注：taskExecutorPool（PriorityBlockingQueue）和 retryScheduler（ScheduledExecutorService）
# 因特殊队列/调度需求保留手动创建，由 CronjobProperties 配置化
ydsz:
  thread:
    enabled: true
    pools:
      cronjobMapReduce:
        core-size: 4
        max-size: 8
        queue-capacity: 200
        thread-name-prefix: ydsz-job-mapreduce-
        reject-policy: CALLER_RUNS
        await-termination-seconds: 30
      cronjobDispatch:
        core-size: 2
        max-size: 4
        queue-capacity: 100
        thread-name-prefix: ydsz-job-dispatch-
        reject-policy: CALLER_RUNS
        await-termination-seconds: 15
""",
    "project": """# YDSZ 项目管理服务本地配置
# 公共配置（mybatis-plus / management / springdoc / datasource / redis）由 Nacos 共享配置
# ydsz-common.yaml 提供，此处仅保留服务私有配置

# P0-2: 分布式事务配置（Seata AT 模式，默认关闭，TC 就绪后开启）
ydsz:
  seata:
    enabled: false
    default-type: SEATA_AT

# Seata 客户端配置（TC 就绪后设置 seata.enabled=true 并取消注释）
# seata:
#   enabled: true
#   application-id: ${spring.application.name}
#   tx-service-group: ydsz_tx_group
#   service:
#     vgroup-mapping:
#       ydsz_tx_group: default
#     grouplist:
#       default: ${SEATA_SERVER_ADDR:127.0.0.1:8091}
#   config:
#     type: file
#   registry:
#     type: file
""",
    "workflow": """# YDSZ 工作流服务本地配置
# 公共配置（mybatis-plus.configuration / global-config / management / springdoc / datasource / redis）
# 由 Nacos 共享配置 ydsz-common.yaml 提供，此处仅保留服务私有配置与 override

# MyBatis-Plus override：在默认路径基础上追加 flow 子目录
mybatis-plus:
  mapper-locations: classpath*:mapper/**/*.xml,classpath*:mapper/flow/**/*.xml

# P0-1: 统一线程池配置（ydsz-common-thread）
ydsz:
  thread:
    enabled: true
    pools:
      flowQueue:
        core-size: 2
        max-size: 8
        queue-capacity: 256
        thread-name-prefix: flow-queue-
        reject-policy: CALLER_RUNS
        await-termination-seconds: 10
  # P0-2: 事务性 Outbox 事件配置
  event:
    outbox:
      enabled: true
      table-name: ydsz_outbox
      poll-interval-seconds: 5
      batch-size: 100
      max-retries: 5
      fail-on-noop: false
  # P0-2: 分布式事务配置（Seata AT 模式，默认关闭，TC 就绪后开启）
  seata:
    enabled: false
    default-type: SEATA_AT

# Seata 客户端配置（TC 就绪后设置 seata.enabled=true 并取消注释）
# seata:
#   enabled: true
#   application-id: ${spring.application.name}
#   tx-service-group: ydsz_tx_group
#   service:
#     vgroup-mapping:
#       ydsz_tx_group: default
#     grouplist:
#       default: ${SEATA_SERVER_ADDR:127.0.0.1:8091}
#   config:
#     type: file
#   registry:
#     type: file
""",
}


def clean_local_config(service: str, new_content: str) -> bool:
    """覆盖写入清理后的本地配置。"""
    path = LOCAL_CONFIGS[service]
    original = path.read_text(encoding="utf-8")
    if original == new_content:
        return False
    path.write_text(new_content, encoding="utf-8")
    return True


def clean_literule_config() -> bool:
    """清理 literule application.yml：仅删除重复的 mybatis-plus.configuration/global-config、
    management、springdoc 段，保留 mapper-locations（默认值）和 ydsz.literule 大块配置。
    """
    path = LOCAL_CONFIGS["literule"]
    content = path.read_text(encoding="utf-8")

    # 1. 替换文件头注释
    old_header = """# YDSZ 规则引擎服务本地配置
# Nacos 配置中心覆盖以下配置（ydsz-literule.yaml）

# MyBatis-Plus
mybatis-plus:
  mapper-locations: classpath*:mapper/**/*.xml
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl
  global-config:
    db-config:
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0

# 规则引擎核心配置（生产环境由 Nacos ydsz-literule.yaml 覆盖）
ydsz:"""

    new_header = """# YDSZ 规则引擎服务本地配置
# 公共配置（mybatis-plus / management / springdoc / datasource / redis）由 Nacos 共享配置
# ydsz-common.yaml 提供，此处仅保留服务私有配置

# 规则引擎核心配置（生产环境由 Nacos ydsz-literule.yaml 覆盖）
ydsz:"""

    if old_header not in content:
        print("  [WARN] literule application.yml 头部锚点未找到，跳过头部清理")
    else:
        content = content.replace(old_header, new_header)

    # 2. 删除文件尾部的重复 management / springdoc 段
    old_tail = """# Actuator
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always

# OpenAPI
springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html
"""

    if old_tail not in content:
        print("  [WARN] literule application.yml 尾部 management/springdoc 锚点未找到，跳过尾部清理")
    else:
        content = content.replace(old_tail, "")

    # 清理末尾空行
    content = content.rstrip() + "\n"

    path.write_text(content, encoding="utf-8")
    return True


def clean_nextwiki_config() -> bool:
    """清理 nextwiki application.yml：删除重复的 mybatis-plus.configuration/global-config、
    management、springdoc 段，保留 server.port override、multipart、nextwiki.*、ydsz.thread.pools。
    """
    path = LOCAL_CONFIGS["nextwiki"]
    content = path.read_text(encoding="utf-8")

    # 1. 替换文件头：nextwiki 保留 multipart 和 server override
    old_header = """# NextWiki 网盘知识库服务本地配置
# Nacos 配置中心覆盖以下配置（ydsz-nextwiki.yml）

server:
  port: 8800

spring:
  servlet:
    multipart:
      max-file-size: 500MB
      max-request-size: 500MB

# MyBatis-Plus
mybatis-plus:
  mapper-locations: classpath*:mapper/**/*.xml
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl
  global-config:
    db-config:
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0

# NextWiki 配置（生产环境由 Nacos ydsz-nextwiki.yml 覆盖）"""

    new_header = """# NextWiki 网盘知识库服务本地配置
# 公共配置（mybatis-plus / management / springdoc / datasource / redis）由 Nacos 共享配置
# ydsz-common.yaml 提供，此处仅保留服务私有配置与 override

# NOTE: bootstrap.yml 中 server.port=9007，此处的 8800 会覆盖 bootstrap 的 9007
# TODO: 与运维确认 nextwiki 实际运行端口，统一 bootstrap 与 application 的端口配置
server:
  port: 8800

spring:
  servlet:
    multipart:
      max-file-size: 500MB
      max-request-size: 500MB

# NextWiki 配置（生产环境由 Nacos ydsz-nextwiki.yml 覆盖）"""

    if old_header not in content:
        print("  [WARN] nextwiki application.yml 头部锚点未找到，跳过头部清理")
    else:
        content = content.replace(old_header, new_header)

    # 2. 删除文件尾部的重复 management / springdoc 段
    old_tail = """# Actuator
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always

# OpenAPI
springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html

# 统一线程池配置（ydsz-common-thread）"""

    if old_tail not in content:
        print("  [WARN] nextwiki application.yml 尾部 management/springdoc 锚点未找到，跳过尾部清理")
    else:
        content = content.replace(
            old_tail,
            "# 统一线程池配置（ydsz-common-thread）",
        )

    content = content.rstrip() + "\n"
    path.write_text(content, encoding="utf-8")
    return True


def main() -> None:
    print("=== P2-5 公共配置下沉 Nacos：清理 5 个服务 application.yml 重复配置 ===")

    print("\n[1/6] 更新 ydsz-common.yaml 增加 mybatis-plus.mapper-locations 默认值")
    if update_shared_config():
        print("  完成")
    else:
        print("  跳过（已存在或锚点未找到）")

    print("\n[2/6] 清理 cronjob application.yml")
    if clean_local_config("cronjob", CLEAN_CONFIGS["cronjob"]):
        print("  完成")
    else:
        print("  跳过（无变化）")

    print("\n[3/6] 清理 project application.yml")
    if clean_local_config("project", CLEAN_CONFIGS["project"]):
        print("  完成")
    else:
        print("  跳过（无变化）")

    print("\n[4/6] 清理 workflow application.yml")
    if clean_local_config("workflow", CLEAN_CONFIGS["workflow"]):
        print("  完成")
    else:
        print("  跳过（无变化）")

    print("\n[5/6] 清理 literule application.yml")
    if clean_literule_config():
        print("  完成")
    else:
        print("  跳过（无变化）")

    print("\n[6/6] 清理 nextwiki application.yml")
    if clean_nextwiki_config():
        print("  完成")
    else:
        print("  跳过（无变化）")

    print("\n=== 清理完成 ===")
    print("后续步骤：")
    print("  1. 抽查 ydsz-common.yaml 与 5 个 application.yml 的内容")
    print("  2. 验证 ydsz-common.yaml 中 mybatis-plus.mapper-locations 已生效")
    print("  3. TODO: nextwiki 的 server.port=8800 与 bootstrap 的 9007 不一致，需运维确认")


if __name__ == "__main__":
    main()
