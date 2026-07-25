"""
第二轮清理：删除 ratelimit 子目录根目录的旧文件，修复 aop 子目录的类名引用。
"""
import pathlib
import re

ROOT = pathlib.Path(
    "d:/Code/ydsz/ydsz-pmis/ydsz-backend/ydsz-common/ydsz-common-safe/src/main/java/com/njydsz/common/safe"
)

# 1. 删除 ratelimit 子目录根目录的旧文件
ratelimit_root = ROOT / "ratelimit"
old_files = [
    "LocalRateLimiter.java",
    "MultiDimensionRateLimiter.java",
    "RateLimitAspect.java",
    "RateLimitFilter.java",
    "RateLimitProperties.java",
]
for f in old_files:
    fp = ratelimit_root / f
    if fp.exists():
        fp.unlink()
        print(f"[OK] 删除旧文件: {fp.name}")
    else:
        print(f"[SKIP] 不存在: {f}")

# 2. 修复 aop/RateLimitAspect.java 内部对 RateLimit 旧类名的引用
# 旧类名 RateLimit -> SentinelRateLimit（仅在 ratelimit 包内）
# 排除 javadoc 中的 {@link RateLimit} 这种引用，改成 {@link SentinelRateLimit}
aop_file = ratelimit_root / "aop" / "RateLimitAspect.java"
if aop_file.exists():
    text = aop_file.read_text(encoding="utf-8")
    original = text
    # 修正类型引用: RateLimit 改为 SentinelRateLimit（仅在 ratelimit 包作用域下）
    text = re.sub(r"@annotation\(([^)]*?)\.RateLimit\)", r"@annotation(\1.SentinelRateLimit)", text)
    # 修正类名引用: RateLimit annotation -> SentinelRateLimit annotation
    text = re.sub(r"\bRateLimit\s+annotation\b", "SentinelRateLimit annotation", text)
    text = re.sub(r"\bRateLimit\s+rateLimit\b", "SentinelRateLimit rateLimit", text)
    # 修正方法参数类型 RateLimit
    text = re.sub(r"\bRateLimit\s+(\w+)\s*\)", r"SentinelRateLimit \1)", text)
    # 修正 javadoc {@link RateLimit} -> {@link SentinelRateLimit}
    text = re.sub(r"\{@link\s+RateLimit\}", "{@link SentinelRateLimit}", text)
    # 修正 javadoc @see RateLimit -> @see SentinelRateLimit
    text = re.sub(r"@see\s+RateLimit\b", "@see SentinelRateLimit", text)
    # 修正 type reference: RateLimit.class -> SentinelRateLimit.class
    text = re.sub(r"RateLimit\.class", "SentinelRateLimit.class", text)
    # 修正其他 RateLimit 标识符（带 sentinel 限定）
    text = re.sub(r"\bRateLimit\b(?!\.\w)", "SentinelRateLimit", text)
    if text != original:
        aop_file.write_text(text, encoding="utf-8")
        print(f"[FIX] {aop_file.name}")
    else:
        print(f"[NO-CHANGE] {aop_file.name}")

# 3. 修复 annotation/SentinelRateLimit.java 中 javadoc 的 @RateLimit 旧引用
anno_file = ratelimit_root / "annotation" / "SentinelRateLimit.java"
if anno_file.exists():
    text = anno_file.read_text(encoding="utf-8")
    original = text
    # 修正 javadoc 注释中 @RateLimit(...) -> @SentinelRateLimit(...)
    text = re.sub(r"\*\s*@RateLimit\(", "* @SentinelRateLimit(", text)
    if text != original:
        anno_file.write_text(text, encoding="utf-8")
        print(f"[FIX] {anno_file.name}")
    else:
        print(f"[NO-CHANGE] {anno_file.name}")

# 4. 删除脚本
print("\n[DONE] 第二轮清理完成")
