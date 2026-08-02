/**
 * index 配置模块
 *
 * @path conf\vite-config\src\plugins\inject-app-loading\index.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { PluginOption } from 'vite';

import fs from 'node:fs';
import fsp from 'node:fs/promises';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

import { readPackageJSON } from '@ydsz/node-utils';

/**
 * 将统一的应用启动 loading 样式/脚本注入 HTML，免去各 app 单独维护。
 *
 * 通过注入脚本提前从 localStorage 读取主题，保证深色主题下刷新时 loading 也
 * 保持深色，避免首屏闪烁；支持在 app 内以自定义模板覆盖默认 loading。
 *
 * @param isBuild - 是否为构建模式（影响缓存主题标识 dev/prod）
 * @param env - 环境变量（用于拼接主题缓存命名空间）
 * @param loadingTemplate - loading 模板文件名，缺省 'loading.html'
 * @returns Vite 插件对象；模板缺失时返回 undefined
 */
async function viteInjectAppLoadingPlugin(
  isBuild: boolean,
  env: Record<string, any> = {},
  loadingTemplate = 'loading.html',
): Promise<PluginOption | undefined> {
  const loadingHtml = await getLoadingRawByHtmlTemplate(loadingTemplate);
  const { version } = await readPackageJSON(process.cwd());
  const envRaw = isBuild ? 'prod' : 'dev';
  const cacheName = `'${env.VITE_APP_NAMESPACE}-${version}-${envRaw}-preferences-theme'`;

  // 获取缓存的主题
  // 保证黑暗主题下，刷新页面时，loading也是黑暗主题
  const injectScript = `
  <script data-app-loading="inject-js">
  var theme = localStorage.getItem(${cacheName});
  document.documentElement.classList.toggle('dark', /dark/.test(theme));
</script>
`;

  if (!loadingHtml) {
    return;
  }

  return {
    enforce: 'pre',
    name: 'vite:inject-app-loading',
    transformIndexHtml: {
      handler(html) {
        const re = /<body\s*>/;
        html = html.replace(re, `<body>${injectScript}${loadingHtml}`);
        return html;
      },
      order: 'pre',
    },
  };
}

/**
 * 读取 loading 的 HTML 模板内容。
 *
 * 优先使用 app 根目录下的自定义模板，缺失时回退到插件内置的 default-loading.html。
 *
 * @param loadingTemplate - loading 模板文件名
 * @returns 模板 HTML 字符串；文件均不存在时返回空串
 */
async function getLoadingRawByHtmlTemplate(loadingTemplate: string) {
  // 支持在app内自定义loading模板，模版参考default-loading.html即可
  let appLoadingPath = join(process.cwd(), loadingTemplate);

  if (!fs.existsSync(appLoadingPath)) {
    const __dirname = fileURLToPath(new URL('.', import.meta.url));
    appLoadingPath = join(__dirname, './default-loading.html');
  }

  return await fsp.readFile(appLoadingPath, 'utf8');
}

export { viteInjectAppLoadingPlugin };
