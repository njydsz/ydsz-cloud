import pathlib

# 1. Add ydsz-common-audit dependency to web pom.xml files
modules = ['ydsz-literule', 'ydsz-cronjob', 'ydsz-message', 'ydsz-nextwiki', 'ydsz-agent']

for module in modules:
    pom_path = pathlib.Path(f'd:/Code/ydsz/ydsz-pmis/ydsz-backend/{module}/{module}-web/pom.xml')
    content = pom_path.read_text(encoding='utf-8')
    
    if 'ydsz-common-audit' in content:
        print(f'{module}: already has audit dependency, skipping')
        continue
    
    # Add audit dependency after common-web dependency
    old = """            <artifactId>ydsz-common-web</artifactId>
        </dependency>"""
    new = """            <artifactId>ydsz-common-web</artifactId>
        </dependency>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-common-audit</artifactId>
        </dependency>"""
    
    if old in content:
        content = content.replace(old, new)
        pom_path.write_text(content, encoding='utf-8')
        print(f'{module}: added audit dependency to pom.xml')
    else:
        print(f'{module}: could not find common-web dependency pattern')

# 2. Add @EnableYdszAudit to Application.java files
for module in modules:
    sub_module = module.split('-')[1]  # e.g., 'literule' from 'ydsz-literule'
    app_dir = pathlib.Path(f'd:/Code/ydsz/ydsz-pmis/ydsz-backend/{module}/{module}-web/src/main/java/com/njydsz/{sub_module}/web')
    
    app_files = list(app_dir.glob('*Application.java'))
    if not app_files:
        print(f'{module}: no Application.java found in {app_dir}')
        continue
    
    app_file = app_files[0]
    content = app_file.read_text(encoding='utf-8')
    
    if '@EnableYdszAudit' in content:
        print(f'{module}: already has @EnableYdszAudit, skipping')
        continue
    
    # Add import statement
    if 'import com.njydsz.common.audit.annotation.EnableYdszAudit;' not in content:
        lines = content.split('\n')
        insert_idx = -1
        for i, line in enumerate(lines):
            if line.strip().startswith('import com.njydsz.common.') and 'audit' not in line:
                insert_idx = i
        if insert_idx >= 0:
            lines.insert(insert_idx + 1, 'import com.njydsz.common.audit.annotation.EnableYdszAudit;')
            content = '\n'.join(lines)
            print(f'{module}: added import')
        else:
            print(f'{module}: could not find import location')
            continue
    
    # Add @EnableYdszAudit annotation before @EnableYdszSafe
    if '@EnableYdszSafe' in content:
        content = content.replace('@EnableYdszSafe', '@EnableYdszAudit\n@EnableYdszSafe')
    elif '@EnableYdszAuth' in content:
        content = content.replace('@EnableYdszAuth', '@EnableYdszAudit\n@EnableYdszAuth')
    else:
        # Find @SpringBootApplication and add before it
        content = content.replace('@SpringBootApplication', '@EnableYdszAudit\n@SpringBootApplication')
    
    app_file.write_text(content, encoding='utf-8')
    print(f'{module}: added @EnableYdszAudit to {app_file.name}')

print('Done!')