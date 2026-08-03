/**
 * 性能预算告警配置
 *
 * 定义 Web Vitals 指标阈值和告警规则
 *
 * @path bash/performance-alerts.config.mjs
 * @author ydsz-team
 * @since 1.0.0
 */

export const performanceBudgets = {
  // Core Web Vitals 阈值（Google 标准）
  webVitals: {
    LCP: { good: 2500, needsImprovement: 4000, unit: 'ms' },
    FID: { good: 100, needsImprovement: 300, unit: 'ms' },
    CLS: { good: 0.1, needsImprovement: 0.25, unit: '' },
    INP: { good: 200, needsImprovement: 500, unit: 'ms' },
    FCP: { good: 1800, needsImprovement: 3000, unit: 'ms' },
    TTFB: { good: 800, needsImprovement: 1800, unit: 'ms' },
  },

  // 资源大小预算
  resourceBudgets: {
    // 单个 JavaScript 文件最大 500KB
    'script:size': 512 * 1024,
    // 单个 CSS 文件最大 100KB
    'stylesheet:size': 100 * 1024,
    // 单个图片最大 1MB
    'image:size': 1024 * 1024,
    // 第三方资源最大 256KB
    'third-party:size': 256 * 1024,
    // 总页面大小最大 3MB
    'total:size': 3 * 1024 * 1024,
  },

  // 数量预算
  countBudgets: {
    // 最多 50 个 JavaScript 文件
    'script:count': 50,
    // 最多 10 个 CSS 文件
    'stylesheet:count': 10,
    // 最多 100 个图片
    'image:count': 100,
    // DOM 节点最多 1500 个
    'dom-nodes': 1500,
  },
};

// 告警级别
export const alertLevels = {
  WARNING: 'warning',
  ERROR: 'error',
  CRITICAL: 'critical',
};

// 告警规则
export const alertRules = [
  {
    name: 'LCP 超标',
    metric: 'LCP',
    threshold: performanceBudgets.webVitals.LCP.needsImprovement,
    level: alertLevels.WARNING,
    message: '最大内容渲染时间超过 4 秒，可能影响用户体验',
  },
  {
    name: 'CLS 超标',
    metric: 'CLS',
    threshold: performanceBudgets.webVitals.CLS.needsImprovement,
    level: alertLevels.WARNING,
    message: '累积布局偏移超过 0.25，页面可能存在布局抖动',
  },
  {
    name: 'JavaScript 体积过大',
    metric: 'script:size',
    threshold: performanceBudgets.resourceBudgets['script:size'],
    level: alertLevels.ERROR,
    message: 'JavaScript 文件体积超过 500KB，建议进行代码分割',
  },
  {
    name: 'DOM 节点过多',
    metric: 'dom-nodes',
    threshold: performanceBudgets.countBudgets['dom-nodes'],
    level: alertLevels.WARNING,
    message: 'DOM 节点超过 1500 个，可能影响渲染性能',
  },
];

// 告警通知配置
export const notificationConfig = {
  // 是否启用控制台输出
  console: true,
  // 是否启用钉钉通知
  dingtalk: false,
  // 是否启用企业微信通知
  wechat: false,
  // 是否启用邮件通知
  email: false,
  // 钉钉 Webhook URL（需要时配置）
  dingtalkWebhook: '',
  // 企业微信 Webhook URL（需要时配置）
  wechatWebhook: '',
  // 邮件接收人（需要时配置）
  emailRecipients: [],
};

/**
 * 检查性能指标是否超标
 * @param {string} metric - 指标名称
 * @param {number} value - 指标值
 * @returns {Object|null} - 告警规则或 null
 */
export function checkPerformanceBudget(metric, value) {
  for (const rule of alertRules) {
    if (rule.metric === metric && value > rule.threshold) {
      return rule;
    }
  }
  return null;
}

/**
 * 发送告警通知
 * @param {Object} rule - 告警规则
 * @param {number} actualValue - 实际值
 */
export async function sendAlert(rule, actualValue) {
  const message = `[性能告警] ${rule.name}: 实际值 ${actualValue}，阈值 ${rule.threshold}。${rule.message}`;

  // 控制台输出
  if (notificationConfig.console) {
    console.warn(message);
  }

  // 钉钉通知
  if (notificationConfig.dingtalk && notificationConfig.dingtalkWebhook) {
    try {
      await fetch(notificationConfig.dingtalkWebhook, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          msgtype: 'text',
          text: { content: message },
        }),
      });
    } catch (error) {
      console.error('钉钉通知发送失败:', error);
    }
  }

  // 企业微信通知
  if (notificationConfig.wechat && notificationConfig.wechatWebhook) {
    try {
      await fetch(notificationConfig.wechatWebhook, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          msgtype: 'text',
          text: { content: message },
        }),
      });
    } catch (error) {
      console.error('企业微信通知发送失败:', error);
    }
  }
}
