/**
 * run 模块
 *
 * @path bash\turbo-run\src\run.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import { execaCommand, getPackages } from '@ydsz/node-utils';

import { cancel, isCancel, select } from '@clack/prompts';

interface RunOptions {
  /** 待执行的 npm script 名称，如 `dev` / `build`；缺省时直接报错退出 */
  command?: string;
}

/**
 * 交互式选择 Monorepo 中的子包并执行其指定 npm script。
 *
 * @remarks
 * 用于 `pnpm dev` / `pnpm build` 这类需要先选应用再跑命令的场景，
 * 免去手写 `--filter` 包名。
 *
 * 行为约定：
 * - 只列出 `package.json` 中真正声明了该 script 的子包，避免选中后才失败；
 * - 命中唯一子包时跳过交互直接执行，保证 CI 等非交互环境可用；
 * - 命令缺失或无匹配子包时以退出码 1 结束，用户主动取消则以退出码 0 结束，
 *   便于上层脚本区分「失败」与「主动放弃」；
 * - 最终以 `stdio: 'inherit'` 透传子进程输出，保留 dev server 的彩色日志与交互能力。
 *
 * @param options - 运行参数，见 {@link RunOptions}
 * @returns 无返回值；正常路径下进程会一直挂在子命令上，异常路径直接终止进程
 */
export async function run(options: RunOptions) {
  const { command } = options;
  if (!command) {
    console.error('Please enter the command to run');
    process.exit(1);
  }
  const { packages } = await getPackages();
  // const appPkgs = await findApps(process.cwd(), packages);
  // const websitePkg = packages.find(
  //   (item) => item.packageJson.name === '@ydsz/website',
  // );

  // 只显示有对应命令的包
  const selectPkgs = packages.filter((pkg) => {
    return (pkg?.packageJson as Record<string, any>)?.scripts?.[command];
  });

  let selectPkg: string | symbol;
  if (selectPkgs.length > 1) {
    selectPkg = await select<string>({
      message: `Select the app you need to run [${command}]:`,
      options: selectPkgs.map((item) => ({
        label: item?.packageJson.name,
        value: item?.packageJson.name,
      })),
    });

    if (isCancel(selectPkg) || !selectPkg) {
      cancel('👋 Has cancelled');
      process.exit(0);
    }
  } else {
    selectPkg = selectPkgs[0]?.packageJson?.name ?? '';
  }

  if (!selectPkg) {
    console.error('No app found');
    process.exit(1);
  }

  execaCommand(`pnpm --filter=${selectPkg} run ${command}`, {
    stdio: 'inherit',
  });
}

/**
 * 过滤app包
 * @param root
 * @param packages
 */
// async function findApps(root: string, packages: Package[]) {
//   // apps内的
//   const appPackages = packages.filter((pkg) => {
//     const viteConfigExists = fs.existsSync(join(pkg.dir, 'vite.config.mts'));
//     return pkg.dir.startsWith(join(root, 'apps')) && viteConfigExists;
//   });

//   return appPackages;
// }
