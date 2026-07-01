/**
 * @file ReAuthDialog 敏感操作二次认证弹窗组件 单元测试
 * @description 覆盖 props 透传、显隐渲染、errorMessage 告警、2FA 方式单选、
 *   密码 / OTP / 备份码凭据输入、update:visible 与 confirm 事件派发及 loading 防抖等场景.
 * @module components/common/__tests__/ReAuthDialog
 */
import { describe, it, expect, vi } from 'vitest'
import { nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import ReAuthDialog from '@/components/common/ReAuthDialog.vue'

// Stub el-dialog to bypass teleport rendering
const ElDialogStub = {
  props: [
    'modelValue',
    'title',
    'width',
    'closeOnClickModal',
    'closeOnPressEscape',
    'showClose',
    'alignCenter',
  ],
  emits: ['update:modelValue', 'cancel', 'closed'],
  template:
    '<div v-if="modelValue" class="el-dialog-stub"><div class="dialog-title">{{ title }}</div><slot /><slot name="footer" /></div>',
}

const ElRadioButtonStub = {
  props: ['value'],
  template: '<label class="el-radio-button-stub"><input type="radio" :value="value" /><slot /></label>',
}

const ElRadioGroupStub = {
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template:
    '<div class="el-radio-group-stub"><slot :model-value="modelValue" @update:model-value="$emit(\'update:modelValue\', $event)" /></div>',
}

const ElInputStub = {
  props: ['modelValue', 'type', 'showPassword', 'placeholder', 'disabled', 'maxlength'],
  emits: ['update:modelValue'],
  template:
    '<input class="el-input-stub" :value="modelValue" :type="type" :disabled="disabled" @input="$emit(\'update:modelValue\', $event.target.value)" />',
}

const ElButtonStub = {
  props: ['loading', 'disabled', 'type'],
  template: '<button class="el-btn" :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
}

const ElAlertStub = {
  props: ['type', 'title', 'description', 'closable', 'showIcon'],
  template: '<div class="el-alert-stub" :data-type="type"><div class="alert-title">{{ title }}</div><div class="alert-desc">{{ description }}</div></div>',
}

const ElIconStub = {
  template: '<i class="el-icon"><slot /></i>',
}

const stubs = {
  'el-dialog': ElDialogStub,
  'el-radio-button': ElRadioButtonStub,
  'el-radio-group': ElRadioGroupStub,
  'el-input': ElInputStub,
  'el-button': ElButtonStub,
  'el-alert': ElAlertStub,
  'el-icon': ElIconStub,
}

describe('ReAuthDialog 敏感操作二次认证弹窗', () => {
  it('应正确接收 operationCode / operationName 等 props', () => {
    const wrapper = mount(ReAuthDialog, {
      props: {
        visible: true,
        operationCode: 'USER_DELETE',
        operationName: '删除用户',
        loading: false,
      },
      global: { stubs },
    })
    expect(wrapper.props('operationCode')).toBe('USER_DELETE')
    expect(wrapper.props('operationName')).toBe('删除用户')
  })

  it('visible=false 时弹窗不渲染', () => {
    const wrapper = mount(ReAuthDialog, {
      props: {
        visible: false,
        operationCode: 'OP',
        operationName: 'name',
      },
      global: { stubs },
    })
    expect(wrapper.find('.el-dialog-stub').exists()).toBe(false)
  })

  it('visible=true 时弹窗渲染并展示标题', () => {
    const wrapper = mount(ReAuthDialog, {
      props: {
        visible: true,
        operationCode: 'USER_DELETE',
        operationName: '删除用户',
      },
      global: { stubs },
    })
    expect(wrapper.find('.el-dialog-stub').exists()).toBe(true)
    expect(wrapper.find('.dialog-title').text()).toContain('USER_DELETE')
  })

  it('errorMessage 渲染为 el-alert', () => {
    const wrapper = mount(ReAuthDialog, {
      props: {
        visible: true,
        operationCode: 'OP',
        operationName: 'name',
        method: 'PASSWORD',
        errorMessage: '凭据错误',
      },
      global: { stubs },
    })
    const alerts = wrapper.findAll('.el-alert-stub')
    expect(alerts.length).toBeGreaterThan(0)
    expect(wrapper.html()).toContain('凭据错误')
  })

  it('has2fa=false 时 PASSWORD 之外的单选按钮不渲染', () => {
    const wrapper = mount(ReAuthDialog, {
      props: {
        visible: true,
        operationCode: 'OP',
        operationName: 'name',
        method: 'PASSWORD',
        has2fa: false,
      },
      global: { stubs },
    })
    const radios = wrapper.findAll('.el-radio-button-stub')
    // 仅 PASSWORD
    expect(radios).toHaveLength(1)
  })

  it('has2fa=true 时 3 个单选按钮渲染', () => {
    const wrapper = mount(ReAuthDialog, {
      props: {
        visible: true,
        operationCode: 'OP',
        operationName: 'name',
        method: 'PASSWORD',
        has2fa: true,
      },
      global: { stubs },
    })
    const radios = wrapper.findAll('.el-radio-button-stub')
    expect(radios.length).toBe(3)
  })

  it('输入密码触发 update:modelValue', async () => {
    const wrapper = mount(ReAuthDialog, {
      props: {
        visible: true,
        operationCode: 'OP',
        operationName: 'name',
        method: 'PASSWORD',
      },
      global: { stubs },
    })
    const input = wrapper.find('input.el-input-stub')
    await input.setValue('mySecret')
    await nextTick()
    // 检查组件内部 password 状态变化（通过暴露属性或 ref）
    expect((wrapper.vm as any).password).toBe('mySecret')
  })

  it('update:visible 通过 onCancel 派发 false', async () => {
    const wrapper = mount(ReAuthDialog, {
      props: {
        visible: true,
        operationCode: 'OP',
        operationName: 'name',
      },
      global: { stubs },
    })
    ;(wrapper.vm as any).onCancel()
    await nextTick()
    expect(wrapper.emitted('update:visible')).toBeTruthy()
    expect((wrapper.emitted('update:visible') as boolean[][])[0][0]).toBe(false)
  })

  it('cancel 事件正常派发（通过 el-dialog 自身的 cancel）', () => {
    const wrapper = mount(ReAuthDialog, {
      props: {
        visible: true,
        operationCode: 'OP',
        operationName: 'name',
        method: 'PASSWORD',
      },
      global: { stubs },
    })
    // 直接调用组件暴露的 onCancel
    ;(wrapper.vm as any).onCancel()
    expect(wrapper.emitted('cancel')).toBeTruthy()
  })

  it('confirm 事件通过 onConfirm 派发 method + 凭据', async () => {
    const wrapper = mount(ReAuthDialog, {
      props: {
        visible: true,
        operationCode: 'OP',
        operationName: 'name',
        method: 'PASSWORD',
      },
      global: { stubs },
    })
    ;(wrapper.vm as any).password = 'secret'
    ;(wrapper.vm as any).onConfirm()
    await nextTick()
    expect(wrapper.emitted('confirm')).toBeTruthy()
    const payload = (wrapper.emitted('confirm') as any[][])[0][0]
    expect(payload.method).toBe('PASSWORD')
    expect(payload.password).toBe('secret')
  })

  it('loading=true 时 onConfirm 不派发', async () => {
    const wrapper = mount(ReAuthDialog, {
      props: {
        visible: true,
        operationCode: 'OP',
        operationName: 'name',
        method: 'PASSWORD',
        loading: true,
      },
      global: { stubs },
    })
    ;(wrapper.vm as any).password = 'p'
    ;(wrapper.vm as any).onConfirm()
    await nextTick()
    expect(wrapper.emitted('confirm')).toBeFalsy()
  })

  it('method=TOTP 时凭据使用 otp', async () => {
    const wrapper = mount(ReAuthDialog, {
      props: {
        visible: true,
        operationCode: 'OP',
        operationName: 'name',
        method: 'TOTP',
        has2fa: true,
      },
      global: { stubs },
    })
    ;(wrapper.vm as any).otp = '123456'
    ;(wrapper.vm as any).onConfirm()
    await nextTick()
    const payload = (wrapper.emitted('confirm') as any[][])[0][0]
    expect(payload.method).toBe('TOTP')
    expect(payload.otp).toBe('123456')
  })

  it('method=BACKUP_CODE 时凭据使用 backupCode', async () => {
    const wrapper = mount(ReAuthDialog, {
      props: {
        visible: true,
        operationCode: 'OP',
        operationName: 'name',
        method: 'BACKUP_CODE',
        has2fa: true,
      },
      global: { stubs },
    })
    ;(wrapper.vm as any).backupCode = 'aabbccdd'
    ;(wrapper.vm as any).onConfirm()
    await nextTick()
    const payload = (wrapper.emitted('confirm') as any[][])[0][0]
    expect(payload.method).toBe('BACKUP_CODE')
    expect(payload.backupCode).toBe('aabbccdd')
  })
})
