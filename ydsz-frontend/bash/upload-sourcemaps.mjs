/**
 * Sourcemap 上传脚本。
 *
 * 构建后运行，将 dist/ 下的 .map 文件上传到后端监控服务做 stack trace 符号化，
 * 上传后从产物删除 .map（避免随静态资源部署泄露源码）。
 *
 * 使用方式：
 *   VITE_APP_RELEASE=v1.0.0-abc123 pnpm upload:sourcemaps
 *   node bash/upload-sourcemaps.mjs --dist=apps/agent-web/dist --release=v1.0.0
 *
 * @path bash/upload-sourcemaps.mjs
 * @author ydsz-team
 * @since 3.1.0
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, '..');

// ==================== 参数解析 ====================
const args = process.argv.slice(2);
function getArg(name, fallback) {
  const found = args.find((a) => a.startsWith(`--${name}=`));
  return found ? found.slice(name.length + 3) : fallback;
}

const distDir = path.resolve(root, getArg('dist', 'main/dist'));
const release =
  getArg('release', '') || process.env.VITE_APP_RELEASE || process.env.VITE_APP_VERSION || '';
const endpoint = getArg('endpoint', '/api/v1/monitor/sourcemaps');
const keep = args.includes('--keep'); // 调试：不删除已上传的 .map

if (!release) {
  console.error('[upload-sourcemaps] 缺少 release 标识（--release 或 VITE_APP_RELEASE）');
  process.exit(1);
}

// ==================== 主流程 ====================

/** 递归收集 .map 文件 */
function collectMapFiles(dir, acc = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      collectMapFiles(full, acc);
    } else if (entry.name.endsWith('.map')) {
      acc.push(full);
    }
  }
  return acc;
}

async function uploadOne(filePath) {
  const relativePath = path.relative(distDir, filePath).replace(/\\/g, '/');
  const body = fs.readFileSync(filePath);
  const url = `${endpoint}?release=${encodeURIComponent(release)}&file=${encodeURIComponent(relativePath)}`;

  try {
    const res = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/octet-stream' },
      body,
    });
    if (!res.ok) {
      throw new Error(`HTTP ${res.status}`);
    }
    return { ok: true, relativePath };
  } catch (err) {
    return { ok: false, relativePath, error: err.message };
  }
}

async function main() {
  if (!fs.existsSync(distDir)) {
    console.error(`[upload-sourcemaps] dist 目录不存在: ${distDir}`);
    process.exit(1);
  }

  const mapFiles = collectMapFiles(distDir);
  console.info(`[upload-sourcemaps] release=${release}`);
  console.info(`[upload-sourcemaps] 发现 ${mapFiles.length} 个 sourcemap 文件`);

  if (mapFiles.length === 0) {
    console.info('[upload-sourcemaps] 无 sourcemap 可上传，退出。');
    return;
  }

  let ok = 0;
  let fail = 0;
  for (const file of mapFiles) {
    const result = await uploadOne(file);
    if (result.ok) {
      ok++;
      if (!keep) fs.unlinkSync(file);
    } else {
      fail++;
      console.warn(`  ✗ ${result.relativePath}: ${result.error}`);
    }
  }

  console.info(`[upload-sourcemaps] 完成: ${ok} 成功, ${fail} 失败${keep ? '（已保留 .map）' : '（已删除 .map）'}`);
  if (fail > 0) process.exit(1);
}

main().catch((err) => {
  console.error('[upload-sourcemaps] 致命错误:', err);
  process.exit(1);
});
