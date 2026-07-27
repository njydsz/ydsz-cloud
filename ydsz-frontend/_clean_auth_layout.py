#!/usr/bin/env python3
"""Clean up auth.vue + AuthPageLayout from all 9 sub-apps (no longer needed)."""
import os

BASE = r"d:\Code\ydsz\ydsz-pmis\ydsz-frontend\apps"
APPS = ["userinfo-web", "system-web", "project-web", "message-web",
        "cronjob-web", "workflow-web", "nextwiki-web", "literule-web", "agent-web"]

LAYOUTS_INDEX = """\
const BasicLayout = () => import('./basic.vue');

const IFrameView = () => import('@ydsz/layouts').then((m) => m.IFrameView);

export { BasicLayout, IFrameView };
"""

count = 0
for app in APPS:
    src = os.path.join(BASE, app, "src")

    # Update layouts/index.ts
    with open(os.path.join(src, "layouts", "index.ts"), 'w', encoding='utf-8') as f:
        f.write(LAYOUTS_INDEX)
    count += 1

    # Delete auth.vue (no longer referenced)
    auth_vue = os.path.join(src, "layouts", "auth.vue")
    if os.path.isfile(auth_vue):
        os.remove(auth_vue)
        count += 1

    print(f"  {app}: layouts/index.ts updated + auth.vue deleted")

print(f"\nTotal: {count} files processed across {len(APPS)} sub-apps")
