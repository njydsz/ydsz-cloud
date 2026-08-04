/**
 * vxe-table 适配器（应用级 re-export）
 *
 * 统一实现已提取至 @ydsz/shared-business，此处保留应用级入口以兼容既有导入路径。
 * 如需应用级扩展自定义 renderer，可在此文件内补充。
 *
 * @path apps/userinfo-web/src/adapter/vxe-table.ts
 * @author ydsz-team
 * @since 1.1.0
 */
export { useYDSZVxeGrid } from '@ydsz/shared-business';

export type * from '@ydsz/plugins/vxe-table';
