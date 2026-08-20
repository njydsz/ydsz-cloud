import os, subprocess, sys
base = r"D:\Code\open\ydsz-cloud\ydsz-nextwiki\ydsz-nextwiki-domain\src\main\java\com\njydsz\nextwiki\domain\service"
os.makedirs(base, exist_ok=True)
print(f"OK: {base}")
print("Listing domain/:")
for f in os.listdir(os.path.dirname(base)):
    print(f"  {f}")
