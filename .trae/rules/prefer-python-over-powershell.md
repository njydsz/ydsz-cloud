---
alwaysApply: true
scene: code
---

# 脚本执行优先使用 Python 而非 PowerShell

> **项目工程规范（强制）** — 适用于项目中所有脚本执行场景，不可豁免。

## 规则定义

在 ydsz-pmis 项目中执行脚本命令时（包括但不限于批量文件处理、文本替换、代码生成、数据转换、文件读写等），**必须优先使用 Python**，禁止使用 PowerShell。

## 原因

PowerShell 在处理文件编码时存在严重问题：

1. **编码损坏**：PowerShell 默认使用 UTF-16 LE BOM 或系统 ANSI 编码读写文件，在处理 UTF-8 无 BOM 的源代码文件时，会将文件内容转换为乱码。
2. **BOM 污染**：PowerShell 的 Out-File、Set-Content 等 cmdlet 默认添加 BOM 前缀，导致 Java 编译器、Git diff、Spotless 等工具出现兼容性问题。
3. **转义陷阱**：PowerShell 的引号转义规则与正则表达式交互混乱，容易在文本替换中引入意外修改。
4. **跨平台不一致**：Windows PowerShell 5.x 与 PowerShell 7+ 行为差异大，脚本可移植性差。

Python 的 pathlib、io 模块默认使用 UTF-8 编码，且 encoding=utf-8 参数行为明确、跨平台一致，不会损坏源代码文件。

## 正确做法

```python
# 使用 Python 进行文件读写和文本替换
import pathlib

content = pathlib.Path("src/main/java/.../Example.java").read_text(encoding="utf-8")

new_content = content.replace("oldText", "newText")

pathlib.Path("src/main/java/.../Example.java").write_text(new_content, encoding="utf-8")
```

```python
# 使用 Python 批量处理多个文件
import pathlib

for f in pathlib.Path("ydsz-pmis-backend").rglob("*.java"):
    content = f.read_text(encoding="utf-8")
    if "oldText" in content:
        f.write_text(content.replace("oldText", "newText"), encoding="utf-8")
```

## 错误做法

```powershell
# ❌ PowerShell 会损坏文件编码
Get-ChildItem -Recurse -Filter "*.java" | ForEach-Object {
    (Get-Content $_.FullName) -replace 'oldText', 'newText' | Set-Content $_.FullName
}

# ❌ Out-File 默认添加 BOM
"Some content" | Out-File -FilePath "example.txt"
```

## 执行机制

- **IDE 规则**：Trae / CatPaw 规则文件 alwaysApply: true，AI 代码生成阶段自动遵守。
- **Code Review**：PR 审查中如发现由 PowerShell 脚本引入的编码损坏，即打回。
- **CI 检测**：可在 CI 流水线中添加 BOM 检测脚本，拒绝含 BOM 的源代码文件。
