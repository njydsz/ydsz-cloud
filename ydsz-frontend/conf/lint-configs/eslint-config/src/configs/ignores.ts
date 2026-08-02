/**
 * ignores 配置模块
 *
 * @path conf\lint-configs\eslint-config\src\configs\ignores.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { Linter } from 'eslint';

/**
 * 声明 ESLint 全局忽略的目录与文件。
 *
 * 覆盖依赖、构建产物、缓存、锁文件等无需 lint 的路径，避免误报与性能损耗。
 *
 * @returns 含 ignores 的 ESLint flat 配置数组
 */
export async function ignores(): Promise<Linter.Config[]> {
  return [
    {
      ignores: [
        '**/node_modules',
        '**/dist',
        '**/dist-*',
        '**/*-dist',
        '**/.husky',
        '**/.nitro',
        '**/.output',
        '**/Dockerfile',
        '**/package-lock.json',
        '**/yarn.lock',
        '**/pnpm-lock.yaml',
        '**/bun.lockb',
        '**/output',
        '**/coverage',
        '**/temp',
        '**/.temp',
        '**/tmp',
        '**/.tmp',
        '**/.history',
        '**/.turbo',
        '**/.nuxt',
        '**/.next',
        '**/.vercel',
        '**/.changeset',
        '**/.idea',
        '**/.cache',
        '**/.output',
        '**/.vite-inspect',

        '**/CHANGELOG*.md',
        '**/*.min.*',
        '**/LICENSE*',
        '**/__snapshots__',
        '**/*.snap',
        '**/fixtures/**',
        '**/.vitepress/cache/**',
        '**/auto-import?(s).d.ts',
        '**/components.d.ts',
        '**/vite.config.mts.*',
        '**/*.sh',
        '**/*.ttf',
        '**/*.woff',
      ],
    },
  ];
}
