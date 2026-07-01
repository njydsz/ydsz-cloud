-- ====================================================================
-- 9. 初始化菜单权限 + 角色授权 (admin 拥有全部权限)
-- ====================================================================

-- 一. 初始化菜单权限
-- 使用 CTE 风格: 先建子节点,再将 id 关联到父 perm_code
WITH inserted AS (
    INSERT INTO pmis_permission
    (parent_id, perm_code, perm_name, perm_type, path, component, icon, sort_order, visible, status, created_by)
    VALUES
        -- 仪表盘
        (0, 'dashboard',          '仪表盘',          'MENU',   '/dashboard',       'dashboard/index',                 'odometer',  1, 1, 'ENABLED', 0),
        -- 系统管理根
        (0, 'system',             '系统管理',        'MENU',   '/system',          'Layout',                          'setting',   2, 1, 'ENABLED', 0),
        -- 系统管理子菜单
        ((SELECT id FROM pmis_permission WHERE perm_code = 'system'), 'system:user',     '用户管理',     'MENU', '/system/user',     'system/user/index',     'user',     1, 1, 'ENABLED', 0),
        ((SELECT id FROM pmis_permission WHERE perm_code = 'system'), 'system:role',     '角色管理',     'MENU', '/system/role',     'system/role/index',     'avatar',   2, 1, 'ENABLED', 0),
        ((SELECT id FROM pmis_permission WHERE perm_code = 'system'), 'system:menu',     '菜单管理',     'MENU', '/system/menu',     'system/menu/index',     'menu',     3, 1, 'ENABLED', 0),
        ((SELECT id FROM pmis_permission WHERE perm_code = 'system'), 'system:dept',     '部门管理',     'MENU', '/system/dept',     'system/dept/index',     'office-building', 4, 1, 'ENABLED', 0),
        ((SELECT id FROM pmis_permission WHERE perm_code = 'system'), 'system:dict',     '数据字典',     'MENU', '/system/dict',     'system/dict/index',     'collection', 5, 1, 'ENABLED', 0),
        ((SELECT id FROM pmis_permission WHERE perm_code = 'system'), 'system:job-level','职级管理',     'MENU', '/system/job-level','system/job-level/index', 'medal',    6, 1, 'ENABLED', 0),
        ((SELECT id FROM pmis_permission WHERE perm_code = 'system'), 'system:config',   '参数配置',     'MENU', '/system/config',   'system/config/index',   'tools',    7, 1, 'ENABLED', 0),
        -- 业务根
        (0, 'business',           '业务管理',        'MENU',   '/business',        'Layout',                          'briefcase', 3, 1, 'ENABLED', 0),
        ((SELECT id FROM pmis_permission WHERE perm_code = 'business'), 'business:opportunity', '商机管理', 'MENU', '/business/opportunity', 'business/opportunity/index', 'lightbulb', 1, 1, 'ENABLED', 0),
        ((SELECT id FROM pmis_permission WHERE perm_code = 'business'), 'business:initiation',  '立项管理', 'MENU', '/business/initiation',  'business/initiation/index',  'document', 2, 1, 'ENABLED', 0),
        ((SELECT id FROM pmis_permission WHERE perm_code = 'business'), 'business:contract',     '合同管理', 'MENU', '/business/contract',     'business/contract/index',     'tickets', 3, 1, 'ENABLED', 0),
        ((SELECT id FROM pmis_permission WHERE perm_code = 'business'), 'business:change',       '变更管理', 'MENU', '/business/change',       'business/change/index',       'refresh', 4, 1, 'ENABLED', 0),
        -- 执行根
        (0, 'execution',          '项目执行',        'MENU',   '/execution',       'Layout',                          'cpu',      4, 1, 'ENABLED', 0),
        ((SELECT id FROM pmis_permission WHERE perm_code = 'execution'), 'execution:wbs',         'WBS 任务',  'MENU', '/execution/wbs',         'execution/wbs/index',         'list',     1, 1, 'ENABLED', 0),
        ((SELECT id FROM pmis_permission WHERE perm_code = 'execution'), 'execution:timesheet',   '工时管理',  'MENU', '/execution/timesheet',   'execution/timesheet/index',   'timer',    2, 1, 'ENABLED', 0),
        ((SELECT id FROM pmis_permission WHERE perm_code = 'execution'), 'execution:attendance',  '考勤管理',  'MENU', '/execution/attendance',  'execution/attendance/index',  'calendar', 3, 1, 'ENABLED', 0),
        ((SELECT id FROM pmis_permission WHERE perm_code = 'execution'), 'execution:cost',        '成本管理',  'MENU', '/execution/cost',        'execution/cost/index',        'money',    4, 1, 'ENABLED', 0),
        ((SELECT id FROM pmis_permission WHERE perm_code = 'execution'), 'execution:revenue',     '收入管理',  'MENU', '/execution/revenue',     'execution/revenue/index',     'wallet',   5, 1, 'ENABLED', 0),
        ((SELECT id FROM pmis_permission WHERE perm_code = 'execution'), 'execution:risk',        '风险登记',  'MENU', '/execution/risk',        'execution/risk/index',        'warning',  6, 1, 'ENABLED', 0),
        ((SELECT id FROM pmis_permission WHERE perm_code = 'execution'), 'execution:profit',      '利润分析',  'MENU', '/execution/profit',      'execution/profit/index',      'data-line',7, 1, 'ENABLED', 0),
        ((SELECT id FROM pmis_permission WHERE perm_code = 'execution'), 'execution:delivery',    '交付管理',  'MENU', '/execution/delivery',    'execution/delivery/index',    'box',      8, 1, 'ENABLED', 0),
        -- 财务根
        (0, 'finance',            '财务收支',        'MENU',   '/finance',         'Layout',                          'credit-card', 5, 1, 'ENABLED', 0),
        ((SELECT id FROM pmis_permission WHERE perm_code = 'finance'), 'finance:invoice',  '发票管理', 'MENU', '/finance/invoice',  'finance/invoice/index',  'document-copy', 1, 1, 'ENABLED', 0),
        ((SELECT id FROM pmis_permission WHERE perm_code = 'finance'), 'finance:payment',  '回款管理', 'MENU', '/finance/payment',  'finance/payment/index',  'bank-card',    2, 1, 'ENABLED', 0),
        -- 报表根
        (0, 'report',             '经营报表',        'MENU',   '/report',          'Layout',                          'data-analysis', 6, 1, 'ENABLED', 0),
        ((SELECT id FROM pmis_permission WHERE perm_code = 'report'), 'report:profit',    '项目利润',   'MENU', '/report/profit',    'report/profit/index',    'pie-chart',    1, 1, 'ENABLED', 0),
        ((SELECT id FROM pmis_permission WHERE perm_code = 'report'), 'report:cost',      '成本明细',   'MENU', '/report/cost',      'report/cost/index',      'data-board',   2, 1, 'ENABLED', 0),
        ((SELECT id FROM pmis_permission WHERE perm_code = 'report'), 'report:lifecycle', '生命周期台账', 'MENU', '/report/lifecycle', 'report/lifecycle/index', 'connection',   3, 1, 'ENABLED', 0),
        -- AI 根
        (0, 'ai',                 '智能助手',        'MENU',   '/ai',              'Layout',                          'magic-stick', 7, 1, 'ENABLED', 0),
        ((SELECT id FROM pmis_permission WHERE perm_code = 'ai'), 'ai:agents',  'AI Agents', 'MENU', '/ai/agents', 'ai/agents/index', 'chat-dot-round', 1, 1, 'ENABLED', 0),
        -- 按钮级权限
        ((SELECT id FROM pmis_permission WHERE perm_code = 'system:user'), 'auth:user:create', '新增用户', 'BUTTON', null, null, null, 1, 0, 'ENABLED', 0),
        ((SELECT id FROM pmis_permission WHERE perm_code = 'system:user'), 'auth:user:update', '编辑用户', 'BUTTON', null, null, null, 2, 0, 'ENABLED', 0),
        ((SELECT id FROM pmis_permission WHERE perm_code = 'system:user'), 'auth:user:delete', '删除用户', 'BUTTON', null, null, null, 3, 0, 'ENABLED', 0),
        ((SELECT id FROM pmis_permission WHERE perm_code = 'system:user'), 'auth:user:reset',  '重置密码', 'BUTTON', null, null, null, 4, 0, 'ENABLED', 0),
        ((SELECT id FROM pmis_permission WHERE perm_code = 'system:role'), 'auth:role:create', '新增角色', 'BUTTON', null, null, null, 1, 0, 'ENABLED', 0),
        ((SELECT id FROM pmis_permission WHERE perm_code = 'system:role'), 'auth:role:update', '编辑角色', 'BUTTON', null, null, null, 2, 0, 'ENABLED', 0),
        ((SELECT id FROM pmis_permission WHERE perm_code = 'system:role'), 'auth:role:delete', '删除角色', 'BUTTON', null, null, null, 3, 0, 'ENABLED', 0),
        ((SELECT id FROM pmis_permission WHERE perm_code = 'system:role'), 'auth:role:assign', '分配权限', 'BUTTON', null, null, null, 4, 0, 'ENABLED', 0),
        ((SELECT id FROM pmis_permission WHERE perm_code = 'system:menu'), 'auth:perm:create', '新增菜单', 'BUTTON', null, null, null, 1, 0, 'ENABLED', 0),
        ((SELECT id FROM pmis_permission WHERE perm_code = 'system:menu'), 'auth:perm:update', '编辑菜单', 'BUTTON', null, null, null, 2, 0, 'ENABLED', 0),
        ((SELECT id FROM pmis_permission WHERE perm_code = 'system:menu'), 'auth:perm:delete', '删除菜单', 'BUTTON', null, null, null, 3, 0, 'ENABLED', 0)
    ON CONFLICT (perm_code, deleted) DO NOTHING
    RETURNING id
)
SELECT count(*) FROM inserted;

-- 二. SUPER_ADMIN 角色绑定所有权限
INSERT INTO pmis_role_permission (role_id, permission_id, created_by)
SELECT
    (SELECT id FROM pmis_role WHERE role_code = 'SUPER_ADMIN'),
    p.id,
    0
FROM pmis_permission p
WHERE p.deleted = 0
  AND p.status = 'ENABLED'
ON CONFLICT DO NOTHING;

-- 三. admin 用户绑定到 SUPER_ADMIN 角色
INSERT INTO pmis_user_role (user_id, role_id, created_by)
SELECT
    (SELECT id FROM pmis_user_account WHERE username = 'admin'),
    (SELECT id FROM pmis_role WHERE role_code = 'SUPER_ADMIN'),
    0
ON CONFLICT DO NOTHING;
