/**
 * 轻量语义化版本比较工具（~110 行，零依赖）。
 * 支持范围语法：`>=1.2.0`、`^1.2.0`、`~1.2.0`、`1.2.0 - 2.0.0`、`*`
 *
 * @path comm/effects/micro-runtime/src/semver.ts
 * @since 4.0.0
 */
export interface SemVer { major: number; minor: number; patch: number; prerelease?: string }

export function parseVersion(v: string): SemVer | null {
  const m = v.match(/^(\d+)\.(\d+)\.(\d+)(?:-([a-zA-Z0-9.-]+))?$/);
  if (!m) return null;
  return { major: +m[1], minor: +m[2], patch: +m[3], prerelease: m[4] };
}

export function compareVersion(a: SemVer, b: SemVer): number {
  if (a.major !== b.major) return a.major - b.major;
  if (a.minor !== b.minor) return a.minor - b.minor;
  if (a.patch !== b.patch) return a.patch - b.patch;
  if (a.prerelease && !b.prerelease) return -1;
  if (!a.prerelease && b.prerelease) return 1;
  if (a.prerelease && b.prerelease) return a.prerelease.localeCompare(b.prerelease);
  return 0;
}

export function satisfiesVersion(version: string, range: string): boolean {
  const v = parseVersion(version);
  if (!v) return false;
  const r = range.trim();
  if (r === '*' || r === 'x' || r === '') return true;
  const rangeMatch = r.match(/^(\d+\.\d+\.\d+)\s*-\s*(\d+\.\d+\.\d+)$/);
  if (rangeMatch) {
    const lo = parseVersion(rangeMatch[1]); const hi = parseVersion(rangeMatch[2]);
    if (!lo || !hi) return false;
    return compareVersion(v, lo) >= 0 && compareVersion(v, hi) <= 0;
  }
  if (r.startsWith('^')) {
    const base = parseVersion(r.slice(1));
    if (!base) return false;
    return v.major === base.major && compareVersion(v, base) >= 0;
  }
  if (r.startsWith('~')) {
    const base = parseVersion(r.slice(1));
    if (!base) return false;
    return v.major === base.major && v.minor === base.minor && v.patch >= base.patch;
  }
  const opMatch = r.match(/^(>=|>|<=|<|=)?\s*(\d+\.\d+\.\d+.*)$/);
  if (opMatch) {
    const [, op = '=', ver] = opMatch;
    const target = parseVersion(ver);
    if (!target) return false;
    const cmp = compareVersion(v, target);
    switch (op) {
      case '>=': return cmp >= 0;
      case '>': return cmp > 0;
      case '<=': return cmp <= 0;
      case '<': return cmp < 0;
      default: return cmp === 0;
    }
  }
  return false;
}
