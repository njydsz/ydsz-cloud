import pathlib,sys,re
BASE=pathlib.Path(r"d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-common\ydsz-pmis-common-docs\src\main\java\com\njydsz\pmis\common\docs")
def wf(r,c):
    f=BASE/r;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(c,encoding="utf-8");print("OK:",r)
def rf(r):
    return (BASE/r).read_text(encoding="utf-8")
print("apply engine ready")

# P3-5: DocsProperties
wf('config/DocsProperties.java', pathlib.Path(str(BASE)+'/'+'config/DocsProperties.java').read_text(encoding='utf-8'))
