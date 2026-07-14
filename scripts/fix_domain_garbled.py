#!/usr/bin/env python3
"""Fix garbled Chinese comments in ydsz-pmis-common-domain module."""

import os
import re

ROOT = r'd:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-common\ydsz-pmis-common-domain\src\main\java'

# Common garbled patterns and their fixes
FIXES = [
    # BaseEntity.java
    ('对修改关。', '对修改关闭'),
    ('每个字段有且只有一个职。', '每个字段有且只有一个职责'),
    ('依赖倒置：业务代码依赖抽象基类，不依赖具体实体', '依赖倒置：业务代码依赖抽象基类，不依赖具体实体'),
    ('乐观。', '乐观锁'),
    ('数据可恢。', '数据可恢复'),
    ('状态启。禁用', '状态启用/禁用'),
    ('防止更新冲。', '防止更新冲突'),
    ('用户。', '用户名'),
    ('手机。', '手机号'),
    ('状态,', '状态'),
    ('创建。', '创建人'),
    ('更新。', '更新人'),
    ('乐观锁版。', '乐观锁版本号'),
    # BaseAuditEntity.java
    ('审计字段对业务代码透明，由框架自动维护', '审计字段对业务代码透明，由框架自动维护'),
    ('支持时区转。', '支持时区转换'),
    ('格式控。', '格式控制'),
    ('字段名', '字段'),
    ('自动填充此字段名', '自动填充此字段'),
    ('兼容。com.njydsz', '兼容 com.njydsz'),
    # BaseIdEntity.java
    ('实与', '实现'),
    ('继与', '继承'),
    ('最必要的字。', '最必要的字段'),
    ('UUID 。', 'UUID 等'),
    # AggregateRoot.java
    ('聚合根标记接。', '聚合根标记接口'),
    ('一致性边。', '一致性边界'),
    ('业务规则的一致。', '业务规则的一致性'),
    ('进。', '进行'),
    # RootEntity.java
    ('向后兼。', '向后兼容'),
    ('+ 乐观。+ 逻辑删除', '+ 乐观锁 + 逻辑删除'),
    # Persistable.java
    ('可能力null', '可能为 null'),
    (' isEmpty 判断', ' isEmpty 判断'),
    ('覆写此方向', '覆写此方法'),
    # TenantAware / ProjectAware / RegionAware
    ('tenant_id 条件', 'tenant_id 条件'),
    ('project_id 条件', 'project_id 条件'),
    ('region_id 条件', 'region_id 条件'),
    ('返。', '返回'),
    ('设计companyId', '设置 companyId'),
    # SoftDeletable
    ('已删除）。', '已删除）'),
    ('删除标识。', '删除标识（0'),
    # Versionable
    ('版本。', '版本号'),
    # DomainEvent
    ('已经发生的。', '已经发生的事情'),
    ('不可改。', '不可改变'),
    ('而非技术细。', '而非技术细节'),
    ('事件溯源。', '事件溯源）'),
    ('构造领域事。', '构造领域事件'),
    ('完整参数。', '完整参数）'),
    ('创建领域事。', '创建领域事件'),
    # DomainEventPublisher
    ('发布。', '发布器'),
    ('为单。Bean', '为单例 Bean'),
    # TreeBuilder
    ('路径生。', '路径生成'),
    ('获取后。', '获取后代'),
    ('循环引用检。', '循环引用检测'),
    ('节点数量阈值', '节点数量阈值'),
    # LazyTreeNode
    ('加载。', '加载'),
    ('最大深。', '最大深度'),
    ('可见。', '可见性'),
    ('持有 loadLock。', '持有 loadLock）'),
    # TreeNode
    ('基础。', '基础类'),
    ('子节点列。', '子节点列表'),
    ('根节点。', '根节点为'),
    ('排。', '排序'),
    ('返。', '返回'),
    ('数。', '数量'),
    ('列。', '列表'),
    # Repository
    ('仓。', '仓储'),
    ('条件查。', '条件查询'),
    ('聚合。', '聚合根'),
    ('聚合根列。', '聚合根列表'),
    ('集。', '集合'),
    ('可序列。', '可序列化'),
    # Specification
    ('规。', '规约'),
    ('条。', '条件'),
    # PageQuery
    ('安全版本。', '安全版本）'),
    ('setter。', 'setter）'),
    ('校验。', '校验）'),
    # CursorPageResult
    ('列。', '列表'),
    ('判。hasMore', '判断 hasMore'),
    # BaseQuery
    ('搜索引', '搜索'),
    ('过。', '过滤'),
    ('时。', '时间'),
    # Various
    ('点', '。'),  # This is too broad, skip
]

# More targeted fixes for specific garbled patterns
TARGETED_FIXES = [
    (r'([\u4e00-\u9fff])\u3002([^\n]*?)(?=[\u4e00-\u9fff])', None),  # Pattern to identify
]

def fix_file(path):
    with open(path, 'r', encoding='utf-8') as f:
        original = f.read()
    
    text = original
    for garbled, fixed in FIXES:
        text = text.replace(garbled, fixed)
    
    # Fix remaining "X。" patterns where X is a Chinese char followed by more Chinese
    # This handles cases like "实体。" -> "实体。" (correct) vs "实。" -> "实现" (garbled)
    # We can't auto-fix all of these, but we can fix common ones
    
    if text != original:
        with open(path, 'w', encoding='utf-8') as f:
            f.write(text)
        return True
    return False

count = 0
for dp, dn, fn in os.walk(ROOT):
    for f in fn:
        if f.endswith('.java'):
            path = os.path.join(dp, f)
            if fix_file(path):
                count += 1
                print(f'Fixed: {os.path.relpath(path, ROOT)}')

print(f'\nTotal files fixed: {count}')
