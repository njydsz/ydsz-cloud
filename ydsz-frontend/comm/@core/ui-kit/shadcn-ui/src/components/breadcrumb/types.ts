/**
 * types 模块
 *
 * @path comm\@core\ui-kit\shadcn-ui\src\components\breadcrumb\types.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { Component } from 'vue';

import type { BreadcrumbStyleType } from '@ydsz-core/typings';

export interface IBreadcrumb {
  icon?: Component | string;
  isHome?: boolean;
  items?: IBreadcrumb[];
  path?: string;
  title?: string;
}

export interface BreadcrumbProps {
  breadcrumbs: IBreadcrumb[];
  showIcon?: boolean;
  styleType?: BreadcrumbStyleType;
}
