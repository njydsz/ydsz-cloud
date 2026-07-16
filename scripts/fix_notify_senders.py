import pathlib
import re

base = pathlib.Path('ydsz-pmis-backend/ydsz-pmis-common/ydsz-pmis-common-notify/src/main/java/com/njydsz/pmis/common/notify')

# 1. Fix EmailNotifySender.java
f = base / 'channel/EmailNotifySender.java'
content = f.read_text(encoding='utf-8')
content = content.replace(' * @since 1.0.0\n * @since 1.0.0\n', ' * @since 1.0.0\n')
content = re.sub(
    r'\t// =+ 内部方法 =+\n\n\tprivate String channelName\(\) \{\n\t\treturn "邮件";\n\t\}\n',
    '\t// ==================== 内部方法 ====================\n',
    content
)
content = content.replace('channelName()', 'getChannel().getName()')
f.write_text(content, encoding='utf-8')
print('EmailNotifySender.java fixed')

# 2. Fix NotifyHealthIndicator.java - remove @Component, fix @since
f = base / 'health/NotifyHealthIndicator.java'
content = f.read_text(encoding='utf-8')
content = content.replace(' * @since 1.0.0\n * @since 1.0.0\n', ' * @since 1.0.0\n')
# Remove @Component and its import
content = content.replace('import org.springframework.stereotype.Component;\n', '')
content = content.replace('@Component\n', '')
f.write_text(content, encoding='utf-8')
print('NotifyHealthIndicator.java fixed')

# 3. Fix EmailMessage.java - duplicate @since
f = base / 'channel/EmailMessage.java'
content = f.read_text(encoding='utf-8')
content = content.replace(' * @since 1.0.0\n * @since 1.0.0\n', ' * @since 1.0.0\n')
content = content.replace(' * @since 1.0.0\n *\n * @since 1.0.0\n', ' * @since 1.0.0\n')
f.write_text(content, encoding='utf-8')
print('EmailMessage.java fixed')

# 4. Fix NotifyType.java - duplicate @since
f = base / 'enums/NotifyType.java'
content = f.read_text(encoding='utf-8')
content = content.replace(' * @since 1.0.0\n * @since 1.0.0\n', ' * @since 1.0.0\n')
f.write_text(content, encoding='utf-8')
print('NotifyType.java fixed')

# 5. Batch clean duplicate @since in all Java files under notify module
count = 0
for java_file in base.rglob('*.java'):
    content = java_file.read_text(encoding='utf-8')
    original = content
    # Pattern: * @since X.X.X followed by * @since X.X.X (with optional blank line between)
    content = re.sub(r' \* @since (\S+)\n \*\n \* @since \1\n', ' * @since \\1\n', content)
    content = re.sub(r' \* @since (\S+)\n \* @since \1\n', ' * @since \\1\n', content)
    if content != original:
        java_file.write_text(content, encoding='utf-8')
        count += 1
        print(f'  Cleaned @since: {java_file.name}')
print(f'Total files with @since cleanup: {count}')
