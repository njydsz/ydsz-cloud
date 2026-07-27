#!/usr/bin/env python3
"""Check if toVO methods are still called anywhere."""
import os
import re

BACKEND = r'd:\Code\ydsz\ydsz-pmis\ydsz-backend'

# The toVO methods found
tovo_locations = [
    ('ydsz-nextwiki/ydsz-nextwiki-server/src/main/java/com/njydsz/nextwiki/server/service/FileApplicationService.java', 'toVO'),
    ('ydsz-system/ydsz-system-server/src/main/java/com/njydsz/system/server/service/impl/AppInfoServiceImpl.java', 'toVO'),
    ('ydsz-system/ydsz-system-server/src/main/java/com/njydsz/system/server/service/impl/ConfigServiceImpl.java', 'toVO'),
    ('ydsz-system/ydsz-system-server/src/main/java/com/njydsz/system/server/service/impl/DictItemServiceImpl.java', 'toVO'),
    ('ydsz-system/ydsz-system-server/src/main/java/com/njydsz/system/server/service/impl/DictServiceImpl.java', 'toVO'),
    ('ydsz-system/ydsz-system-server/src/main/java/com/njydsz/system/server/service/impl/DictVersionServiceImpl.java', 'toVO'),
    ('ydsz-system/ydsz-system-server/src/main/java/com/njydsz/system/server/service/impl/VariableServiceImpl.java', 'toVO'),
    ('ydsz-userinfo/ydsz-userinfo-server/src/main/java/com/njydsz/userinfo/server/service/impl/CompanyServiceImpl.java', 'toVO'),
    ('ydsz-userinfo/ydsz-userinfo-server/src/main/java/com/njydsz/userinfo/server/service/impl/DepartmentServiceImpl.java', 'toVO'),
    ('ydsz-userinfo/ydsz-userinfo-server/src/main/java/com/njydsz/userinfo/server/service/impl/LanguageServiceImpl.java', 'toVO'),
    ('ydsz-userinfo/ydsz-userinfo-server/src/main/java/com/njydsz/userinfo/server/service/impl/MenuServiceImpl.java', 'toVO'),
    ('ydsz-userinfo/ydsz-userinfo-server/src/main/java/com/njydsz/userinfo/server/service/impl/PostServiceImpl.java', 'toVO'),
    ('ydsz-userinfo/ydsz-userinfo-server/src/main/java/com/njydsz/userinfo/server/service/impl/RoleServiceImpl.java', 'toVO'),
    ('ydsz-userinfo/ydsz-userinfo-server/src/main/java/com/njydsz/userinfo/server/service/impl/UserAccountServiceImpl.java', 'toVO'),
]

for rel_path, method_name in tovo_locations:
    fp = os.path.join(BACKEND, rel_path.replace('/', os.sep))
    with open(fp, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # Count calls to toVO( in the same file (excluding the method declaration)
    # Method declaration pattern: private/public/protected ... toVO(
    # Call pattern: toVO( without preceding private/public/protected
    calls = []
    lines = content.split('\n')
    for i, line in enumerate(lines, 1):
        if 'toVO(' in line:
            # Check if it's a declaration (has private/public/protected)
            if any(x in line for x in ['private ', 'public ', 'protected ']):
                continue
            calls.append((i, line.strip()))
    
    if calls:
        print(f"\n{rel_path}: {len(calls)} CALLS FOUND")
        for ln, text in calls:
            print(f"  Line {ln}: {text[:100]}")
    else:
        print(f"\n{rel_path}: DEAD CODE (no calls)")
