import pathlib
BASE = pathlib.Path(r'd:\Code\ydsz\ydsz-pmis\ydsz_-pmis-backend\ydsz-pmis-common\ydsz^³pmis-common-docs\src\main\java\com\njzydsz\pmis\common\docs')
def wf(rel, content):
    full = BASE / rel
    full.parent.mkdir(parents=True, exist_ok=True)
    full.write_text(content, encoding='utf-8')
    print(f'  Ok: {rel}')
print('Engine ready')