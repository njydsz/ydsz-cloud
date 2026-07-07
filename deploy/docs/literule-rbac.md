# LiteRule 规则引擎 RBAC 权限编码（P2-12）

> 规则引擎关键操作的权限编码清单，配合 `@PrePermission` 注解与系统权限管理使用。

## 权限编码矩阵

| 权限编码 | 说明 | 适用角色 |
|---------|------|---------|
| `execution:rule:save` | 新建/更新规则 | 规则编辑、规则管理员 |
| `execution:rule:toggle` | 启停规则（单条 + 批量） | 规则编辑、运维 |
| `execution:rule:delete` | 软删除规则 | 规则管理员 |
| `execution:rule:status` | 变更规则状态（DRAFT/REVIEW/PUBLISHED/ARCHIVED） | 规则管理员 |
| `execution:rule:approve` | 审批通过/驳回 | 规则审批人 |

## 角色建议

| 角色 | 权限编码 | 职责 |
|------|---------|------|
| 规则编辑 | `execution:rule:save`, `execution:rule:toggle` | 编写规则、启停规则，不能删除/发布 |
| 规则审批 | `execution:rule:approve`, `execution:rule:status` | 审批规则、变更状态，不能编辑规则内容 |
| 规则管理员 | 全部权限 | 完整管理，包括删除 |
| 运维 | `execution:rule:toggle` | 仅启停规则（应急） |
| 只读 | 无（查询接口不要求权限） | 查看规则列表、版本、统计 |

## 状态流转与权限对应

```
DRAFT ──save──→ DRAFT（编辑保存，需 execution:rule:save）
  │
  ├──approve──→ PUBLISHED（审批通过，需 execution:rule:approve）
  │                 │
  │                 ├──toggle──→ PUBLISHED(enabled=false)（停用，需 execution:rule:toggle）
  │                 │
  │                 └──status──→ ARCHIVED（下线，需 execution:rule:status）
  │
  └──reject───→ ARCHIVED（审批驳回，需 execution:rule:approve）
```

## 配置示例

```sql
-- 规则编辑角色
INSERT INTO sys_role_permission (role_code, permission_code) VALUES
('rule_editor', 'execution:rule:save'),
('rule_editor', 'execution:rule:toggle');

-- 规则审批角色
INSERT INTO sys_role_permission (role_code, permission_code) VALUES
('rule_approver', 'execution:rule:approve'),
('rule_approver', 'execution:rule:status');

-- 规则管理员角色（全部权限）
INSERT INTO sys_role_permission (role_code, permission_code) VALUES
('rule_admin', 'execution:rule:save'),
('rule_admin', 'execution:rule:toggle'),
('rule_admin', 'execution:rule:delete'),
('rule_admin', 'execution:rule:status'),
('rule_admin', 'execution:rule:approve');
```
