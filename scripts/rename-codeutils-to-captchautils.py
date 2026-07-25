"""重命名 CodeUtils → CaptchaUtils 并迁移到 captcha 子包。

变更：
1. 将 code/CodeUtils.java 移到 captcha/CaptchaUtils.java
2. 修改 package 声明：com.njydsz.common.util.code → com.njydsz.common.util.captcha
3. 修改类名：CodeUtils → CaptchaUtils
4. 修改内部所有自引用（"CodeUtils is a utility class" 等字符串）
5. 更新 README

不动 CaptchaResult 内部类名（它已经是正确命名）。
"""
from __future__ import annotations

import pathlib
import re

UTIL_ROOT = pathlib.Path(r"d:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-common\ydsz-common-util\src\main\java\com\njydsz\common\util")
SRC_FILE = UTIL_ROOT / "code" / "CodeUtils.java"
DST_DIR = UTIL_ROOT / "captcha"
DST_FILE = DST_DIR / "CaptchaUtils.java"


def main() -> None:
    if not SRC_FILE.exists():
        raise SystemExit(f"源文件不存在: {SRC_FILE}")

    content = SRC_FILE.read_text(encoding="utf-8")

    # 1. 修改 package 声明
    new_content = content.replace(
        "package com.njydsz.common.util.code;",
        "package com.njydsz.common.util.captcha;",
    )

    # 2. 修改类名 CodeUtils → CaptchaUtils
    new_content = new_content.replace("CodeUtils", "CaptchaUtils")

    # 3. 创建目标目录并写入新文件
    DST_DIR.mkdir(parents=True, exist_ok=True)
    DST_FILE.write_text(new_content, encoding="utf-8")

    # 4. 删除旧文件
    SRC_FILE.unlink()
    print(f"[OK] 重命名: {SRC_FILE.name} -> {DST_FILE.name}")
    print(f"[OK] 迁移: code/ -> captcha/")
    print(f"[OK] package: com.njydsz.common.util.code -> com.njydsz.common.util.captcha")


if __name__ == "__main__":
    main()
