# -*- coding: utf-8 -*-
"""批量补充 @Nested 测试内部类的类级注释（第二遍：覆盖无 @DisplayName 的分组）。

对无 @DisplayName 的 @Nested 类：
1. 尝试从类声明向下找第一个 @DisplayName（该分组首个用例的描述）；
2. 否则用类名拆词生成通用描述（如 ClosedStateCases -> 关闭状态相关用例）。
"""
import io
import os
import re
import json

ROOT = r'D:\Code\ydsz\ydsz-pmis'
todo = json.load(open(os.path.join(ROOT, 'docs', 'scripts', 'java_todo.json'), encoding='utf-8'))

targets = []
for rel, v in todo.items():
    if '/test/' not in rel.replace('\\', '/'):
        continue
    if not v['missing_classes']:
        continue
    full = os.path.join(ROOT, rel)
    targets.append((full, v['missing_classes']))

CLASS_RE = re.compile(
    r"(?m)^(?P<indent>\s*)class\s+(?P<name>\w+)\s*\{"
)
DISPLAY_RE = re.compile(r'@DisplayName\("([^"]+)"\)')

def split_words(name):
    """ClosedStateCases -> ['Closed', 'State', 'Cases']"""
    return re.findall(r'[A-Z]?[a-z]+|[A-Z]+(?![a-z])', name)

def describe(name):
    words = split_words(name)
    if words and words[-1].lower() in ('cases', 'test', 'tests'):
        words = words[:-1]
    if not words:
        return name
    cn_map = {
        'Closed': '关闭状态', 'Open': '打开状态', 'Half': '半开', 'State': '状态',
        'Construct': '构造', 'Multi': '多', 'Rule': '规则', 'Update': '更新',
        'Find': '查找', 'Candidate': '候选', 'Inverted': '倒排', 'Index': '索引',
        'Compatibility': '兼容性', 'Shard': '分片', 'Determinism': '确定性',
        'Mine': '归属', 'Check': '检查', 'Static': '静态', 'Weight': '权重',
        'Distribution': '分布', 'Minimal': '最小', 'Migration': '迁移',
        'Virtual': '虚拟', 'Node': '节点', 'Count': '计数', 'Number': '数字',
        'Literal': '字面量', 'String': '字符串', 'Keyword': '关键字',
        'Identifier': '标识符', 'Operator': '运算符', 'Delimiter': '分隔符',
        'Comment': '注释', 'Whitespace': '空白', 'Position': '位置',
        'Tracking': '追踪', 'Complex': '复杂', 'Expression': '表达式',
        'Resolve': '解析', 'Url': 'URL', 'Build': '构建', 'Payload': '载荷',
        'Percent': '百分号', 'Encode': '编码', 'Canonical': '规范', 'Query': '查询',
        'List': '列表查询', 'Remove': '删除', 'ById': '按ID', 'Get': '获取',
        'Error': '错误', 'Code': '码', 'Chain': '链', 'Data': '数据',
        'Message': '消息', 'Resolution': '解析', 'Concurrency': '并发',
        'Incremental': '增量', 'Enable': '启用', 'Rebuild': '重建',
        'Key': '密钥', 'Validation': '校验', 'Encrypt': '加密', 'Decrypt': '解密',
        'Roundtrip': '往返', 'Tamper': '篡改', 'Detection': '检测',
        'Sign': '签名', 'Verify': '验证', 'Signature': '签名', 'Util': '工具',
        'Zero': '零值', 'Null': '空值', 'Normal': '正常', 'Overdue': '逾期',
        'Boundary': '边界', 'Blended': '混合', 'Margin': '利润', 'Achieved': '达成',
        'Evaluate': '评估', 'DryRun': '试运行', 'TopResult': 'Top结果',
        'Stats': '统计', 'MdcTrace': 'MDC链路', 'Register': '注册',
        'Unregister': '注销', 'Parse': '解析', 'Valid': '有效', 'Invalid': '无效',
        'Safe': '安全', 'Constructor': '构造器', 'Sequential': '顺序',
        'Parallel': '并行', 'Failure': '失败', 'Propagation': '传播',
        'Cycle': '循环', 'Append': '追加', 'Transit': '流转', 'Can': '能否',
        'Bytes': '字节', 'ToHex': '转十六进制', 'Hex': '十六进制',
        'Base64': 'Base64', 'Base32': 'Base32', 'Url': 'URL', 'Encoding': '编码',
        'Crc32': 'CRC32', 'MurmurHash32': 'MurmurHash32', 'Base62': 'Base62',
        'Base58': 'Base58', 'ConsistentHash': '一致性哈希', 'WeightAnd': '权重与',
        'UpdateThenInsert': '先更新后插入', 'Idempotent': '幂等',
        'GetById': '按ID查询', 'RemoveById': '按ID删除',
    }
    parts = [cn_map.get(w, w) for w in words]
    return ''.join(parts) + '用例'

def process(full_path, missing_classes):
    with io.open(full_path, 'r', encoding='utf-8', errors='replace') as f:
        src = f.read()
    lines = src.split('\n')
    modified = 0
    for m in CLASS_RE.finditer(src):
        name = m.group('name')
        if name not in missing_classes:
            continue
        line_no = src[: m.start()].count('\n')  # 0-based
        # 向上找 @DisplayName
        display = None
        for k in range(line_no - 1, max(line_no - 5, -1), -1):
            t = lines[k].strip()
            dm = DISPLAY_RE.match(t)
            if dm:
                display = dm.group(1)
                break
            if t.startswith('@'):
                continue
            if not t:
                continue
            break
        # 向下找第一个 @DisplayName（分组内首个用例描述）
        if display is None:
            for k in range(line_no + 1, min(line_no + 60, len(lines))):
                t = lines[k].strip()
                dm = DISPLAY_RE.search(t)
                if dm:
                    display = '「' + dm.group(1) + '」等'
                    break
                if t.startswith('class '):
                    break
        if display is None:
            display = describe(name)
        indent = m.group('indent')
        comment = f"{indent}/**\n{indent} * 测试分组：{display}\n{indent} */\n"
        pos = m.start()
        src = src[:pos] + comment + src[pos:]
        lines = src.split('\n')
        modified += 1
        print(f'  [OK] {os.path.basename(full_path)}::{name} <- {display}')
    if modified:
        with io.open(full_path, 'w', encoding='utf-8', newline='') as f:
            f.write(src)
    return modified

total = 0
for full, missing in targets:
    total += process(full, missing)
print(f'共插入 {total} 处注释')
