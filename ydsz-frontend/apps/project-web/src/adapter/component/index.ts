/**
 * 表单组件适配器（应用级 re-export）
 *
 * 统一实现已提取至 @ydsz/shared-business，此处保留应用级入口以兼容既有导入路径。
 *
 * @path apps/project-web/src/adapter/component/index.ts
 * @author ydsz-team
 * @since 1.1.0
 */
export {
  initComponentAdapter,
  type ComponentType,
} from '@ydsz/shared-business';
