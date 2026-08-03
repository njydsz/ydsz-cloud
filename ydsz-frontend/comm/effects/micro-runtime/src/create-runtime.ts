/**
 * 运行时工厂 + 内核注册机制
 *
 * 内核实现（qiankun / micro-kernel）通过 registerKernel 注册，
 * createRuntime 按 name 选择内核并返回 MicroRuntime 实例。
 *
 * 主应用 bootstrap.ts 中调用 createRuntime({ kernel: 'qiankun' })
 * 即可替换现有 registerMicroApps/start 体系，此后切换内核只需改一个字面量。
 *
 * @path comm/effects/micro-runtime/src/create-runtime.ts
 * @author ydsz-team
 * @since 3.0.0
 */

import type { MicroRuntime } from './types';

/** 已知内核名称 */
export type KernelName = 'micro-kernel' | 'qiankun' | string;

/** 内核工厂注册表 */
const kernelRegistry = new Map<KernelName, () => MicroRuntime>();

/**
 * 注册内核实现
 *
 * @example
 * registerKernel('qiankun', () => createQiankunAdapter());
 * registerKernel('micro-kernel',   () => createKernel());
 */
export function registerKernel(name: KernelName, factory: () => MicroRuntime): void {
  if (kernelRegistry.has(name)) {
    console.warn(`[MicroRuntime] Kernel "${name}" is already registered, overwriting.`);
  }
  kernelRegistry.set(name, factory);
}

/**
 * 创建运行时实例（单例）
 *
 * @example
 * const runtime = createRuntime({ kernel: 'qiankun' });
 * runtime.registerApps(microApps);
 * runtime.start({ sandbox: { styleIsolation: true }, prefetch: false });
 */
export function createRuntime(options: { kernel: KernelName }): MicroRuntime {
  const factory = kernelRegistry.get(options.kernel);
  if (!factory) {
    throw new Error(
      `[MicroRuntime] Kernel "${options.kernel}" is not registered. ` +
      `Available: ${[...kernelRegistry.keys()].join(', ')}`,
    );
  }
  return factory();
}

/** 获取已注册的内核列表（调试用） */
export function getRegisteredKernels(): KernelName[] {
  return [...kernelRegistry.keys()];
}
