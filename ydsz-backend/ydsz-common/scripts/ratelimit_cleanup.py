"""
清理 ydsz-common-ratelimit 残留，整合到 ydsz-common-safe。

1. 删除 ydsz-common-ratelimit 整个目录
2. 删除 ydsz-common-safe 根目录的 5 个旧 ratelimit 文件
3. 重命名 annotation/RateLimit.java 为 SentinelRateLimit.java
4. 批量修正 ratelimit 包的引用路径
5. 验证所有 ratelimit 类的 import 路径正确
"""
import os
import re
import shutil
import pathlib

ROOT = pathlib.Path("d:/Code/ydsz/ydsz-pmis/ydsz-backend/ydsz-common")

# 1. 删除 ydsz-common-ratelimit 整个目录
ratelimit_module = ROOT / "ydsz-common-ratelimit"
if ratelimit_module.exists():
    shutil.rmtree(ratelimit_module)
    print(f"[OK] 删除模块: {ratelimit_module}")
else:
    print(f"[SKIP] 模块不存在: {ratelimit_module}")

# 2. 删除 ydsz-common-safe 根目录的旧 ratelimit 文件
safe_root = ROOT / "ydsz-common-safe" / "src" / "main" / "java" / "com" / "njydsz" / "common" / "safe"
old_files = [
    "LocalRateLimiter.java",
    "MultiDimensionRateLimiter.java",
    "RateLimitAspect.java",
    "RateLimitFilter.java",
    "RateLimitProperties.java",
]
for f in old_files:
    fp = safe_root / f
    if fp.exists():
        fp.unlink()
        print(f"[OK] 删除旧文件: {fp}")
    else:
        print(f"[SKIP] 不存在: {fp}")

# 3. 重命名 annotation/RateLimit.java 为 SentinelRateLimit.java
old_anno = safe_root / "ratelimit" / "annotation" / "RateLimit.java"
new_anno = safe_root / "ratelimit" / "annotation" / "SentinelRateLimit.java"
if old_anno.exists():
    old_anno.rename(new_anno)
    print(f"[OK] 重命名: {old_anno.name} -> {new_anno.name}")
else:
    print(f"[SKIP] 旧 annotation 不存在: {old_anno}")

# 4. 批量修正 ratelimit 包的引用路径
# 所有引用 com.njydsz.common.ratelimit.* 应改为 com.njydsz.common.safe.ratelimit.*
# 但 com.njydsz.common.ratelimit.exceptions 等不存在的子包不要盲目替换（先看 enum/model/core 等已知子包）
ratelimit_subpackages = [
    "annotation", "aop", "algorithm", "circuitbreaker", "cluster", "config",
    "core", "enums", "metrics", "model", "properties", "provider", "spi", "spring"
]
ratelimit_dir = safe_root / "ratelimit"
fixed_count = 0
for f in ratelimit_dir.rglob("*.java"):
    text = f.read_text(encoding="utf-8")
    original = text
    # 修正 import 语句: com.njydsz.common.ratelimit. -> com.njydsz.common.safe.ratelimit.
    text = re.sub(
        r"import\s+com\.njydsz\.common\.ratelimit\.",
        "import com.njydsz.common.safe.ratelimit.",
        text
    )
    # 修正行内 FQN: com.njydsz.common.ratelimit. -> com.njydsz.common.safe.ratelimit.
    # 但要避开已经是 safe.ratelimit 的
    text = re.sub(
        r"(?<!safe\.)com\.njydsz\.common\.ratelimit\.",
        "com.njydsz.common.safe.ratelimit.",
        text
    )
    if text != original:
        f.write_text(text, encoding="utf-8")
        fixed_count += 1
        print(f"[FIX] {f.relative_to(safe_root.parent.parent.parent.parent)}")
print(f"[INFO] 修正 {fixed_count} 个文件的 import 路径")

# 5. 修正其他模块对 ratelimit 的引用（如果有）
# 检查 ydsz-common-safe 之外是否有引用 com.njydsz.common.ratelimit.* 的地方
print("\n[CHECK] 扫描其他模块对 com.njydsz.common.ratelimit.* 的引用...")
for java_file in ROOT.rglob("*.java"):
    text = java_file.read_text(encoding="utf-8")
    # 查找错误的引用路径（不包括 ratelimit 子包内部的已修正文件）
    if "com.njydsz.common.ratelimit" in text and "safe.ratelimit" not in text:
        if "ratelimit" in str(java_file).replace("\\", "/").split("/")[-3:]:
            # 这是 ratelimit 子包内部的文件，已经在步骤4处理过
            continue
        # 看是否需要修正
        if re.search(r"com\.njydsz\.common\.ratelimit\.", text) and not re.search(
            r"com\.njydsz\.common\.safe\.ratelimit\.", text
        ):
            new_text = re.sub(
                r"com\.njydsz\.common\.ratelimit\.",
                "com.njydsz.common.safe.ratelimit.",
                text
            )
            java_file.write_text(new_text, encoding="utf-8")
            print(f"[FIX-EXT] {java_file}")

print("\n[DONE] 清理完成")
