/**
 * 监控模块统一安装入口
 *
 * 在 app.mount() 之前调用 setupMonitor(app, config) 即可同时启用错误监控和 Web Vitals。
 * v3.1: config 支持 release / sampleRate / beforeSend / getUserId，用于全链路追踪与采样。
 * v3.4: 集成面包屑自动采集（click / navigation / console）。
 */
import type { MonitorConfig } from './error-monitor';

import { setupBreadcrumbAutoCapture } from './breadcrumb';
import { setupErrorMonitoring } from './error-monitor';
import { setupWebVitals } from './web-vitals';

/**
 * 安装全部监控能力
 *
 * @param app - Vue 应用实例
 * @param config - 监控配置（release 版本、采样率、脱敏钩子、用户 ID 获取）
 */
export function setupMonitor(app: any, config: MonitorConfig = {}) {
  // 面包屑自动采集需最先安装，确保后续错误上报能携带完整轨迹
  setupBreadcrumbAutoCapture();
  setupErrorMonitoring(app, config);
  setupWebVitals();
}
