/**
 * index 配置模块
 *
 * @path conf\vite-config\src\config\index.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { DefineConfig } from '../typing';

import { existsSync } from 'node:fs';
import { join } from 'node:path';

import { defineApplicationConfig } from './application';
import { defineLibraryConfig } from './library';

export * from './application';
export * from './library';

/**
 * 统一的 Vite 配置入口，按项目类型分发到应用/库配置。
 *
 * 当 type 为 'auto' 时，依据当前目录是否存在 index.html 自动判定
 * （存在则视为应用，否则视为库），从而无需各子项目手动声明类型。
 *
 * @param userConfigPromise - 用户自定义配置函数，返回应用/库配置片段
 * @param type - 项目类型：application / library / auto（默认自动探测）
 * @returns 适配后的 Vite 配置
 * @throws 项目类型不支持时抛出错误
 */
function defineConfig(
  userConfigPromise?: DefineConfig,
  type: 'application' | 'auto' | 'library' = 'auto',
) {
  let projectType = type;

  // 根据包是否存在 index.html,自动判断类型
  if (projectType === 'auto') {
    const htmlPath = join(process.cwd(), 'index.html');
    projectType = existsSync(htmlPath) ? 'application' : 'library';
  }

  switch (projectType) {
    case 'application': {
      return defineApplicationConfig(userConfigPromise);
    }
    case 'library': {
      return defineLibraryConfig(userConfigPromise);
    }
    default: {
      throw new Error(`Unsupported project type: ${projectType}`);
    }
  }
}

export { defineConfig };
