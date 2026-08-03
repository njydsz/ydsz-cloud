/**
 * 骨架屏注册表模块
 *
 * 管理页面级骨架屏组件的注册和获取，支持按类型动态加载骨架屏。
 *
 * @path main/src/views/_core/subapp/skeletons/skeleton-registry.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import { defineAsyncComponent, type Component } from 'vue';

/** 骨架屏类型 */
export type SkeletonType = 'dashboard' | 'default' | 'detail' | 'form' | 'list';

/** 骨架屏组件映射表 */
const skeletonComponents: Record<SkeletonType, Component> = {
  list: defineAsyncComponent(() => import('./ListSkeleton.vue')),
  form: defineAsyncComponent(() => import('./FormSkeleton.vue')),
  detail: defineAsyncComponent(() => import('./DetailSkeleton.vue')),
  dashboard: defineAsyncComponent(() => import('./DashboardSkeleton.vue')),
  default: defineAsyncComponent(() => import('./DefaultSkeleton.vue')),
};

/**
 * 获取骨架屏组件
 *
 * @param type - 骨架屏类型，默认 'default'
 * @returns 对应的骨架屏组件
 */
export function getSkeletonComponent(type: SkeletonType = 'default'): Component {
  return skeletonComponents[type] || skeletonComponents.default;
}

/**
 * 扩展 vue-router 的 RouteMeta 类型，支持 skeletonType 配置
 */
declare module 'vue-router' {
  interface RouteMeta {
    /** 页面骨架屏类型 */
    skeletonType?: SkeletonType;
  }
}