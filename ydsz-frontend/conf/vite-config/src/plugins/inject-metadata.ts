/**
 * inject-metadata 配置模块
 *
 * @path conf\vite-config\src\plugins\inject-metadata.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { PluginOption } from 'vite';

import {
  dateUtil,
  findMonorepoRoot,
  getPackages,
  readPackageJSON,
} from '@ydsz/node-utils';

import { readWorkspaceManifest } from '@pnpm/workspace.read-manifest';

/**
 * 将依赖版本声明解析为真实版本号。
 *
 * 支持 pnpm 的 `catalog:` 与 `workspace:*` 协议：catalog 取工作区 catalog 映射，
 * workspace 取本地包实际版本，其余原样返回。
 *
 * @param pkgsMeta - 本地包名到版本的映射
 * @param name - 依赖包名
 * @param value - 原始版本声明字符串
 * @param catalog - pnpm workspace catalog 映射
 * @returns 解析后的实际版本号
 */
function resolvePackageVersion(
  pkgsMeta: Record<string, string>,
  name: string,
  value: string,
  catalog: Record<string, string>,
) {
  if (value.includes('catalog:')) {
    return catalog[name];
  }

  if (value.includes('workspace')) {
    return pkgsMeta[name];
  }

  return value;
}

/**
 * 汇总大仓所有包的 dependencies / devDependencies 并解析真实版本。
 *
 * 遍历各子包，结合 {@link resolvePackageVersion} 处理 catalog/workspace 协议，
 * 供注入元数据时展示完整依赖树。
 *
 * @returns 含 dependencies 与 devDependencies 的解析结果
 */
async function resolveMonorepoDependencies() {
  const { packages } = await getPackages();
  const manifest = await readWorkspaceManifest(findMonorepoRoot());
  const catalog = manifest?.catalog || {};

  const resultDevDependencies: Record<string, string | undefined> = {};
  const resultDependencies: Record<string, string | undefined> = {};
  const pkgsMeta: Record<string, string> = {};

  for (const { packageJson } of packages) {
    pkgsMeta[packageJson.name] = packageJson.version;
  }

  for (const { packageJson } of packages) {
    const { dependencies = {}, devDependencies = {} } = packageJson;
    for (const [key, value] of Object.entries(dependencies)) {
      resultDependencies[key] = resolvePackageVersion(
        pkgsMeta,
        key,
        value,
        catalog,
      );
    }
    for (const [key, value] of Object.entries(devDependencies)) {
      resultDevDependencies[key] = resolvePackageVersion(
        pkgsMeta,
        key,
        value,
        catalog,
      );
    }
  }
  return {
    dependencies: resultDependencies,
    devDependencies: resultDevDependencies,
  };
}

/**
 * 向构建产物注入项目元信息（版本/作者/依赖/构建时间等）。
 *
 * 在 config 阶段将元信息写入 `__YDSZ_ADMIN_METADATA__` 全局变量与
 * `import.meta.env.VITE_APP_VERSION`，便于运行时展示与诊断；
 * 依赖版本经 {@link resolveMonorepoDependencies} 解析。
 *
 * @param root - 项目根目录，默认 process.cwd()
 * @returns Vite 插件对象
 */
async function viteMetadataPlugin(
  root = process.cwd(),
): Promise<PluginOption | undefined> {
  const { author, description, homepage, license, version } =
    await readPackageJSON(root);

  const buildTime = dateUtil().format('YYYY-MM-DD HH:mm:ss');

  return {
    async config() {
      const { dependencies, devDependencies } =
        await resolveMonorepoDependencies();

      const isAuthorObject = typeof author === 'object';
      const authorName = isAuthorObject ? author.name : author;
      const authorEmail = isAuthorObject ? author.email : null;
      const authorUrl = isAuthorObject ? author.url : null;

      return {
        define: {
          __YDSZ_ADMIN_METADATA__: JSON.stringify({
            authorEmail,
            authorName,
            authorUrl,
            buildTime,
            dependencies,
            description,
            devDependencies,
            homepage,
            license,
            version,
          }),
          'import.meta.env.VITE_APP_VERSION': JSON.stringify(version),
        },
      };
    },
    enforce: 'post',
    name: 'vite:inject-metadata',
  };
}

export { viteMetadataPlugin };
