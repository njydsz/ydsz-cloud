import pathlib

files = [
    r'd:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-userinfo\ydsz-userinfo-server\src\main\java\com\njydsz\userinfo\server\health\UserInfoHealthIndicator.java',
    r'd:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-system\ydsz-system-server\src\main\java\com\njydsz\system\server\health\SystemHealthIndicator.java',
    r'd:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-cronjob\ydsz-cronjob-server\src\main\java\com\njydsz\cronjob\server\health\CronjobHealthIndicator.java',
    r'd:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-message\ydsz-message-server\src\main\java\com\njydsz\message\server\health\MessageHealthIndicator.java',
    r'd:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-nextwiki\ydsz-nextwiki-server\src\main\java\com\njydsz\nextwiki\server\health\NextwikiHealthIndicator.java',
]

for fpath_str in files:
    fpath = pathlib.Path(fpath_str)
    if not fpath.exists():
        print(f'SKIP (not found): {fpath.name}')
        continue
    content = fpath.read_text(encoding='utf-8')
    if '@Component' not in content:
        print(f'SKIP (no @Component): {fpath.name}')
        continue
    content = content.replace('import org.springframework.stereotype.Component;\n', '')
    content = content.replace('@Component\n', '')
    fpath.write_text(content, encoding='utf-8')
    print(f'OK: {fpath.name}')
