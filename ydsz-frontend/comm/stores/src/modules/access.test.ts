/**
 * access.test Pinia 状态管理
 *
 * @path comm\stores\src\modules\access.test.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it } from 'vitest';

import { useAccessStore } from './access';

describe('useAccessStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it('updates accessMenus state', () => {
    const store = useAccessStore();
    expect(store.accessMenus).toEqual([]);
    store.setAccessMenus([{ name: 'Dashboard', path: '/dashboard' }]);
    expect(store.accessMenus).toEqual([
      { name: 'Dashboard', path: '/dashboard' },
    ]);
  });

  // 测试设置空的访问菜单列表
  it('handles empty accessMenus correctly', () => {
    const store = useAccessStore();
    store.setAccessMenus([]);
    expect(store.accessMenus).toEqual([]);
  });

  // 测试设置空的访问路由列表
  it('handles empty accessRoutes correctly', () => {
    const store = useAccessStore();
    store.setAccessRoutes([]);
    expect(store.accessRoutes).toEqual([]);
  });

  // 测试设置权限码
  it('updates accessCodes correctly', () => {
    const store = useAccessStore();
    expect(store.accessCodes).toEqual([]);
    store.setAccessCodes(['AC_100100', 'AC_100200']);
    expect(store.accessCodes).toEqual(['AC_100100', 'AC_100200']);
  });

  // 测试 isAccessChecked 标记
  it('updates isAccessChecked correctly', () => {
    const store = useAccessStore();
    expect(store.isAccessChecked).toBe(false);
    store.setIsAccessChecked(true);
    expect(store.isAccessChecked).toBe(true);
  });
});
