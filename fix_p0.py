import os
from pathlib import Path
BASE = Path(r"d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-common\ydsz-pmis-common-excel\src\main\java\com\njydsz\pmis\common\excel")
def rf(p):
    with open(p,"r",encoding="utf-8") as f: return f.read()
def wf(p,c):
    with open(p,"w",encoding="utf-8") as f: f.write(c)
def rep(p,old,new):
    c=rf(p)
    if old not in c:
        print("  WARN:",p.name)
        return False
    wf(p,c.replace(old,new,1))
    return True
