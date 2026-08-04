/**
 * use-access 数据级权限单元测试
 *
 * @path comm\effects\access\src\use-access.test.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('@ydsz/preferences', () => ({
  preferences: { app: { accessMode: 'frontend' } },
  updatePreferences: vi.fn(),
}));

import { useAccessStore } from '@ydsz/stores';

import { useAccess } from './use-access';

describe('useAccess 数据级权限', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  describe('hasDataScope / getDataScope', () => {
    it('未设置 dataScopes 时视为未受限', () => {
      const { hasDataScope, getDataScope } = useAccess();
      expect(hasDataScope('project:budget')).toBe(true);
      expect(getDataScope('project:budget')).toBeUndefined();
    });

    it('设置数据范围后可读取', () => {
      const store = useAccessStore();
      store.setDataScopes({
        'project:budget': { deptIds: [1, 2, 3] },
        'project:revenue': [],
      });
      const { hasDataScope, getDataScope } = useAccess();
      expect(hasDataScope('project:budget')).toBe(true);
      expect(getDataScope<{ deptIds: number[] }>('project:budget')).toEqual({
        deptIds: [1, 2, 3],
      });
      // 空数组视为完全受限
      expect(hasDataScope('project:revenue')).toBe(false);
    });

    it('未受约束的资源返回 true', () => {
      const store = useAccessStore();
      store.setDataScopes({ 'project:budget': { deptIds: [1] } });
      const { hasDataScope } = useAccess();
      expect(hasDataScope('project:unknown')).toBe(true);
    });
  });

  describe('getFieldPermission / applyFieldMask', () => {
    it('未设置字段权限时默认 read', () => {
      const { getFieldPermission, applyFieldMask } = useAccess();
      expect(getFieldPermission('project.budget.amount')).toBe('read');
      expect(applyFieldMask('project.budget.amount', 12345)).toBe('12345');
    });

    it('mask 模式脱敏处理', () => {
      const store = useAccessStore();
      store.setFieldPermissions({ 'user.phone': 'mask' });
      const { applyFieldMask } = useAccess();
      // 11 位手机号：保留前 2 + 后 2，中间 4 个 *
      expect(applyFieldMask('user.phone', '13800138000')).toBe('13****00');
    });

    it('短字符串脱敏', () => {
      const store = useAccessStore();
      store.setFieldPermissions({ 'user.name': 'mask' });
      const { applyFieldMask } = useAccess();
      expect(applyFieldMask('user.name', '张三')).toBe('**');
      expect(applyFieldMask('user.name', '王五丰')).toBe('王*丰');
    });

    it('hidden 模式返回空字符串', () => {
      const store = useAccessStore();
      store.setFieldPermissions({ 'project.salary': 'hidden' });
      const { applyFieldMask } = useAccess();
      expect(applyFieldMask('project.salary', 99999)).toBe('');
    });

    it('null/undefined 值处理', () => {
      const { applyFieldMask } = useAccess();
      expect(applyFieldMask('any.field', null)).toBe('');
      expect(applyFieldMask('any.field', undefined)).toBe('');
    });
  });
});
