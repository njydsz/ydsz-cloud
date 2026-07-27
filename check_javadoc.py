import os, re
from pathlib import Path

base = Path(r'd:\Code\ydsz\ydsz-pmis\ydsz-backend')
total = 0
no_javadoc = 0
minimal_javadoc = 0
good_javadoc = 0

for p in base.rglob('*.java'):
    text = p.read_text(encoding='utf-8')
    total += 1
    has_class_doc = bool(re.search(r'/\*\*[\s\S]*?\*/\s*(?:public|protected|private)', text))
    if not has_class_doc:
        no_javadoc += 1
    else:
        class_doc = re.search(r'/\*\*([\s\S]*?)\*/', text)
        if class_doc:
            doc_body = class_doc.group(1).strip()
            if len(doc_body) < 30:
                minimal_javadoc += 1
            else:
                good_javadoc += 1

print(f'Total: {total}')
print(f'No Javadoc: {no_javadoc} ({no_javadoc*100//total}%)')
print(f'Minimal Javadoc: {minimal_javadoc} ({minimal_javadoc*100//total}%)')
print(f'Good Javadoc: {good_javadoc} ({good_javadoc*100//total}%)')
