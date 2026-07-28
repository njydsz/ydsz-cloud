/**
 * User API 重导出
 * <p>从 {@code @ydsz/shared-auth} 统一 re-export 用户信息 API。
 * <p>提供 {@code getUserInfoApi}（获取当前用户信息）。
 * <p>供业务代码统一 {@code import { getUserInfoApi } from '#/api/core/user'} 引用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
export {
  getUserInfoApi,
} from '@ydsz/shared-auth';
