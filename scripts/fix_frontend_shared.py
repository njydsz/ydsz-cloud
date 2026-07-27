#!/usr/bin/env python3
"""P1-3: 前端公共包迁移 — 将 9 个子应用的重复文件替换为从 @ydsz/shared-auth 导入"""

import os

APPS_DIR = r"d:\Code\ydsz\ydsz-pmis\ydsz-frontend\apps"

# Files to replace with re-exports
REPLACEMENTS = {
    "src/api/request.ts": """/**
 * RequestClient — re-export from @ydsz/shared-auth
 */
export {
  baseRequestClient,
  initSharedRequest,
  requestClient,
} from '@ydsz/shared-auth';
""",
    "src/api/core/auth.ts": """/**
 * Auth API — re-export from @ydsz/shared-auth
 */
export {
  loginApi,
  logoutApi,
  refreshTokenApi,
  getAccessCodesApi,
} from '@ydsz/shared-auth';
""",
    "src/api/core/user.ts": """/**
 * User API — re-export from @ydsz/shared-auth
 */
export {
  getUserInfoApi,
} from '@ydsz/shared-auth';
""",
    "src/api/core/menu.ts": """/**
 * Menu API — re-export from @ydsz/shared-auth
 */
export {
  getAllMenusApi,
  getMenuTreeApi,
} from '@ydsz/shared-auth';
""",
    "src/api/core/index.ts": """/**
 * Core API — re-export from @ydsz/shared-auth
 */
export * from './auth';
export * from './user';
export * from './menu';
""",
}

# List of sub-apps
APPS = [
    "agent-web", "cronjob-web", "literule-web", "message-web",
    "nextwiki-web", "project-web", "system-web", "userinfo-web", "workflow-web",
]


def main():
    modified = 0
    for app in APPS:
        app_dir = os.path.join(APPS_DIR, app)
        if not os.path.isdir(app_dir):
            print(f"SKIP (not found): {app}")
            continue
        for rel_path, content in REPLACEMENTS.items():
            file_path = os.path.join(app_dir, rel_path)
            file_dir = os.path.dirname(file_path)
            if not os.path.isdir(file_dir):
                os.makedirs(file_dir, exist_ok=True)
            with open(file_path, 'w', encoding='utf-8') as f:
                f.write(content)
            modified += 1
            print(f"REPLACED: {app}/{rel_path}")

    print(f"\nTotal replaced: {modified} files")


if __name__ == '__main__':
    main()
