#!/usr/bin/env node
/**
 * nginx 子应用配置生成器 — MICRO_APPS → nginx.conf 生成闭环。
 *
 * 从 conf/vite-config/src/micro-apps.config.ts 读取微应用注册表，
 * 自动生成子应用 location 片段并写入 bash/deploy/nginx-sub-apps.conf。
 * nginx.conf 通过 include 指令引用该片段，确保注册表与部署配置始终一致。
 *
 * 用法：
 *   node ./bash/gen-nginx-conf.mjs           # 写入片段文件
 *   node ./bash/gen-nginx-conf.mjs --check   # 仅校验，不写入（CI 用）
 *
 * @path bash/gen-nginx-conf.mjs
 * @author ydsz-team
 * @since 1.0.0
 */

import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { resolve, dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const ROOT_DIR = resolve(__dirname, '..');
const CONFIG_FILE = join(ROOT_DIR, 'conf/vite-config/src/micro-apps.config.ts');
const OUTPUT_FILE = join(ROOT_DIR, 'bash/deploy/nginx-sub-apps.conf');

const isCheckMode = process.argv.includes('--check');

/**
 * 从 micro-apps.config.ts 提取子应用注册信息。
 *
 * 该文件是 TypeScript，但仅导入类型（vue-router 的 RouteRecordRaw），
 * 运行时数据为纯静态。此处用正则提取 name/prodPath 字段，
 * 避免引入 jiti/tsx 等额外依赖。
 *
 * @returns {{ name: string, prodPath?: string }[]}
 */
function parseMicroApps() {
  if (!existsSync(CONFIG_FILE)) {
    console.error(`❌ 配置文件不存在: ${CONFIG_FILE}`);
    process.exit(1);
  }

  const content = readFileSync(CONFIG_FILE, 'utf-8');

  const arrayStart = content.indexOf('export const MICRO_APPS');
  if (arrayStart === -1) {
    console.error('❌ 未找到 MICRO_APPS 导出');
    process.exit(1);
  }

  const bracketStart = content.indexOf('[', arrayStart);
  const bracketEnd = content.indexOf('];', bracketStart);
  if (bracketStart === -1 || bracketEnd === -1) {
    console.error('❌ MICRO_APPS 数组格式异常');
    process.exit(1);
  }

  const arrayContent = content.slice(bracketStart, bracketEnd);

  const entries = [];
  const blockRegex = /\{[^{}]*\}/g;
  let blockMatch;

  while ((blockMatch = blockRegex.exec(arrayContent)) !== null) {
    const block = blockMatch[0];
    const nameMatch = block.match(/name:\s*['"`]([^'"`]+)['"`]/);
    const prodPathMatch = block.match(/prodPath:\s*['"`]([^'"`]+)['"`]/);

    if (nameMatch) {
      entries.push({
        name: nameMatch[1],
        prodPath: prodPathMatch?.[1],
      });
    }
  }

  if (entries.length === 0) {
    console.error('❌ 未从 MICRO_APPS 提取到任何子应用');
    process.exit(1);
  }

  return entries;
}

/**
 * 获取子应用生产部署路径（与 micro-apps.config.ts 的 getProdEntry 逻辑一致）。
 */
function getProdPath(app) {
  return app.prodPath ?? `/ydsz-${app.name}/`;
}

/**
 * 生成 nginx 子应用 location 片段。
 */
function generateNginxSnippet(apps) {
  const blocks = apps.map((app) => {
    const path = getProdPath(app);
    const trimmed = path.endsWith('/') ? path.slice(0, -1) : path;
    const dirName = trimmed.slice(1);
    return `    location ${path} {
      alias /usr/share/nginx/html/${dirName}/;
      try_files $uri $uri/ ${path}index.html;
      add_header Access-Control-Allow-Origin *;
      add_header Cache-Control "public, max-age=31536000, immutable";
    }`;
  });

  return [
    '# ==================== Sub-App Static Assets ====================',
    '# 此文件由 bash/gen-nginx-conf.mjs 从 MICRO_APPS 自动生成，请勿手动修改。',
    '# 新增子应用后运行: pnpm run gen:nginx',
    '',
    blocks.join('\n\n'),
    '',
  ].join('\n');
}

function main() {
  const apps = parseMicroApps();
  const snippet = generateNginxSnippet(apps);

  if (isCheckMode) {
    if (!existsSync(OUTPUT_FILE)) {
      console.error('❌ nginx-sub-apps.conf 不存在，请先运行 pnpm run gen:nginx');
      process.exit(1);
    }
    const existing = readFileSync(OUTPUT_FILE, 'utf-8');
    if (existing.trim() !== snippet.trim()) {
      console.error('❌ nginx-sub-apps.conf 与 MICRO_APPS 不一致，请运行 pnpm run gen:nginx');
      process.exit(1);
    }
    console.log(`✅ nginx-sub-apps.conf 与 ${apps.length} 个子应用注册一致`);
    return;
  }

  writeFileSync(OUTPUT_FILE, snippet, 'utf-8');
  console.log(`✅ 已生成 ${OUTPUT_FILE}`);
  console.log(`   ${apps.length} 个子应用: ${apps.map((a) => a.name).join(', ')}`);
}

main();
