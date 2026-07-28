import pathlib, re

files = {
    r'd:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-userinfo\ydsz-userinfo-domain\src\main\java\com\njydsz\userinfo\domain\enums\UserInfoResultCode.java': ('userinfo', '用户中心'),
    r'd:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-system\ydsz-system-domain\src\main\java\com\njydsz\system\domain\enums\SystemResultCode.java': ('system', '系统管理'),
    r'd:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-project\ydsz-project-domain\src\main\java\com\njydsz\project\domain\enums\ProjectResultCode.java': ('project', '项目管理'),
    r'd:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-workflow\ydsz-workflow-domain\src\main\java\com\njydsz\workflow\domain\enums\WorkflowResultCode.java': ('workflow', '工作流'),
    r'd:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-message\ydsz-message-domain\src\main\java\com\njydsz\message\domain\enums\MessageResultCode.java': ('message', '消息中心'),
    r'd:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-cronjob\ydsz-cronjob-domain\src\main\java\com\njydsz\cronjob\domain\enums\CronjobResultCode.java': ('cronjob', '定时任务'),
    r'd:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-literule\ydsz-literule-domain\src\main\java\com\njydsz\literule\domain\enums\LiteruleResultCode.java': ('literule', '规则引擎'),
    r'd:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-agent\ydsz-agent-domain\src\main\java\com\njydsz\agent\domain\enums\AgentResultCode.java': ('agent', 'AI Agent'),
}

for fpath_str, (module, desc) in files.items():
    fpath = pathlib.Path(fpath_str)
    if not fpath.exists():
        print(f'SKIP (not found): {fpath.name}')
        continue
    content = fpath.read_text(encoding='utf-8')
    if '@YdszResultCode' in content:
        print(f'SKIP (already has): {fpath.name}')
        continue
    # Add import
    import_line = 'import com.njydsz.common.exception.registry.YdszResultCode;'
    if import_line not in content:
        # Find last import line
        lines = content.split('\n')
        last_import_idx = 0
        for i, line in enumerate(lines):
            if line.startswith('import '):
                last_import_idx = i
        lines.insert(last_import_idx + 1, import_line)
        content = '\n'.join(lines)
    # Add annotation before enum declaration
    annotation = f'@YdszResultCode(module = "{module}", description = "{desc}")\n'
    content = re.sub(r'(public\s+enum\s+)', annotation + r'\1', content, count=1)
    fpath.write_text(content, encoding='utf-8')
    print(f'OK: {fpath.name}')
