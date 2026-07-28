/**
 * Auth API 重导出
 * <p>从 {@code @ydsz/shared-auth} 统一 re-export 认证相关 API。
 * <p>提供 {@code loginApi}（登录）、{@code logoutApi}（登出）、{@code refreshTokenApi}（刷新 Token）、{@code getAccessCodesApi}（权限码）。
 * <p>供业务代码统一 {@code import { loginApi } from '#/api/core/auth'} 引用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
export {
  loginApi,
  logoutApi,
  refreshTokenApi,
  getAccessCodesApi,
} from '@ydsz/shared-auth';
