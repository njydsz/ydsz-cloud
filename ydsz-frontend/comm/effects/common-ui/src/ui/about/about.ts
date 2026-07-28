/**
 * about 模块
 *
 * @path comm\effects\common-ui\src\ui\about\about.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { Component } from 'vue';

interface AboutProps {
  description?: string;
  name?: string;
  title?: string;
}

interface DescriptionItem {
  content: Component | string;
  title: string;
}

export type { AboutProps, DescriptionItem };
