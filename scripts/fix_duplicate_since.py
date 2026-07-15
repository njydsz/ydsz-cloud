import re, os

base = r'd:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-common\ydsz-pmis-common-redis\src\main\java'

def parse_ver(s):
    return tuple(int(x) for x in s.split('.'))

def fix_file(fp):
    with open(fp, 'r', encoding='utf-8') as f:
        lines = f.readlines()

    since_info = []
    for i, line in enumerate(lines):
        m = re.search(r'@since (\d+\.\d+\.\d+)', line)
        if m:
            since_info.append((i, m.group(1)))

    if len(since_info) <= 1:
        return False

    groups = []
    cur = [since_info[0]]
    for i in range(1, len(since_info)):
        prev_idx = since_info[i-1][0]
        curr_idx = since_info[i][0]
        is_consec = True
        for j in range(prev_idx + 1, curr_idx):
            s = lines[j].strip()
            if s and s != '*' and s != '*/':
                is_consec = False
                break
        if is_consec:
            cur.append(since_info[i])
        else:
            groups.append(cur)
            cur = [since_info[i]]
    groups.append(cur)

    to_remove = set()
    for g in groups:
        if len(g) <= 1:
            continue
        max_v = max(g, key=lambda x: parse_ver(x[1]))
        for item in g:
            if item != max_v:
                to_remove.add(item[0])
        for i in range(len(g) - 1):
            for j in range(g[i][0] + 1, g[i+1][0]):
                s = lines[j].strip()
                if s == '*' or s == '':
                    to_remove.add(j)

    if not to_remove:
        return False

    new_lines = [l for i, l in enumerate(lines) if i not in to_remove]
    with open(fp, 'w', encoding='utf-8') as f:
        f.writelines(new_lines)
    return True

count = 0
for root, dirs, files in os.walk(base):
    for f in files:
        if f.endswith('.java'):
            fp = os.path.join(root, f)
            if fix_file(fp):
                count += 1
                print(f'Fixed: {os.path.relpath(fp, base)}')
print(f'Total: {count} files fixed')
