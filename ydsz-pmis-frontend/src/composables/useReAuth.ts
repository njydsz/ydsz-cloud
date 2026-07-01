/**
 * @file 敏感操作二次认证 Composable
 * @description 维护二次认证弹窗状态机、表单、异步颁发 token；提供 withReAuth 装饰器包装业务调用
 * @module composables/useReAuth
 *
 * 提供高层 API：
 *  - useReAuth：维护弹窗状态机 / 表单 / 异步颁发
 *  - withReAuth：以装饰器方式包装业务调用，自动弹窗→颁发→重放
 *
 * 典型用法（删除用户）：
 * ```ts
 * const { withReAuth, dialog } = useReAuth({
 *   operationCode: 'USER_DELETE',
 *   operationName: '删除用户',
 * })
 *
 * async function onDelete(row) {
 *   await withReAuth(async (token) => {
 *     await request({
 *       url: `/users/${row.id}`,
 *       method: 'DELETE',
 *       headers: { 'X-Re-Auth-Token': token },
 *     })
 *     ElMessage.success('删除成功')
 *   })
 * }
 * ```
 */
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { issueReAuthToken, type ReAuthMethod } from '@/api/user/reauth'

export interface UseReAuthOptions {
  /** 操作码（与后端 @RequireReAuth.code() 一致） */
  operationCode: string
  /** 操作名（弹窗标题用） */
  operationName: string
  /** 默认凭据类型 */
  defaultMethod?: ReAuthMethod
  /** token 有效期（秒），默认 300 */
  ttlSeconds?: number
  /** 是否优先 TOTP（若用户已绑定） */
  preferTotp?: boolean
  /** 颁发失败时是否显示 ElMessage.error */
  silentOnError?: boolean
}

export interface ReAuthDialogState {
  visible: boolean
  loading: boolean
  method: ReAuthMethod
  password: string
  otp: string
  backupCode: string
  errorMessage: string
  countdown: number
}

const DEFAULT_TTL = 300

/**
 * 二次认证组合式函数
 */
export function useReAuth(options: UseReAuthOptions) {
  const dialog = reactive<ReAuthDialogState>({
    visible: false,
    loading: false,
    method: options.defaultMethod ?? 'PASSWORD',
    password: '',
    otp: '',
    backupCode: '',
    errorMessage: '',
    countdown: 0,
  })

  const pending = ref<{
    resolve: (token: string) => void
    reject: (err: Error) => void
  } | null>(null)

  function open() {
    dialog.visible = true
    dialog.loading = false
    dialog.password = ''
    dialog.otp = ''
    dialog.backupCode = ''
    dialog.errorMessage = ''
  }

  function close() {
    dialog.visible = false
    if (pending.value) {
      pending.value.reject(new Error('用户取消二次认证'))
      pending.value = null
    }
  }

  function setError(msg: string) {
    dialog.errorMessage = msg
  }

  /** 提交凭据，颁发 token */
  async function submit(): Promise<string> {
    if (dialog.loading) return ''
    dialog.errorMessage = ''
    dialog.loading = true
    try {
      const req: Parameters<typeof issueReAuthToken>[0] = {
        operationCode: options.operationCode,
        method: dialog.method,
        ttlSeconds: options.ttlSeconds ?? DEFAULT_TTL,
      }
      if (dialog.method === 'PASSWORD') {
        if (!dialog.password) {
          dialog.errorMessage = '请输入当前密码'
          return ''
        }
        req.password = dialog.password
      } else if (dialog.method === 'TOTP') {
        if (!/^\d{6}$/.test(dialog.otp)) {
          dialog.errorMessage = '请输入 6 位 TOTP 动态码'
          return ''
        }
        req.otp = dialog.otp
      } else if (dialog.method === 'BACKUP_CODE') {
        if (!dialog.backupCode) {
          dialog.errorMessage = '请输入备份码'
          return ''
        }
        req.backupCode = dialog.backupCode
      }

      const { data } = await issueReAuthToken(req)
      if (!data?.token) {
        dialog.errorMessage = '未获取到二次认证 token'
        return ''
      }
      return data.token
    } catch (e: any) {
      // 捕获 API 错误，转换成错误提示而非抛出
      dialog.errorMessage = e?.message || '二次认证失败'
      return ''
    } finally {
      dialog.loading = false
    }
  }

  /** 触发弹窗并等待 token */
  function requestToken(): Promise<string> {
    return new Promise<string>((resolve, reject) => {
      pending.value = { resolve, reject }
      open()
    })
  }

  async function handleConfirm() {
    try {
      const token = await submit()
      if (!token) return
      const p = pending.value
      pending.value = null
      dialog.visible = false
      p?.resolve(token)
    } catch (e: any) {
      // 表单内已显示错误，catch 静默
      if (!dialog.errorMessage) {
        if (!options.silentOnError) ElMessage.error(e?.message || '二次认证失败')
      }
    }
  }

  function handleCancel() {
    close()
  }

  /**
   * 装饰业务调用：自动弹窗 + 颁发 + 注入 X-Re-Auth-Token
   *
   * @param biz 业务回调，接收 token
   */
  async function withReAuth<T>(
    biz: (token: string) => Promise<T>,
  ): Promise<T | undefined> {
    try {
      const token = await requestToken()
      return await biz(token)
    } catch (e: any) {
      if (e?.message && e.message !== '用户取消二次认证' && !options.silentOnError) {
        // 后端 FORBIDDEN（含 20004 等）已在 request 拦截器提示
      }
      if (e?.message === '用户取消二次认证') {
        // 用户主动取消，吞掉
        return undefined
      }
      throw e
    }
  }

  return {
    dialog,
    options,
    requestToken,
    submit,
    handleConfirm,
    handleCancel,
    close,
    setError,
    withReAuth,
  }
}

/**
 * 便捷：注入 X-Re-Auth-Token 到现有请求头
 */
export function withReAuthHeader(
  base: Record<string, string> | undefined,
  token: string,
): Record<string, string> {
  return { ...(base || {}), 'X-Re-Auth-Token': token }
}

/** 静默包装器：捕获并提示错误 */
export async function safeWithReAuth<T>(
  reauth: ReturnType<typeof useReAuth>,
  biz: (token: string) => Promise<T>,
): Promise<T | undefined> {
  return reauth.withReAuth(biz)
}
