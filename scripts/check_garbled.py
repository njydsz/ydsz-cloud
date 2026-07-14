import os
import re
import sys

root = r'd:/Code/ydsz/ydsz-pmis/ydsz-pmis-backend/ydsz-pmis-common/ydsz-pmis-common-domain/src/main/java'
pattern = re.compile(
    r'\u3002[^\uff0c\u3002\u3001\uff1b\uff1a\s\)\}\n/]'
    r'|\u3002$'
    r'|\uff08\u3002'
    r'|\u4e3a\u3002null'
    r'|\u4e3a\u3002\{'
    r'|\u3002[\u4e0d\u53ef\u4ee3\u4f46\u6765\u9012\u5b9e\u6839\u5168\u6216\u4e24\u503c\u88ab\u5b50\u5df2\u524d\u4e5f\u5185\u6700\u5206\u5c42\u5355\u957f\u65f6\u65b9\u6b64\u56e0\u5373\u6bcf\u4e00\u540c\u6570\u4e8b\u4ee5\u7b49\u5176\u5e76\u6784\u65e5\u6240\u4e14\u4fdd\u5220\u5f53\u67e5\u8bbe\u540d\u5165\u5927\u51fa\u7531\u7c7b\u5b83\u4e09\u591a\u4f7f\u5bf9\u4ece\u901a\u652f\u6709\u53cd\u590d\u5411\u53d7\u5efa\u5c55T\u5982\u65e0\u65b0\u4ec5\u66f4\u5c06\u5305\u53e6]'
)

count = 0
for dp, dn, fn in os.walk(root):
    for f in fn:
        if not f.endswith('.java'):
            continue
        fp = os.path.join(dp, f)
        for ln, line in enumerate(open(fp, encoding='utf-8'), 1):
            if pattern.search(line):
                rel = os.path.relpath(fp, root)
                print(f'{rel}:{ln}: {line.rstrip()[:120]}')
                count += 1
                if count >= 60:
                    sys.exit(0)
print(f'Total found: {count}')
