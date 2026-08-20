"""临时脚本：创建 domain/service 目录结构"""
import os
import sys

base = r"D:\Code\open\ydsz-cloud\ydsz-nextwiki\ydsz-nextwiki-domain\src\main\java\com\njydsz\nextwiki\domain\service"
os.makedirs(base, exist_ok=True)
print(f"Created: {base}")

# Verify
parent = os.path.dirname(base)
for item in os.listdir(parent):
    print(f"  - {item}")
