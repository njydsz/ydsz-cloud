import pathlib
p = pathlib.Path(r'd:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-common\ydsz-common-core\src\main\java\com\njydsz\common\core\metrics\AbstractModuleMetrics.java')
b = p.read_bytes()

# 查找不可见 ASCII 字符
print('Non-printable ASCII chars:')
for i, byte in enumerate(b[:300]):
    if byte < 32 and byte not in (10, 13):
        print(f'pos {i}: 0x{byte:02x}')

# 查找特殊 unicode 字符
print('Special unicode chars:')
text = p.read_text(encoding='utf-8')
for i, ch in enumerate(text[:400]):
    cp = ord(ch)
    if cp > 127:
        # 允许中文 CJK、常见标点
        if not (0x4E00 <= cp <= 0x9FFF or cp in (0xFF0C, 0x3002, 0xFF1A, 0xFF1B, 0xFF01, 0xFF1F, 0xFF08, 0xFF09, 0x3010, 0x3011, 0x300C, 0x300D, 0x201C, 0x201D, 0x2018, 0x2019, 0x300A, 0x300B, 0x3001, 0x2026, 0x2014)):
            print(f'pos {i}: char={ch!r} code=U+{cp:04X}')
