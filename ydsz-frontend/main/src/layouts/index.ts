/**
 * index 模块
 *
 * @path main\src\layouts\index.ts
 * @author ydsz-team
 * @since 1.0.0
 */
const BasicLayout = () => import('./basic.vue');
const AuthPageLayout = () => import('./auth.vue');

const IFrameView = () => import('@ydsz/layouts').then((m) => m.IFrameView);

export { AuthPageLayout, BasicLayout, IFrameView };
