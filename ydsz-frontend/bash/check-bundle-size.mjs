/**
 * bundle-size 体积监控脚本
 *
 * @path bash/check-bundle-size.mjs
 * @author ydsz-team
 * @since 1.1.0
 *
 * @remarks
 * 统计各子应用 dist 产物体积，与历史基线对比，超限则报错。
 * 无需额外依赖（纯 Node fs 实现），对标 size-limit 的 CI 体积门槛能力。
 *
 * 使用方式:
 *   node bash/check-bundle-size.mjs           # 检查全部
 *   node bash/check-bundle-size.mjs system    # 仅检查 system-web
 *   node bash/check-bundle-size.mjs --baseline  # 生成新基线
 *
 * 阈值配置见下方 APP_LIMITS（gzip 后 KB）。
 * CI 集成: 在构建后执行本脚本，超限会以非零退出码阻塞合并。
 */

import { existsSync, readFileSync, readdirSync, statSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { gzipSync } from 'node:zlib';

const __dirname = dirname(fileURLToPath(import.meta.url));
const ROOT = join(__dirname, '..');
const BASELINE_FILE = join(ROOT, '.bundle-size-baseline.json');

/**
 * 各子应用体积上限（gzip KB）。
 * main 最大（含全部共享依赖），业务子应用按复杂度分级。
 */
const APP_LIMITS = {
  main: 900,
  'userinfo-web': 500,
  'system-web': 500,
  'project-web': 700,
  'message-web': 400,
  'cronjob-web': 400,
  'workflow-web': 500,
  'nextwiki-web': 400,
  'literule-web': 450,
  'agent-web': 450,
};

/** 递归统计目录内所有文件大小（gzip 后） */
function collectFiles(dir, files = []) {
  if (!existsSync(dir)) return files;
  for (const name of readdirSync(dir)) {
    const full = join(dir, name);
    const stat = statSync(full);
    if (stat.isDirectory()) {
      collectFiles(full, files);
    } else if (name.endsWith('.js') || name.endsWith('.css') || name.endsWith('.html')) {
      files.push(full);
    }
  }
  return files;
}

/** 统计单个应用产物体积（KB，gzip） */
function measureApp(appName) {
  const distDir = join(ROOT, appName === 'main' ? 'main/dist' : `apps/${appName}/dist`);
  if (!existsSync(distDir)) return null;

  const files = collectFiles(distDir);
  let totalBytes = 0;
  const largest = [];

  for (const file of files) {
    const content = readFileSync(file);
    const gzipSize = gzipSync(content).length;
    totalBytes += gzipSize;
    largest.push({ name: file.replace(distDir, ''), size: gzipSize });
  }

  largest.sort((a, b) => b.size - a.size);
  return {
    app: appName,
    totalKb: Math.round(totalBytes / 1024),
    fileCount: files.length,
    largest: largest.slice(0, 5).map((f) => ({
      name: f.name,
      kb: Math.round(f.size / 1024),
    })),
  };
}

function main() {
  const args = process.argv.slice(2);
  const target = args.find((a) => !a.startsWith('--'));
  const isBaseline = args.includes('--baseline');

  const apps = target ? [target] : Object.keys(APP_LIMITS);
  const results = [];
  let hasError = false;

  console.log('[bundle-size] 子应用体积检查（gzip）\n');

  for (const app of apps) {
    const result = measureApp(app);
    if (!result) {
      console.log(`  ${app}: dist 不存在，跳过（请先构建）`);
      continue;
    }
    results.push(result);
    const limit = APP_LIMITS[app];
    const status = limit && result.totalKb > limit ? 'FAIL' : 'OK';
    if (status === 'FAIL') hasError = true;
    console.log(
      `  ${status.padEnd(4)} ${app.padEnd(14)} ${String(result.totalKb).padStart(6)} KB / ${limit ?? '-'} KB (${result.fileCount} files)`,
    );
  }

  // 基线模式：写入当前体积
  if (isBaseline) {
    const baseline = Object.fromEntries(results.map((r) => [r.app, r.totalKb]));
    const existing = existsSync(BASELINE_FILE) ? JSON.parse(readFileSync(BASELINE_FILE, 'utf-8')) : {};
    const merged = { ...existing, ...baseline, updatedAt: new Date().toISOString() };
    writeFileSyncSafe(BASELINE_FILE, JSON.stringify(merged, null, 2));
    console.log('\n[baseline] 已更新 .bundle-size-baseline.json');
  }

  console.log('\n' + (hasError ? '[FAIL] 存在超限应用，请检查新增依赖或分包策略' : '[PASS] 全部应用体积达标'));
  process.exit(hasError ? 1 : 0);
}

function writeFileSyncSafe(file, content) {
  writeFileSync(file, content);
}

main();
