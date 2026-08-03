# -*- coding: utf-8 -*-
"""精细调试：打印 strip_block_comments 找到的每个 docs 起始行与内容摘要。"""
import sys
sys.path.insert(0, r'D:\Code\ydsz\ydsz-pmis\docs\scripts')
import scan_comments as sc

p = r'D:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-common\ydsz-common-json\src\main\java\com\njydsz\common\json\reader\JSONReader.java'
with open(p, encoding='utf-8') as f:
    src = f.read()

stripped, docs = sc.strip_block_comments(src)
print(f'docs 总数: {len(docs)}')
for i, (ln, content) in enumerate(docs):
    first = content.split('\n')[0][:50] if content else ''
    print(f'  [{i+1}] 起始行 {ln}: {first}')
