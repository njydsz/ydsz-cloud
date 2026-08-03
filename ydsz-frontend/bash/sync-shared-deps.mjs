/**
 * 共享依赖自托管同步脚本。
 *
 * 配合 importmap 插件的 selfHostBase 模式，将 ESM 依赖产物从公网 CDN
 * 预下载到本地 public/vendor/，并生成完整的 importmap.json（含全部传递依赖），
 * 消除运行时公网 CDN SPOF。
 *
 * 原理：
 *   1. 使用 @jspm/generator 解析 ALL_SHARED_DEPS 的完整依赖图
 *   2. 下载 importmap 中引用的所有 URL 到 vendor/ 下（保留 host/path 结构）
 *   3. 将 importmap 中的 URL 改写为同源 /vendor/ 路径
 *   4. 输出 public/vendor/importmap.json 供构建期读取
 *
 * 使用方式：
 *   pnpm sync:shared-deps                          # 默认下载到 main/public/vendor
 *   node bash/sync-shared-deps.mjs apps/agent-web  # 指定目标应用目录
 *
 * @path bash/sync-shared-deps.mjs
 * @author ydsz-team
 * @since 3.1.0
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { Generator } from '@jspm/generator';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, '..');

// 与 micro-shared-deps.ts 保持一致；TS 文件无法直接 import，此处内联镜像
const ALL_SHARED_DEPS = [
  { name: 'vue', range: '^3.5.17' },
  { name: 'vue-router', range: '^4.5.1' },
  { name: 'pinia', range: '^3.0.3' },
  { name: 'element-plus', range: '^2.10.2' },
  { name: '@element-plus/icons-vue', range: '^2.3.2' },
  { name: 'vxe-table', range: '^4.14.4' },
  { name: 'vxe-pc-ui', range: '^4.7.12' },
  { name: 'axios', range: '^1.10.0' },
  { name: 'echarts', range: '^5.6.0' },
  { name: 'dayjs', range: '^1.11.13' },
  { name: 'vue-demi', range: '^0.14.10' },
];

const VENDOR_DIR_NAME = 'vendor';

// ==================== 主流程 ====================

async function main() {
  const targetAppDir = process.argv[2] || 'main';
  const vendorBase = `/${VENDOR_DIR_NAME}`;
  const publicDir = path.resolve(root, targetAppDir, 'public');
  const vendorDir = path.join(publicDir, VENDOR_DIR_NAME);

  console.info(`[sync-shared-deps] 目标应用: ${targetAppDir}`);
  console.info(`[sync-shared-deps] 输出目录: ${vendorDir}`);

  // 1. 清理旧产物
  if (fs.existsSync(vendorDir)) {
    fs.rmSync(vendorDir, { recursive: true, force: true });
    console.info('[sync-shared-deps] 已清理旧产物');
  }
  fs.mkdirSync(vendorDir, { recursive: true });

  // 2. jspm generator 解析完整依赖图
  console.info('[sync-shared-deps] 通过 jspm generator 解析依赖图...');
  const generator = new Generator({
    baseUrl: root,
    env: ['production', 'browser', 'module'],
    defaultProvider: 'esm.sh',
  });

  for (const dep of ALL_SHARED_DEPS) {
    await generator.install({ target: dep.name, range: dep.range });
    console.info(`  ✓ ${dep.name}@${dep.range}`);
  }

  const originalMap = generator.getMap();
  if (!originalMap) {
    throw new Error('[sync-shared-deps] importmap 生成失败');
  }

  // 3. 收集所有需下载的 URL
  const urlSet = new Set();
  for (const url of Object.values(originalMap.imports || {})) urlSet.add(url);
  for (const scope of Object.values(originalMap.scopes || {})) {
    for (const url of Object.values(scope)) urlSet.add(url);
  }

  console.info(`[sync-shared-deps] 待下载模块: ${urlSet.size} 个`);

  // 4. 下载并改写 URL → 同源路径
  const urlRemap = new Map(); // originalUrl → localPath（以 /vendor 开头）
  let downloaded = 0;
  let failed = 0;

  for (const url of urlSet) {
    try {
      const localPath = await downloadToVendor(url, vendorDir, vendorBase);
      urlRemap.set(url, localPath);
      downloaded++;
      if (downloaded % 10 === 0) {
        console.info(`  进度: ${downloaded}/${urlSet.size}`);
      }
    } catch (err) {
      failed++;
      console.warn(`  ✗ 下载失败: ${url} → ${err.message}`);
      // 失败时保留原始 URL（回退到 CDN）
      urlRemap.set(url, url);
    }
  }

  console.info(`[sync-shared-deps] 下载完成: ${downloaded} 成功, ${failed} 失败`);

  // 5. 改写 importmap
  const remapped = {
    imports: remapKeys(originalMap.imports || {}, urlRemap),
    scopes: remapScopes(originalMap.scopes || {}, urlRemap),
  };

  // 6. 写入 importmap.json
  const importmapPath = path.join(vendorDir, 'importmap.json');
  fs.writeFileSync(importmapPath, JSON.stringify(remapped, null, 2), 'utf-8');
  console.info(`[sync-shared-deps] importmap 已写入: ${importmapPath}`);
  console.info('[sync-shared-deps] 完成。构建时设置 VITE_IMPORTMAP_SELF_HOST=/vendor 即可启用。');
}

/**
 * 下载单个 URL 到 vendor 目录，返回同源路径。
 * URL 结构保留 host + pathname 作为目录层级，避免文件名冲突。
 */
async function downloadToVendor(url, vendorDir, vendorBase) {
  const parsed = new URL(url);
  // host/pathname 作为子路径，去除协议
  const relPath = path.posix.join(parsed.host, parsed.pathname);
  // 以 .js 结尾直接作为文件；否则视为目录，追加 index.js
  const isFile = /\.(js|mjs)$/.test(relPath);
  const filePath = isFile
    ? path.join(vendorDir, relPath)
    : path.join(vendorDir, relPath, 'index.js');
  const localUrlPath = isFile
    ? `${vendorBase}/${relPath}`
    : `${vendorBase}/${relPath}/index.js`;

  fs.mkdirSync(path.dirname(filePath), { recursive: true });

  const res = await fetch(url, { redirect: 'follow' });
  if (!res.ok) {
    throw new Error(`HTTP ${res.status}`);
  }
  const body = await res.text();
  fs.writeFileSync(filePath, body, 'utf-8');
  return localUrlPath;
}

/** 改写 importmap.imports 的值 */
function remapKeys(imports, urlRemap) {
  const result = {};
  for (const [key, url] of Object.entries(imports)) {
    result[key] = urlRemap.get(url) || url;
  }
  return result;
}

/** 改写 importmap.scopes 的值 */
function remapScopes(scopes, urlRemap) {
  const result = {};
  for (const [scope, mapping] of Object.entries(scopes)) {
    result[urlRemap.get(scope) || scope] = remapKeys(mapping, urlRemap);
  }
  return result;
}

main().catch((err) => {
  console.error('[sync-shared-deps] 致命错误:', err);
  process.exit(1);
});
