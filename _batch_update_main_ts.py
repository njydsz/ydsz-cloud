import re
import os

apps = ['agent-web','cronjob-web','literule-web','message-web','nextwiki-web','project-web','system-web','userinfo-web','workflow-web']

for app in apps:
    path = os.path.join('ydsz-frontend', 'apps', app, 'src', 'main.ts')
    with open(path, 'r', encoding='utf-8') as f:
        content = f.read()

    # 1. Replace import
    content = content.replace(
        "import { initSharedRequest } from '@ydsz/shared-auth';",
        "import { setupSharedAuth } from '@ydsz/shared-auth';"
    )

    # 2. Remove initSharedAuth function definition
    # Pattern: from the Javadoc comment to the closing brace
    pattern = r'/\*\*?\n \* 初始化共享请求客户端.*?\n \*/\nasync function initSharedAuth\(\) \{.*?\n\}\n'
    content = re.sub(pattern, '', content, flags=re.DOTALL)

    # 3. Replace calls: await initSharedAuth() -> await setupSharedAuth('appname')
    content = content.replace(
        'await initSharedAuth();',
        "await setupSharedAuth('" + app + "');"
    )

    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)
    print(f'Updated: {path}')

print('Done: 9 apps updated')
