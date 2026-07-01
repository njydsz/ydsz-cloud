/**
 * @file 系统管理模块 Mock 数据处理器
 * @description 为系统管理类接口提供 Mock 数据。
 *
 * 覆盖以下模块:
 * - 操作日志 (audit)
 * - 系统配置 (config)
 * - 字典 (dict)
 * - 部门 (dept)
 * - 菜单 (menu)
 * - 角色 (role)
 * - 导入导出 (import-export)
 *
 * 注: 2FA/会话/用户相关 mock 见 user.ts
 *
 * @module mock/handlers/system
 */
import type { MockHandler } from './types'

/**
 * 批量生成 Mock 列表数据
 * @param n 生成的记录数量
 * @param factory 单条记录工厂函数, 入参为序号 (从 1 开始)
 * @returns 生成的记录数组
 */
const list = (n: number, factory: (i: number) => Record<string, unknown>) =>
  Array.from({ length: n }, (_, i) => factory(i + 1))

/**
 * 系统管理模块 Mock 处理器集合
 *
 * 覆盖端点:
 * - GET /system/audit-log/page   操作日志分页查询
 * - GET /system/config/page      系统配置分页查询
 * - GET /system/config/by-key    按 key 查询系统配置
 * - GET /system/dict/page        字典分页查询
 * - GET /system/dict/type        字典类型查询
 * - GET /system/dept/tree        部门树查询
 * - GET /system/menu/tree        菜单树查询
 * - GET /system/role/page        角色分页查询
 *
 * @returns 系统管理模块所有 Mock 处理器数组
 */
export const systemHandlers: MockHandler[] = [
  // ============ 操作日志 (审计) ============
  {
    method: 'GET',
    path: '/system/audit-log/page',
    handler: ({ query }) => ({
      list: list(Number(query.size || 10), (i) => {
        const moduleName = ['project', 'execution', 'finance', 'system'][i % 4]
        return {
          id: i,
          userId: i,
          username: `用户${i}`,
          module: moduleName,
          action: ['CREATE', 'UPDATE', 'DELETE', 'QUERY'][i % 4],
          bizType: `${moduleName}:${i}:create`,
          requestUrl: `/api/v1/${moduleName}/create`,
          requestMethod: 'POST',
          success: i % 5 !== 0,
          costMs: 50 + ((i * 13) % 200),
          clientIp: `192.168.1.${i % 256}`,
          verifiedAt: '2026-07-01 10:00:00',
          createdAt: '2026-07-01 10:00:00',
        }
      }),
      total: 100,
      page: Number(query.page || 1),
      size: Number(query.size || 10),
      pages: 10,
    }),
  },

  // ============ 系统配置 ============
  {
    method: 'GET',
    path: '/system/config/page',
    handler: ({ query }) => ({
      list: list(Number(query.size || 10), (i) => ({
        id: i,
        configKey: `sys.config.${i}`,
        configValue: `value-${i}`,
        configGroup: ['system', 'security', 'business'][i % 3],
        remark: `配置项 ${i}`,
      })),
      total: 30,
      page: Number(query.page || 1),
      size: Number(query.size || 10),
      pages: 3,
    }),
  },
  {
    method: 'GET',
    path: '/system/config/by-key',
    handler: ({ query }) => ({
      configKey: query.key,
      configValue: 'mock-value',
      configGroup: 'system',
    }),
  },

  // ============ 字典 ============
  {
    method: 'GET',
    path: '/system/dict/page',
    handler: ({ query }) => ({
      list: list(Number(query.size || 10), (i) => ({
        id: i,
        dictType: `dict_type_${i}`,
        dictLabel: `标签${i}`,
        dictValue: `value_${i}`,
        sort: i,
        status: 1,
      })),
      total: 30,
      page: Number(query.page || 1),
      size: Number(query.size || 10),
      pages: 3,
    }),
  },
  {
    method: 'GET',
    path: '/system/dict/type',
    handler: ({ query }) =>
      [
        { dictLabel: '是', dictValue: '1' },
        { dictLabel: '否', dictValue: '0' },
      ].filter((d) => !query.type || d.dictValue.startsWith(query.type)),
  },

  // ============ 部门 ============
  {
    method: 'GET',
    path: '/system/dept/tree',
    handler: () => [
      {
        id: 1,
        parentId: 0,
        deptName: '云顶科技',
        sort: 1,
        children: [
          { id: 11, parentId: 1, deptName: '技术中心', sort: 1, children: [] },
          { id: 12, parentId: 1, deptName: '销售中心', sort: 2, children: [] },
          { id: 13, parentId: 1, deptName: '财务中心', sort: 3, children: [] },
        ],
      },
    ],
  },

  // ============ 菜单 ============
  {
    method: 'GET',
    path: '/system/menu/tree',
    handler: () => [
      {
        id: 1,
        parentId: 0,
        menuName: '系统管理',
        path: '/system',
        icon: 'Setting',
        children: [
          { id: 11, parentId: 1, menuName: '用户管理', path: '/system/user', icon: 'User' },
          { id: 12, parentId: 1, menuName: '角色管理', path: '/system/role', icon: 'UserFilled' },
        ],
      },
    ],
  },

  // ============ 角色 ============
  {
    method: 'GET',
    path: '/system/role/page',
    handler: ({ query }) => ({
      list: list(Number(query.size || 10), (i) => ({
        id: i,
        roleCode: `ROLE_${i}`,
        roleName: `角色${i}`,
        remark: `测试角色 ${i}`,
        status: 1,
      })),
      total: 20,
      page: Number(query.page || 1),
      size: Number(query.size || 10),
      pages: 2,
    }),
  },
]
