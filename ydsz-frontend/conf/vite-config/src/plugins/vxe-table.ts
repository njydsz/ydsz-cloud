/**
 * vxe-table 配置模块
 *
 * @path conf\vite-config\src\plugins\vxe-table.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { PluginOption } from 'vite';

import { lazyImport, VxeResolver } from 'vite-plugin-lazy-import';

/**
 * 为 vxe-table / vxe-pc-ui 开启按需懒加载的 Vite 插件。
 *
 * 借助 vite-plugin-lazy-import 的 VxeResolver，按使用点位自动引入组件，
 * 避免全量打包以减小产物体积。
 *
 * @returns Vite 插件对象（懒加载解析器数组）
 */
async function viteVxeTableImportsPlugin(): Promise<PluginOption> {
  return [
    lazyImport({
      resolvers: [
        VxeResolver({
          libraryName: 'vxe-table',
        }),
        VxeResolver({
          libraryName: 'vxe-pc-ui',
        }),
      ],
    }),
  ];
}

export { viteVxeTableImportsPlugin };
