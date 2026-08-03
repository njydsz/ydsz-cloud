/**
 * micro-kernel 接入指南
 *
 * 子应用接入 micro-kernel 时只需两步：
 *
 * 1. vite.config.ts 中添加 manifest 插件：
 *    import { viteManifestPlugin } from '@ydsz/micro-kernel';
 *    export default defineApplicationConfig(() => ({
 *      application: {
 *        // 在现有 options 中加一条
 *        manifest: { name: 'project-web', version: '2.3.1' }
 *      }
 *    }));
 *
 * 2. main.ts 中 createSubApp 一行无需改动（defineSubApp 双兼容）
 *
 * 构建后 dist/ 输出 version.json 供 micro-kernel 加载。
 */
