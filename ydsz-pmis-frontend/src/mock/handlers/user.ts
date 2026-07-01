/**
 * 用户模块 mock (用户/角色/权限/部门)
 */
import type { MockHandler } from './types'

export const userHandlers: MockHandler[] = [
  {
    method: 'GET',
    path: '/user/info',
    handler: () => ({
      id: 1,
      username: 'admin',
      realName: '系统管理员',
      email: 'admin@pmis.local',
      phone: '13800000000',
      avatar: '',
      department: '技术中心',
      role: 'SUPER_ADMIN',
    }),
  },
  {
    method: 'GET',
    path: '/user/menu',
    handler: () => [
      { id: 1, name: '系统管理', path: '/system', icon: 'Setting' },
      { id: 2, name: '项目管理', path: '/project', icon: 'Folder' },
      { id: 3, name: '执行管理', path: '/execution', icon: 'Operation' },
      { id: 4, name: '财务管理', path: '/finance', icon: 'Money' },
      { id: 5, name: '报表中心', path: '/report', icon: 'DataAnalysis' },
    ],
  },
  {
    method: 'GET',
    path: '/user/permission',
    handler: () => ['*.*.*']),
}
