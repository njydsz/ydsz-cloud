#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
修复 ydsz-workflow-domain 中错误使用 @JsonSerialize(using = SensitiveDataSerializer.class) 的 DTO。

问题根因：
  SensitiveDataSerializer 实现的是 com.njydsz.common.json.serializer.JsonSerializer（YdszJson 引擎），
  而不是 Jackson 的 com.fasterxml.jackson.databind.JsonSerializer<?>。
  因此 @JsonSerialize(using = SensitiveDataSerializer.class) 注解在编译期类型不匹配，
  且 jackson-databind 依赖未在 ydsz-workflow-domain 模块中声明，导致编译失败。

修复方案：
  1. 移除 @JsonSerialize(using = SensitiveDataSerializer.class) 注解行
  2. 移除 import com.fasterxml.jackson.databind.annotation.JsonSerialize 行
  3. 保留 @SensitiveData(...) 注解（YdszJson 引擎通过全局注册的 SensitiveDataSerializer 自动识别）

遵循 prefer-python-over-powershell.md 规则，使用 Python 处理文件以避免编码损坏。
"""
import pathlib
import re

BASE = pathlib.Path(r"d:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-workflow\ydsz-workflow-domain\src\main\java\com\njydsz\workflow\domain\dto")

FILES = [
    "FlowStartProcessDTO.java",
    "FlowDelegateAuthSaveDTO.java",
    "FlowCommentCreateDTO.java",
    "FlowTaskOperateDTO.java",
    "FlowInstanceViewDTO.java",
    "FlowAssigneeDTO.java",
    "EmbeddedApprovalViewDTO.java",
    "EmbeddedApprovalActionDTO.java",
]

# 需要移除的 import 行
IMPORT_LINE = "import com.fasterxml.jackson.databind.annotation.JsonSerialize;\n"
# 需要移除的注解行（可能带前导空格）
ANNOTATION_RE = re.compile(r'^[ \t]*@JsonSerialize\(using\s*=\s*SensitiveDataSerializer\.class\)[ \t]*\n', re.MULTILINE)

changed = []
for fname in FILES:
    fpath = BASE / fname
    if not fpath.exists():
        print(f"[SKIP] 文件不存在: {fpath}")
        continue
    original = fpath.read_text(encoding="utf-8")
    new = original
    # 1. 移除 import 行
    if IMPORT_LINE in new:
        new = new.replace(IMPORT_LINE, "", 1)
    # 2. 移除 @JsonSerialize 注解行
    new, n = ANNOTATION_RE.subn("", new)
    if new != original:
        fpath.write_text(new, encoding="utf-8")
        changed.append(f"{fname} (removed {n} annotations)")
        print(f"[OK] 已修复: {fname} (移除 {n} 个 @JsonSerialize 注解)")
    else:
        print(f"[NOOP] 无需修改: {fname}")

print(f"\n总计修复 {len(changed)} 个文件")
