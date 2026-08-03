/**
 * design 包 Vite 构建配置：将 scss-bem 源码目录作为静态资源目录。
 *
 * @remarks
 * 供 @core 设计系统按 scss-bem 命名方案产出样式时使用，其余构建行为由共享 vite-config 提供。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import { defineConfig } from '@ydsz/vite-config';

export default defineConfig(async () => {
  return {
    vite: {
      publicDir: 'src/scss-bem',
    },
  };
});
