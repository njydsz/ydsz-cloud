import os, re

ROOT = r"D:\Code\open\ydsz-cloud"
MAIN_JAVA = "/src/main/java/"

OFF_RE = re.compile(r"CHECKSTYLE\.OFF:\s*(\S+)")
ON_RE  = re.compile(r"CHECKSTYLE\.ON:\s*(\S+)")
STAR_RE = re.compile(r"^\s*import\s+[\w.]+\.\*;")
RAW_RE  = re.compile(r"new\s+(ThreadPoolExecutor|ThreadPoolTaskExecutor|ScheduledThreadPoolExecutor)\s*\(")

star_files = []
illegal_files = []
raw_files = []
exec_new = 0
long_lines = 0
long_files = set()
over2000 = []

def scan(p):
    global exec_new, long_lines
    active = set()
    with open(p, encoding="utf-8", errors="replace") as f:
        for lineno, line in enumerate(f, 1):
            raw = line.rstrip("\n")
            m = OFF_RE.search(raw)
            if m: active.add(m.group(1))
            m = ON_RE.search(raw)
            if m: active.discard(m.group(1))

            if STAR_RE.match(raw) and "AvoidStarImport" not in active:
                star_files.append((p, lineno))
            if "import java.util.concurrent.Executors;" in raw and "IllegalImport" not in active:
                illegal_files.append((p, lineno))
            if RAW_RE.search(raw) and "RegexpSinglelineJava" not in active:
                raw_files.append((p, lineno))
            if re.search(r"\bExecutors\.\w+\(", raw):
                exec_new += 1
            if len(raw) > 120:
                s = raw.lstrip()
                if not (s.startswith("import ") or s.startswith("package ")):
                    if "://" not in raw and "www." not in raw:
                        long_lines += 1
                        long_files.add(p)

for dirpath, dirs, files in os.walk(ROOT):
    dp = dirpath.replace("\\", "/")
    if MAIN_JAVA not in dp + "/":
        continue
    for fn in files:
        if fn.endswith(".java"):
            p = os.path.join(dirpath, fn)
            scan(p)
            with open(p, encoding="utf-8", errors="replace") as f:
                n = sum(1 for _ in f)
            if n > 2000:
                over2000.append((p, n))

def rel(p):
    return p.replace(ROOT, "").replace("\\", "/")

print("=== SUPPRESSION-AWARE AUDIT ===")
print("STAR_IMPORT:", len(star_files))
for p, l in star_files: print("   ", rel(p), l)
print("ILLEGAL_EXECUTORS_IMPORT:", len(illegal_files))
for p, l in illegal_files: print("   ", rel(p), l)
print("RAW_POOL (new ThreadPool*):", len(raw_files))
for p, l in raw_files: print("   ", rel(p), l)
print("EXEC_NEW_CALLS (info):", exec_new)
print("LONG_LINES >120:", long_lines, "in", len(long_files), "files")
print("OVER_2000_LINES:", len(over2000))
for p, n in over2000: print("   ", rel(p), n)
