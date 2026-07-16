import json,sys
from pathlib import Path
BASE=Path(sys.argv[1])
def rf(p):
 with open(p,"r",encoding="utf-8") as f: return f.read()
def wf(p,c):
 with open(p,"w",encoding="utf-8") as f: f.write(c)
def rep(p,old,new):
 c=rf(p)
 if old not in c:
  print("WARN:",p)
  return False
 wf(p,c.replace(old,new,1))
 return True
rules=json.load(open(sys.argv[2],"r",encoding="utf-8"))
for r in rules:
 fp=BASE/r["file"]
 rep(str(fp),r["old"],r["new"])
 if r.get("all"):
  c2=rf(str(fp))
  if r["old"] in c2:
   wf(str(fp),c2.replace(r["old"],r["new"]))
   print("ALL:",r["desc"])
 print("DONE:",r["desc"])
