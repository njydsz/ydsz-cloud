#!/usr/bin/env python3
"""Add @ydsz/monitor dependency to all 9 sub-app package.json files."""
import os, json

BASE = r"d:\Code\ydsz\ydsz-pmis\ydsz-frontend\apps"
APPS = ["userinfo-web", "system-web", "project-web", "message-web",
        "cronjob-web", "workflow-web", "nextwiki-web", "literule-web", "agent-web"]

count = 0
for app in APPS:
    pkg_path = os.path.join(BASE, app, "package.json")
    with open(pkg_path, 'r', encoding='utf-8') as f:
        pkg = json.load(f)

    deps = pkg.get("dependencies", {})
    if "@ydsz/monitor" not in deps:
        # Insert after @ydsz/shared-auth if it exists, otherwise after first @ydsz/ key
        new_deps = {}
        added = False
        for k, v in deps.items():
            new_deps[k] = v
            if k == "@ydsz/shared-auth" and not added:
                new_deps["@ydsz/monitor"] = "workspace:*"
                added = True
        if not added:
            # Insert before first non-shared-auth @ydsz key
            new_deps = {}
            for k, v in deps.items():
                if not added and k.startswith("@ydsz/"):
                    new_deps["@ydsz/monitor"] = "workspace:*"
                    added = True
                new_deps[k] = v
            if not added:
                new_deps["@ydsz/monitor"] = "workspace:*"
        pkg["dependencies"] = new_deps

        with open(pkg_path, 'w', encoding='utf-8') as f:
            json.dump(pkg, f, indent=2, ensure_ascii=False)
            f.write('\n')
        count += 1
        print(f"  {app}: @ydsz/monitor added")
    else:
        print(f"  {app}: already has @ydsz/monitor")

print(f"\nTotal: {count} packages updated")
