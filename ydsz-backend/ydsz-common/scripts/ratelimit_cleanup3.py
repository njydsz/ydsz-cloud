"""
第三轮清理：修复 aop/RateLimitAspect.java 中的行内 FQN 和 javadoc 错误。
"""
import pathlib
import re

ROOT = pathlib.Path(
    "d:/Code/ydsz/yydsz-pmis/ydsz-backend/ydsz-common/ydsz-common-safe/src/main/java/com/njydsz/common/safe"
)
ROOT = pathlib.Path(
    "d:/Code/ydsz/ydsz-pmis/ydsz-backend/ydsz-common/ydsz-common-safe/src/main/java/com/njydsz/common/safe"
)

# 1. 修复 aop/RateLimitAspect.java
aop_file = ROOT / "ratelimit" / "aop" / "RateLimitAspect.java"
if aop_file.exists():
    text = aop_file.read_text(encoding="utf-8")
    new_lines = []
    imports_added = set()

    # 删除重复的 BusinessException import
    seen_business = False
    for line in text.split("\n"):
        if line.strip() == "import com.njydsz.common.exception.custom.BusinessException;":
            if seen_business:
                continue
            seen_business = True
        new_lines.append(line)

    text = "\n".join(new_lines)

    # 在 import 区域添加 jakarta.servlet 和 spring web request 相关 import
    # 找最后一个 import 行的位置
    import_pattern = re.compile(r"^(import .+;)$", re.MULTILINE)
    imports = import_pattern.findall(text)
    if imports:
        last_import = imports[-1]
        new_imports = [
            "import jakarta.servlet.http.HttpServletRequest;",
            "import org.springframework.web.context.request.RequestAttributes;",
            "import org.springframework.web.context.request.RequestContextHolder;",
            "import org.springframework.web.context.request.ServletRequestAttributes;",
        ]
        for ni in new_imports:
            if ni not in text:
                # 找到 last_import 位置，在其后插入
                idx = text.rfind(last_import)
                if idx >= 0:
                    end = idx + len(last_import)
                    text = text[:end] + "\n" + ni + text[end:]

    # 修复 javadoc 错误引用 RateLimitException -> 删除或改为 BusinessException
    text = re.sub(
        r"失败时抛出 \{@link RateLimitException\}",
        "失败时抛出 {@link com.njydsz.common.exception.custom.BusinessException}",
        text
    )

    # 修复行内 FQN: jakarta.servlet.http.HttpServletRequest -> HttpServletRequest
    text = re.sub(
        r"jakarta\.servlet\.http\.HttpServletRequest",
        "HttpServletRequest",
        text
    )

    # 修复行内 FQN: org.springframework.web.context.request.RequestAttributes
    text = re.sub(
        r"org\.springframework\.web\.context\.request\.RequestAttributes",
        "RequestAttributes",
        text
    )
    text = re.sub(
        r"org\.springframework\.web\.context\.request\.RequestContextHolder",
        "RequestContextHolder",
        text
    )
    text = re.sub(
        r"org\.springframework\.web\.context\.request\.ServletRequestAttributes",
        "ServletRequestAttributes",
        text
    )

    aop_file.write_text(text, encoding="utf-8")
    print(f"[FIX] {aop_file.name}")

# 2. 扫描 ratelimit 子目录所有文件的行内 FQN
ratelimit_dir = ROOT / "ratelimit"
fixed_files = []
for f in ratelimit_dir.rglob("*.java"):
    text = f.read_text(encoding="utf-8")
    original = text
    # 修复 org.springframework.web.context.request.* 行内 FQN
    text = re.sub(
        r"org\.springframework\.web\.context\.request\.RequestAttributes",
        "RequestAttributes",
        text
    )
    text = re.sub(
        r"org\.springframework\.web\.context\.request\.RequestContextHolder",
        "RequestContextHolder",
        text
    )
    text = re.sub(
        r"org\.springframework\.web\.context\.request\.ServletRequestAttributes",
        "ServletRequestAttributes",
        text
    )
    # 修复 jakarta.servlet.http.HttpServletRequest 行内 FQN
    text = re.sub(
        r"jakarta\.servlet\.http\.HttpServletRequest",
        "HttpServletRequest",
        text
    )
    # 修复 jakarta.servlet.http.HttpServletResponse
    text = re.sub(
        r"jakarta\.servlet\.http\.HttpServletResponse",
        "HttpServletResponse",
        text
    )
    # 修复 jakarta.servlet.Filter
    text = re.sub(
        r"jakarta\.servlet\.Filter",
        "Filter",
        text
    )
    # 修复 jakarta.servlet.FilterChain
    text = re.sub(
        r"jakarta\.servlet\.FilterChain",
        "FilterChain",
        text
    )
    if text != original:
        f.write_text(text, encoding="utf-8")
        fixed_files.append(str(f))
        print(f"[FIX-FQN] {f.name}")

# 3. 检查并添加缺失的 import
if aop_file.exists():
    text = aop_file.read_text(encoding="utf-8")
    needed = []
    if "HttpServletRequest" in text and "import jakarta.servlet.http.HttpServletRequest;" not in text:
        needed.append("import jakarta.servlet.http.HttpServletRequest;")
    if "RequestContextHolder" in text and "import org.springframework.web.context.request.RequestContextHolder;" not in text:
        needed.append("import org.springframework.web.context.request.RequestContextHolder;")
    if "RequestAttributes" in text and "import org.springframework.web.context.request.RequestAttributes;" not in text:
        needed.append("import org.springframework.web.context.request.RequestAttributes;")
    if "ServletRequestAttributes" in text and "import org.springframework.web.context.request.ServletRequestAttributes;" not in text:
        needed.append("import org.springframework.web.context.request.ServletRequestAttributes;")
    if needed:
        # 在最后一个 import 行后插入
        import_pattern = re.compile(r"^(import .+;)$", re.MULTILINE)
        imports = import_pattern.findall(text)
        if imports:
            last_import = imports[-1]
            idx = text.rfind(last_import)
            end = idx + len(last_import)
            text = text[:end] + "\n" + "\n".join(needed) + text[end:]
            aop_file.write_text(text, encoding="utf-8")
            print(f"[ADD-IMPORT] {aop_file.name}: {len(needed)} 个")

print(f"\n[INFO] 修复文件数: {len(fixed_files)}")
print("[DONE] 第三轮清理完成")
