/**
 * Vitest 工作区配置
 *
 * <p>Monorepo 多项目测试工作区入口，聚合 vitest.config.ts 配置。
 *
 * @path vitest.workspace.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import { defineWorkspace } from 'vitest/config';

export default defineWorkspace(['vitest.config.ts']);
