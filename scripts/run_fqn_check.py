import subprocess
import os

script = r'd:\Code\ydsz\ydsz-pmis\deploy\scripts\check-inline-fqn.sh'
target = r'd:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-common\ydsz-pmis-common-domain\src\main\java'
git_bash = r'C:\Program Files\Git\bin\bash.exe'
if not os.path.exists(git_bash):
    git_bash = r'C:\Program Files\Git\usr\bin\bash.exe'
r = subprocess.run([git_bash, script, target], capture_output=True, text=True)
print(r.stdout[:3000])
if r.stderr:
    print('STDERR:', r.stderr[:1000])
print('exit:', r.returncode)
