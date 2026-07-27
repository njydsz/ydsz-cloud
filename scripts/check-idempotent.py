import pathlib, re

BACKEND = pathlib.Path("d:/Code/ydsz/ydsz-pmis/ydsz-backend")
MODULES = {"ydsz-userinfo","ydsz-workflow","ydsz-nextwiki","ydsz-message","ydsz-cronjob","ydsz-agent","ydsz-literule","ydsz-system","ydsz-project"}

issues = []

for md in sorted(BACKEND.iterdir()):
    if not md.is_dir() or md.name not in MODULES:
        continue
    web = md / f"{md.name}-web"
    if not web.exists():
        continue
    for f in sorted(web.rglob("**/controller/**/*Controller.java")):
        content = f.read_text(encoding="utf-8")
        rel = f.relative_to(BACKEND)

        # Check 1: @Idempotent inside method params
        if re.search(r"\(@Idempotent", content):
            issues.append(f"  BAD_PARAM: {rel}")

        # Check 2: Duplicate @SentinelRateLimit
        sl_matches = list(re.finditer(r"@SentinelRateLimit\(([^)]+)\)", content))
        sl_keys = [m.group(1) for m in sl_matches]
        if len(sl_keys) != len(set(sl_keys)):
            issues.append(f"  DUP_SENTINEL: {rel}")

        # Check 3: Duplicate @Idempotent
        id_matches = list(re.finditer(r"@Idempotent\(([^)]+)\)", content))
        id_keys = [m.group(1) for m in id_matches]
        if len(id_keys) != len(set(id_keys)):
            issues.append(f"  DUP_IDEMPOTENT: {rel}")

        # Check 4: Bad key format
        for m in id_matches:
            key_match = re.search(r'key\s*=\s*"([^"]+)"', m.group(1))
            if key_match:
                key = key_match.group(1)
                if not key.startswith("ydsz:"):
                    issues.append(f"  BAD_KEY_FORMAT: {rel} -> {key}")

if issues:
    print("ISSUES FOUND:")
    for i in issues:
        print(i)
else:
    print("ALL CHECKS PASSED")