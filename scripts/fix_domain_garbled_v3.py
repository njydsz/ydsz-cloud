"""
Fix garbled Javadoc in ydsz-pmis-common-domain module.
The garbled text is caused by truncated Chinese characters where '。' (U+3002)
appears in places where it shouldn't, replacing actual characters that were lost
during encoding corruption.

Strategy: Read each file, detect lines with garbled patterns, and fix them
using context-based replacement rules.
"""
import os
import re

ROOT = r'd:/Code/ydsz/ydsz-pmis/ydsz-pmis-backend/ydsz-pmis-common/ydsz-pmis-common-domain/src/main/java'

# Mapping of common garbled patterns to their correct forms
# These are based on actual occurrences found in the codebase
REPLACEMENTS = [
    # BaseDTO.java
    ('所。DTO', '所有 DTO'),
    ('而非直接使用。/p>', '而非直接使用。</p>'),
    ('操作人姓。', '操作人姓名'),
    ('。operatorId', '与 operatorId'),
    ('唯一标识一。HTTP', '唯一标识一次 HTTP'),
    ('。SaaS', '在 SaaS'),
    ('WEB 。', 'Web 端'),
    ('开始API', '开放 API'),
    # BaseAuditEntity.java
    ('审计字段基础实体。', '审计字段基础实体'),
    ('这些字段MyBatis-Plus', '这些字段由 MyBatis-Plus'),
    ('。重构规划', '重构规划'),
    ('迁移路径：BaseAuditEntity 。', '迁移路径：BaseAuditEntity →'),
    ('支。Long', '支持 Long'),
    ('通常。SecurityContext', '通常从 SecurityContext'),
    ('框架。INSERT', '框架在 INSERT'),
    ('框架。INSERT/UPDATE', '框架在 INSERT/UPDATE'),
    ('是否。null', '是否为 null'),
    # BaseEntity.java
    ('逻辑删除标识（0', '逻辑删除标识（0 表示未删除）'),
    ('自动递增。1', '自动递增（+1）'),
    ('。1）作为根节点标识', '（如 0 或 -1）作为根节点标识'),
    ('。TreeBuilder', '由 TreeBuilder'),
    ('推荐。', '推荐）'),
    ('参。ydsz-pmis-common-jdbc', '参见 ydsz-pmis-common-jdbc'),
    ('支。Long', '支持 Long'),
    ('。重构规划', '重构规划'),
    ('扁平化。2-3', '扁平化为 2-3'),
    # AggregateRoot.java
    ('订单项不能超。00', '订单项不能超过100'),
    # Auditable.java
    ('审计实体。', '审计实体接口'),
    # Versionable.java
    ('执。UPDATE', '执行 UPDATE'),
    ('更新人配。', '更新人配置'),
    # SoftDeletable.java
    ('逻辑删除标识接口（软删除。', '逻辑删除标识接口（软删除）'),
    ('执。delete', '执行 delete'),
    ('追为', '追加'),
    # RootEntity.java
    ('组合了三个职责单一的子接口', '组合了三个职责单一的子接口'),
    ('。ID + isNew()', '— ID + isNew()'),
    ('。乐观锁版本号', '— 乐观锁版本号'),
    ('。逻辑删除', '— 逻辑删除'),
    ('已统一收敛。', '已统一收敛至'),
    ('。', '。'),  # fallback
    # CursorPageResult.java
    ('。null 表示无更多数据', '为 null 表示无更多数据'),
    # Repository.java - already fixed
    ('细节点', '细节'),
    ('基础设施。', '基础设施层'),
    ('可序列表', '可序列化'),
    ('不存在时返回。', '不存在时返回空 Optional'),
    ('根据ID。', '根据ID集合'),
    # TreeBuilder.java
    ('核心构建。', '核心构建器'),
    ('统一的对。API', '统一的对外 API'),
    ('。ID 查找', '按 ID 查找'),
    ('扁平。', '扁平化'),
    ('筛。', '筛选'),
    ('null 值排在最。', 'null 值排在最后'),
    ('缓存的全量扁平节点列表', '缓存的全量扁平节点列表'),
    ('ID -> Node。', 'ID -> Node）'),
    ('构方向', '构造方法'),
    ('构方向', '构造方法'),
    # TreeNode.java
    ('根节点层级深。', '根节点层级深度'),
    ('也可使用特定值（。。1）', '也可使用特定值（如 0 或 -1）'),
    ('。TreeBuilder', '由 TreeBuilder'),
    ('判断 parentId 是否。null', '判断 parentId 是否为 null'),
    ('版本号', '版本'),
    ('构建当前节点的路。', '构建当前节点的路径'),
    ('路径字符。', '路径字符串'),
    ('避免递归调用栈溢。', '避免递归调用栈溢出'),
    ('用于查找。', '用于查找父节点'),
    # BaseIdEntity.java
    ('主键基础实体。', '主键基础实体'),
    ('使用 MyBatis-Plus 。ASSIGN_ID', '使用 MyBatis-Plus 的 ASSIGN_ID'),
    ('或使。String', '或使用 String'),
    ('支。Long', '支持 Long'),
    # DomainEventPublisher.java
    ('退化为同步发布器', '退化为同步发布'),
    ('则直接同步发布器', '则直接同步发布'),
    # LogBaseDO.java
    ('（兼容 com.njydsz.pmis.common.entity.LogBaseDO）。', '（兼容 com.njydsz.pmis.common.entity.LogBaseDO）'),
    # BaseDO.java
    ('（兼容 com.njydsz.pmis.common.domain.entity.BaseDO）。', '（兼容 com.njydsz.pmis.common.domain.entity.BaseDO）'),
    # VersionableDO.java
    ('（已废弃）。', '（已废弃）'),
    # Annotation files
    ('标注在实。', '标注在实体'),
    ('标注在实。', '标注在实体'),
    ('记录数据创建时间', '记录数据创建时间'),
    ('标注在实。', '标注在实体'),
]

def fix_file(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    original = content
    for old, new in REPLACEMENTS:
        content = content.replace(old, new)
    
    if content != original:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(content)
        return True
    return False

count = 0
for dp, dn, fn in os.walk(ROOT):
    for f in fn:
        if f.endswith('.java'):
            fp = os.path.join(dp, f)
            if fix_file(fp):
                count += 1
                print(f'Fixed: {os.path.relpath(fp, ROOT)}')

print(f'\nTotal files fixed: {count}')
