import pathlib
import re

def add_audit_annotations(file_path, module_name, audit_type):
    """Add @Audit annotations to POST/PUT/DELETE mappings in a controller file."""
    if not file_path.exists():
        print(f'  SKIP: {file_path} not found')
        return False
    
    content = file_path.read_text(encoding='utf-8')
    
    # Check if already has Audit imports
    has_audit_import = 'import com.njydsz.common.audit.annotation.Audit;' in content
    has_action_import = 'import com.njydsz.common.audit.enums.AuditAction;' in content
    has_type_import = 'import com.njydsz.common.audit.enums.AuditType;' in content
    
    lines = content.split('\n')
    modified = False
    
    # Find write methods and add @Audit
    i = 0
    while i < len(lines):
        line = lines[i].strip()
        
        # Check if this line is a write mapping
        is_write = False
        mapping_type = ''
        if re.match(r'@PostMapping\b', line):
            is_write = True
            mapping_type = 'PostMapping'
        elif re.match(r'@PutMapping\b', line):
            is_write = True
            mapping_type = 'PutMapping'
        elif re.match(r'@DeleteMapping\b', line):
            is_write = True
            mapping_type = 'DeleteMapping'
        
        if is_write:
            # Look backwards (up to 10 lines) to see if @Audit is already present
            has_audit = False
            for j in range(max(0, i - 10), i):
                if '@Audit' in lines[j]:
                    has_audit = True
                    break
            
            if not has_audit:
                # Determine action
                action_map = {'PostMapping': 'CREATE', 'PutMapping': 'UPDATE', 'DeleteMapping': 'DELETE'}
                action = action_map.get(mapping_type, 'OTHER')
                
                # Find method name (look ahead up to 5 lines)
                method_name = mapping_type.lower()
                for k in range(i + 1, min(i + 5, len(lines))):
                    m = re.search(r'(?:public|private|protected)\s+\S+\s+(\w+)\s*\(', lines[k])
                    if m:
                        method_name = m.group(1)
                        break
                
                # Insert @Audit annotation
                indent = lines[i][:len(lines[i]) - len(lines[i].lstrip())]
                audit_line = f'{indent}@Audit(module = "{module_name}", type = {audit_type}, action = AuditAction.{action}, content = "\'{method_name}\'")'
                lines.insert(i, audit_line)
                modified = True
                i += 1  # Account for inserted line
        
        i += 1
    
    if modified:
        # Add imports if needed (before the first import line)
        import_insert_idx = 0
        for idx, line in enumerate(lines):
            if line.strip().startswith('import '):
                import_insert_idx = idx
        
        imports_to_add = []
        if not has_audit_import:
            imports_to_add.append('import com.njydsz.common.audit.annotation.Audit;')
        if not has_action_import:
            imports_to_add.append('import com.njydsz.common.audit.enums.AuditAction;')
        if not has_type_import:
            imports_to_add.append('import com.njydsz.common.audit.enums.AuditType;')
        
        for imp in reversed(imports_to_add):
            lines.insert(import_insert_idx + 1, imp)
        
        file_path.write_text('\n'.join(lines), encoding='utf-8')
        print(f'  OK: {file_path.name} ({module_name}) - {sum(1 for l in lines if "@Audit" in l and "import" not in l)} audit annotations')
        return True
    else:
        print(f'  SKIP: {file_path.name} - no write methods without @Audit')
        return False


def main():
    base = pathlib.Path('d:/Code/ydsz/ydsz-pmis/ydsz-backend')
    
    controllers = [
        # ydsz-literule
        (base / 'ydsz-literule/ydsz-literule-web/src/main/java/com/njydsz/literule/web/RuleAdminController.java', '规则管理', 'AuditType.OPERATION'),
        (base / 'ydsz-literule/ydsz-literule-web/src/main/java/com/njydsz/literule/web/RuleVariableAdminController.java', '变量管理', 'AuditType.OPERATION'),
        (base / 'ydsz-literule/ydsz-literule-web/src/main/java/com/njydsz/literule/web/RuleDslController.java', 'DSL管理', 'AuditType.OPERATION'),
        (base / 'ydsz-literule/ydsz-literule-web/src/main/java/com/njydsz/literule/web/BreakpointController.java', '断点管理', 'AuditType.OPERATION'),
        (base / 'ydsz-literule/ydsz-literule-web/src/main/java/com/njydsz/literule/web/CEPController.java', 'CEP管理', 'AuditType.OPERATION'),
        
        # ydsz-cronjob
        (base / 'ydsz-cronjob/ydsz-cronjob-web/src/main/java/com/njydsz/cronjob/web/controller/JobController.java', '任务管理', 'AuditType.OPERATION'),
        (base / 'ydsz-cronjob/ydsz-cronjob-web/src/main/java/com/njydsz/cronjob/web/controller/JobDagController.java', 'DAG管理', 'AuditType.OPERATION'),
        (base / 'ydsz-cronjob/ydsz-cronjob-web/src/main/java/com/njydsz/cronjob/web/controller/AlertController.java', '告警管理', 'AuditType.OPERATION'),
        (base / 'ydsz-cronjob/ydsz-cronjob-web/src/main/java/com/njydsz/cronjob/web/controller/GlueCodeController.java', 'Glue代码', 'AuditType.OPERATION'),
        (base / 'ydsz-cronjob/ydsz-cronjob-web/src/main/java/com/njydsz/cronjob/web/controller/JobWebhookController.java', 'WebHook', 'AuditType.OPERATION'),
        (base / 'ydsz-cronjob/ydsz-cronjob-web/src/main/java/com/njydsz/cronjob/web/controller/JobGroupController.java', '任务分组', 'AuditType.OPERATION'),
        (base / 'ydsz-cronjob/ydsz-cronjob-web/src/main/java/com/njydsz/cronjob/web/controller/JobTaskController.java', '任务编排', 'AuditType.OPERATION'),
        (base / 'ydsz-cronjob/ydsz-cronjob-web/src/main/java/com/njydsz/cronjob/web/controller/ScheduleCalendarController.java', '调度日历', 'AuditType.OPERATION'),
        
        # ydsz-message
        (base / 'ydsz-message/ydsz-message-web/src/main/java/com/njydsz/message/web/controller/TemplateController.java', '模板管理', 'AuditType.OPERATION'),
        (base / 'ydsz-message/ydsz-message-web/src/main/java/com/njydsz/message/web/controller/RouteRuleController.java', '路由规则', 'AuditType.CONFIG'),
        (base / 'ydsz-message/ydsz-message-web/src/main/java/com/njydsz/message/web/controller/SubscriptionController.java', '订阅管理', 'AuditType.OPERATION'),
        (base / 'ydsz-message/ydsz-message-web/src/main/java/com/njydsz/message/web/controller/PreferenceController.java', '偏好设置', 'AuditType.OPERATION'),
        (base / 'ydsz-message/ydsz-message-web/src/main/java/com/njydsz/message/web/controller/CanaryController.java', '灰度管理', 'AuditType.CONFIG'),
        (base / 'ydsz-message/ydsz-message-web/src/main/java/com/njydsz/message/web/controller/UserChannelBindingController.java', '通道绑定', 'AuditType.OPERATION'),
        (base / 'ydsz-message/ydsz-message-web/src/main/java/com/njydsz/message/web/controller/MessageController.java', '消息管理', 'AuditType.OPERATION'),
        (base / 'ydsz-message/ydsz-message-web/src/main/java/com/njydsz/message/web/controller/NotificationController.java', '通知管理', 'AuditType.OPERATION'),
        (base / 'ydsz-message/ydsz-message-web/src/main/java/com/njydsz/message/web/controller/RecallController.java', '消息撤回', 'AuditType.OPERATION'),
        (base / 'ydsz-message/ydsz-message-web/src/main/java/com/njydsz/message/web/controller/DeadLetterController.java', '死信管理', 'AuditType.OPERATION'),
        
        # ydsz-nextwiki
        (base / 'ydsz-nextwiki/ydsz-nextwiki-web/src/main/java/com/njydsz/nextwiki/web/controller/FileController.java', '文件管理', 'AuditType.FILE'),
        (base / 'ydsz-nextwiki/ydsz-nextwiki-web/src/main/java/com/njydsz/nextwiki/web/controller/ShareController.java', '分享管理', 'AuditType.OPERATION'),
        (base / 'ydsz-nextwiki/ydsz-nextwiki-web/src/main/java/com/njydsz/nextwiki/web/controller/TrashController.java', '回收站', 'AuditType.OPERATION'),
        (base / 'ydsz-nextwiki/ydsz-nextwiki-web/src/main/java/com/njydsz/nextwiki/web/controller/TagController.java', '标签管理', 'AuditType.OPERATION'),
        (base / 'ydsz-nextwiki/ydsz-nextwiki-web/src/main/java/com/njydsz/nextwiki/web/controller/FileLockController.java', '文件锁定', 'AuditType.FILE'),
        (base / 'ydsz-nextwiki/ydsz-nextwiki-web/src/main/java/com/njydsz/nextwiki/web/controller/FileCommentController.java', '文件评论', 'AuditType.OPERATION'),
        (base / 'ydsz-nextwiki/ydsz-nextwiki-web/src/main/java/com/njydsz/nextwiki/web/controller/BatchImportController.java', '批量导入', 'AuditType.DATA'),
        
        # ydsz-agent
        (base / 'ydsz-agent/ydsz-agent-web/src/main/java/com/njydsz/agent/web/controller/AgentController.java', 'Agent管理', 'AuditType.OPERATION'),
        (base / 'ydsz-agent/ydsz-agent-web/src/main/java/com/njydsz/agent/web/controller/AgentDefinitionController.java', 'Agent定义', 'AuditType.OPERATION'),
        (base / 'ydsz-agent/ydsz-agent-web/src/main/java/com/njydsz/agent/web/controller/DagController.java', 'DAG管理', 'AuditType.OPERATION'),
        (base / 'ydsz-agent/ydsz-agent-web/src/main/java/com/njydsz/agent/web/controller/ChatController.java', '对话管理', 'AuditType.OPERATION'),
        (base / 'ydsz-agent/ydsz-agent-web/src/main/java/com/njydsz/agent/web/controller/RagController.java', 'RAG管理', 'AuditType.OPERATION'),
        (base / 'ydsz-agent/ydsz-agent-web/src/main/java/com/njydsz/agent/web/controller/HumanApprovalController.java', '人工审批', 'AuditType.OPERATION'),
    ]
    
    total = 0
    for file_path, module_name, audit_type in controllers:
        if add_audit_annotations(file_path, module_name, audit_type):
            total += 1
    
    print(f'\nTotal: {total} controllers updated')

if __name__ == '__main__':
    main()