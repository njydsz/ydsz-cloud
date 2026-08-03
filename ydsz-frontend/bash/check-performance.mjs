#!/usr/bin/env node

/**
 * 性能预算检查脚本
 *
 * 运行 Lighthouse CI 并检查性能预算，超标时发送告警
 *
 * @path bash/check-performance.mjs
 * @author ydsz-team
 * @since 1.0.0
 */

import { execSync } from 'child_process';
import { readFileSync, writeFileSync, existsSync } from 'fs';
import { join } from 'path';
import {
  performanceBudgets,
  alertRules,
  checkPerformanceBudget,
  sendAlert,
} from './performance-alerts.config.mjs';

const ROOT_DIR = join(import.meta.dirname, '..');
const REPORTS_DIR = join(ROOT_DIR, '.lighthouseci');

/**
 * 运行 Lighthouse CI 收集性能数据
 */
function collectPerformanceData() {
  console.log('🔍 开始收集性能数据...');

  try {
    // 运行 Lighthouse CI collect
    execSync('pnpm run test:perf:collect', {
      stdio: 'inherit',
      cwd: ROOT_DIR,
    });
    console.log('✅ 性能数据收集完成');
  } catch (error) {
    console.error('❌ 性能数据收集失败:', error.message);
    process.exit(1);
  }
}

/**
 * 分析性能报告
 */
function analyzePerformanceReports() {
  console.log('\n📊 分析性能报告...');

  if (!existsSync(REPORTS_DIR)) {
    console.error('❌ 未找到性能报告目录:', REPORTS_DIR);
    process.exit(1);
  }

  const reportFiles = execSync(`ls -1 ${REPORTS_DIR}/*.json 2>/dev/null || true`, {
    encoding: 'utf-8',
  })
    .trim()
    .split('\n')
    .filter(Boolean);

  if (reportFiles.length === 0) {
    console.error('❌ 未找到性能报告文件');
    process.exit(1);
  }

  const violations = [];
  const summaries = [];

  for (const reportFile of reportFiles) {
    try {
      const report = JSON.parse(readFileSync(reportFile, 'utf-8'));
      const { lhr } = report;

      if (!lhr) continue;

      const url = lhr.finalUrl || lhr.requestedUrl;
      const summary = {
        url,
        metrics: {},
        violations: [],
      };

      // 检查 Core Web Vitals
      const audits = lhr.audits;

      // LCP
      const lcp = audits['largest-contentful-paint'];
      if (lcp) {
        summary.metrics.LCP = lcp.numericValue;
        const violation = checkPerformanceBudget('LCP', lcp.numericValue);
        if (violation) {
          summary.violations.push({
            ...violation,
            actualValue: lcp.numericValue,
          });
          violations.push({
            url,
            ...violation,
            actualValue: lcp.numericValue,
          });
        }
      }

      // FID
      const fid = audits['max-potential-fid'];
      if (fid) {
        summary.metrics.FID = fid.numericValue;
        const violation = checkPerformanceBudget('FID', fid.numericValue);
        if (violation) {
          summary.violations.push({
            ...violation,
            actualValue: fid.numericValue,
          });
          violations.push({
            url,
            ...violation,
            actualValue: fid.numericValue,
          });
        }
      }

      // CLS
      const cls = audits['cumulative-layout-shift'];
      if (cls) {
        summary.metrics.CLS = cls.numericValue;
        const violation = checkPerformanceBudget('CLS', cls.numericValue);
        if (violation) {
          summary.violations.push({
            ...violation,
            actualValue: cls.numericValue,
          });
          violations.push({
            url,
            ...violation,
            actualValue: cls.numericValue,
          });
        }
      }

      // 资源大小检查
      const resourceSummary = audits['resource-summary'];
      if (resourceSummary && resourceSummary.details && resourceSummary.details.items) {
        const items = resourceSummary.details.items;

        // JavaScript 大小
        const jsResource = items.find((item) => item.resourceType === 'Script');
        if (jsResource) {
          summary.metrics['script:size'] = jsResource.size;
          const violation = checkPerformanceBudget(
            'script:size',
            jsResource.size,
          );
          if (violation) {
            summary.violations.push({
              ...violation,
              actualValue: jsResource.size,
            });
            violations.push({
              url,
              ...violation,
              actualValue: jsResource.size,
            });
          }
        }

        // CSS 大小
        const cssResource = items.find((item) => item.resourceType === 'Stylesheet');
        if (cssResource) {
          summary.metrics['stylesheet:size'] = cssResource.size;
          const violation = checkPerformanceBudget(
            'stylesheet:size',
            cssResource.size,
          );
          if (violation) {
            summary.violations.push({
              ...violation,
              actualValue: cssResource.size,
            });
            violations.push({
              url,
              ...violation,
              actualValue: cssResource.size,
            });
          }
        }

        // 图片大小
        const imgResource = items.find((item) => item.resourceType === 'Image');
        if (imgResource) {
          summary.metrics['image:size'] = imgResource.size;
          const violation = checkPerformanceBudget('image:size', imgResource.size);
          if (violation) {
            summary.violations.push({
              ...violation,
              actualValue: imgResource.size,
            });
            violations.push({
              url,
              ...violation,
              actualValue: imgResource.size,
            });
          }
        }
      }

      // DOM 节点数
      const domSize = audits['dom-size'];
      if (domSize) {
        summary.metrics['dom-nodes'] = domSize.numericValue;
        const violation = checkPerformanceBudget(
          'dom-nodes',
          domSize.numericValue,
        );
        if (violation) {
          summary.violations.push({
            ...violation,
            actualValue: domSize.numericValue,
          });
          violations.push({
            url,
            ...violation,
            actualValue: domSize.numericValue,
          });
        }
      }

      summaries.push(summary);
    } catch (error) {
      console.warn(`⚠️  分析报告失败: ${reportFile}`, error.message);
    }
  }

  return { summaries, violations };
}

/**
 * 生成性能报告
 */
function generatePerformanceReport(summaries, violations) {
  console.log('\n📋 生成性能报告...\n');

  const report = {
    timestamp: new Date().toISOString(),
    summaries,
    violations,
    budget: performanceBudgets,
  };

  // 输出到控制台
  console.log('='.repeat(80));
  console.log('性能预算检查报告');
  console.log('='.repeat(80));
  console.log(`检查时间: ${report.timestamp}`);
  console.log(`检查页面数: ${summaries.length}`);
  console.log(`违规数量: ${violations.length}`);
  console.log('='.repeat(80));

  if (violations.length === 0) {
    console.log('\n✅ 所有页面均符合性能预算要求！\n');
  } else {
    console.log('\n❌ 发现性能预算违规:\n');

    for (const violation of violations) {
      console.log(`🔴 ${violation.name}`);
      console.log(`   页面: ${violation.url}`);
      console.log(`   级别: ${violation.level}`);
      console.log(`   实际值: ${violation.actualValue}`);
      console.log(`   阈值: ${violation.threshold}`);
      console.log(`   说明: ${violation.message}`);
      console.log('');
    }
  }

  // 保存报告到文件
  const reportFile = join(ROOT_DIR, 'performance-report.json');
  writeFileSync(reportFile, JSON.stringify(report, null, 2));
  console.log(`📄 详细报告已保存至: ${reportFile}\n`);

  return report;
}

/**
 * 发送告警通知
 */
async function sendAlerts(violations) {
  if (violations.length === 0) return;

  console.log('📢 发送告警通知...\n');

  for (const violation of violations) {
    await sendAlert(violation, violation.actualValue);
  }

  console.log('✅ 告警通知已发送\n');
}

/**
 * 主函数
 */
async function main() {
  const startTime = Date.now();

  console.log('🚀 开始性能预算检查...\n');

  try {
    // 1. 收集性能数据
    collectPerformanceData();

    // 2. 分析性能报告
    const { summaries, violations } = analyzePerformanceReports();

    // 3. 生成性能报告
    const report = generatePerformanceReport(summaries, violations);

    // 4. 发送告警通知
    await sendAlerts(violations);

    const duration = ((Date.now() - startTime) / 1000).toFixed(2);
    console.log(`⏱️  总耗时: ${duration}s\n`);

    // 如果有严重违规，退出码为 1
    const hasCriticalViolations = violations.some(
      (v) => v.level === 'critical' || v.level === 'error',
    );

    if (hasCriticalViolations) {
      console.log('❌ 存在严重性能违规，构建失败\n');
      process.exit(1);
    } else if (violations.length > 0) {
      console.log('⚠️  存在性能警告，请优化\n');
      process.exit(0);
    } else {
      console.log('✅ 性能检查通过\n');
      process.exit(0);
    }
  } catch (error) {
    console.error('❌ 性能检查失败:', error);
    process.exit(1);
  }
}

// 执行主函数
main();
