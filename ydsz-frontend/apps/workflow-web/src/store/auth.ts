/**
 * Auth Store（工作流子应用）
 * <p>封装 ydsz-workflow 子应用的 Pinia 认证状态：accessToken / refreshToken / userInfo / accessCodes。
 * <p>底层基于 {@code @ydsz/shared-auth} 的 {@code createSharedAuthStore}，与 9 个子应用共享同一份登录/刷新/登出逻辑。
 * <p>供路由守卫、菜单渲染、按钮级权限校验使用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
export { createSharedAuthStore } from '@ydsz/shared-auth';

const useAuthStore = createSharedAuthStore();
export { useAuthStore };
