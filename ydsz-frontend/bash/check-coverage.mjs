#!/usr/bin/env node
/**
 * 代码覆盖率检查脚本
 *
 * 运行单元测试并检查覆盖率阈值，生成覆盖率报告
 *
 * @path bash/check-coverage.mjs
 * @author ydsz-team
 * @since 1.0.0
 */

import { execSync } from 'child_process';
import { readFileSync, writeFileSync, existsSync } from 'fs';
import { join } from 'path';

const ROOT_DIR = join(import.meta.dirname, '..');
const COVERAGE_DIR = join(ROOT_DIR, 'coverage');
const COVERAGE_JSON = join(COVERAGE_DIR, 'coverage-summary.json');

// 覆盖率阈值配置（Q1 目标：branches/functions 70%、lines/statements 80%）
// 后续阶段提升路线：Q2 → 80%/85%，Q3 → 85%/90%
const COVERAGE_THRESHOLDS = {
  branches: 70,
  functions: 70,
  lines: 80,
  statements: 80,
};

/**
 * 运行单元测试并生成覆盖率报告
 */
function runCoverage() {
  console.log('🔍 运行单元测试并生成覆盖率报告...');

  try {
    // 运行 vitest 并生成覆盖率报告
    execSync('pnpm run test:unit:coverage', {
      stdio: 'inherit',
      cwd: ROOT_DIR,
    });
    console.log('✅ 覆盖率报告生成成功');
  } catch (error) {
    console.error('❌ 覆盖率报告生成失败:', error.message);
    process.exit(1);
  }
}

/**
 * 解析覆盖率报告
 */
function parseCoverageReport() {
  console.log('\n📊 解析覆盖率报告...');

  if (!existsSync(COVERAGE_JSON)) {
    console.error('❌ 未找到覆盖率报告文件:', COVERAGE_JSON);
    process.exit(1);
  }

  try {
    const coverageData = JSON.parse(readFileSync(COVERAGE_JSON, 'utf-8'));
    const total = coverageData.total;

    if (!total) {
      console.error('❌ 覆盖率报告格式错误：缺少 total 字段');
      process.exit(1);
    }

    const summary = {
      timestamp: new Date().toISOString(),
      thresholds: COVERAGE_THRESHOLDS,
      actual: {
        branches: total.branches.pct,
        functions: total.functions.pct,
        lines: total.lines.pct,
        statements: total.statements.pct,
      },
      violations: [],
      passed: true,
    };

    // 检查是否达到阈值
    for (const [metric, threshold] of Object.entries(COVERAGE_THRESHOLDS)) {
      const actual = summary.actual[metric];
      if (actual < threshold) {
        summary.violations.push({
          metric,
          threshold,
          actual,
          gap: threshold - actual,
        });
        summary.passed = false;
      }
    }

    return summary;
  } catch (error) {
    console.error('❌ 解析覆盖率报告失败:', error.message);
    process.exit(1);
  }
}

/**
 * 生成覆盖率报告
 */
function generateCoverageReport(summary) {
  console.log('\n📋 生成覆盖率报告...\n');

  const report = {
    ...summary,
    details: {
      branches: {
        covered: summary.actual.branches,
        threshold: summary.thresholds.branches,
        status: summary.actual.branches >= summary.thresholds.branches ? '✅ PASS' : '❌ FAIL',
      },
      functions: {
        covered: summary.actual.functions,
        threshold: summary.thresholds.functions,
        status: summary.actual.functions >= summary.thresholds.functions ? '✅ PASS' : '❌ FAIL',
      },
      lines: {
        covered: summary.actual.lines,
        threshold: summary.thresholds.lines,
        status: summary.actual.lines >= summary.thresholds.lines ? '✅ PASS' : '❌ FAIL',
      },
      statements: {
        covered: summary.actual.statements,
        threshold: summary.thresholds.statements,
        status: summary.actual.statements >= summary.thresholds.statements ? '✅ PASS' : '❌ FAIL',
      },
    },
  };

  // 输出到控制台
  console.log('='.repeat(80));
  console.log('代码覆盖率检查报告');
  console.log('='.repeat(80));
  console.log(`检查时间: ${report.timestamp}`);
  console.log('='.repeat(80));
  console.log('\n覆盖率指标:');
  console.log(`  分支覆盖率:   ${report.details.branches.covered.toFixed(2)}% (阈值: ${report.thresholds.branches}%) ${report.details.branches.status}`);
  console.log(`  函数覆盖率:   ${report.details.functions.covered.toFixed(2)}% (阈值: ${report.thresholds.functions}%) ${report.details.functions.status}`);
  console.log(`  行覆盖率:     ${report.details.lines.covered.toFixed(2)}% (阈值: ${report.thresholds.lines}%) ${report.details.lines.status}`);
  console.log(`  语句覆盖率:   ${report.details.statements.covered.toFixed(2)}% (阈值: ${report.thresholds.statements}%) ${report.details.statements.status}`);
  console.log('='.repeat(80));

  if (report.violations.length > 0) {
    console.log('\n❌ 覆盖率未达标:\n');
    for (const violation of report.violations) {
      console.log(`  ${violation.metric}: ${violation.actual.toFixed(2)}% < ${violation.threshold}% (差距: ${violation.gap.toFixed(2)}%)`);
    }
    console.log('\n请增加单元测试以提高覆盖率。\n');
  } else {
    console.log('\n✅ 所有覆盖率指标均达标！\n');
  }

  // 保存报告到文件
  const reportFile = join(ROOT_DIR, 'coverage-report.json');
  writeFileSync(reportFile, JSON.stringify(report, null, 2));
  console.log(`📄 详细报告已保存至: ${reportFile}\n`);

  // 生成 HTML 报告链接
  const htmlReport = join(COVERAGE_DIR, 'index.html');
  if (existsSync(htmlReport)) {
    console.log(`🌐 HTML 覆盖率报告: ${htmlReport}\n`);
  }

  return report;
}

/**
 * 主函数
 */
function main() {
  const startTime = Date.now();

  console.log('🚀 开始代码覆盖率检查...\n');

  try {
    // 1. 运行覆盖率测试
    runCoverage();

    // 2. 解析覆盖率报告
    const summary = parseCoverageReport();

    // 3. 生成覆盖率报告
    const report = generateCoverageReport(summary);

    const duration = ((Date.now() - startTime) / 1000).toFixed(2);
    console.log(`⏱️  总耗时: ${duration}s\n`);

    // 如果有违规，退出码为 1
    if (!report.passed) {
      console.log('❌ 覆盖率检查失败\n');
      process.exit(1);
    } else {
      console.log('✅ 覆盖率检查通过\n');
      process.exit(0);
    }
  } catch (error) {
    console.error('❌ 覆盖率检查失败:', error);
    process.exit(1);
  }
}

// 执行主函数
main();
