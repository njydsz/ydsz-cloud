import os
root = r'd:\Code\ydsz\ydsz-pmis'
exts = {'.java','.xml','.yml','.yaml','.properties','.md','.sql','.vue','.ts','.js','.json','.txt','.conf'}
skip = {'node_modules','.git','target','dist','build','.idea','.vscode','.run-logs','.keypoint'}
bad = []
for dp, dns, fns in os.walk(root):
    parts = dp.replace(chr(92), '/').split('/')
    if any(s in parts for s in skip):
        continue
    for fn in fns:
        ext = os.path.splitext(fn)[1].lower()
        if ext not in exts:
            continue
        fp = os.path.join(dp, fn)
        try:
            with open(fp, 'rb') as f:
                raw = f.read()
            try:
                raw.decode('utf-8')
            except:
                bad.append(fp)
        except:
             pass
with open(os.path.join(root, 'nonUTF8_files.txt'), 'w', encoding='utf-8') as out:
    for fp in bad:
        out.write(fp + chr(10))
print('Total non-UTF-8 files:', len(bad))
for fp in bad[:30]:
    print(fp)