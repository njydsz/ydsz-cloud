/**
 * cn 工具函数模块
 *
 * @path comm\@core\base\shared\src\utils\cn.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { ClassValue } from 'clsx';

import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

export { cn };
