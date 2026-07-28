/**
 * 布局组件懒加载入口
 * <p>封装当前子应用所有布局组件（{@code BasicLayout} / {@code IFrameView}）的按需加载入口。
 * <p>{@code BasicLayout} 来自当前子应用本地（{@code ./basic.vue}），{@code IFrameView} 来自 {@code @ydsz/layouts} 公共包。
 * <p>供路由表按需引用，避免一次性加载所有布局。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
const BasicLayout = () => import('./basic.vue');

const IFrameView = () => import('@ydsz/layouts').then((m) => m.IFrameView);

export { BasicLayout, IFrameView };
