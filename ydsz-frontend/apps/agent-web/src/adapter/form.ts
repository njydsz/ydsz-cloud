/**
 * form 适配器（应用级 re-export）
 *
 * 统一实现已提取至 @ydsz/shared-business，此处保留应用级入口以兼容既有导入路径。
 *
 * @path apps/agent-web/src/adapter/form.ts
 * @author ydsz-team
 * @since 1.1.0
 */
export {
  initSetupYDSZForm,
  useYDSZForm,
  z,
  type YDSZFormProps,
  type YDSZFormSchema,
} from '@ydsz/shared-business';
