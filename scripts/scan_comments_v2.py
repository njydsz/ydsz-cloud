import pathlib
import re

base = pathlib.Path(r'd:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-common')

results = []
for java_file in base.rglob('*.java'):
    if 'target' in java_file.parts:
        continue
    try:
        content = java_file.read_text(encoding='utf-8')
    except:
        continue

    lines = content.split('\n')
    has_class_javadoc = False
    javadoc_lines = 0
    class_keywords = [
        'public class', 'public interface', 'public enum',
        'public abstract class', 'public final class', 'public sealed',
        'public @interface', 'class ', 'interface ', 'enum ', '@interface'
    ]

    for i, line in enumerate(lines):
        stripped = line.strip()
        if stripped.startswith('/**'):
            j = i
            while j < len(lines) and '*/' not in lines[j]:
                j += 1
            javadoc_lines = j - i + 1
            k = j + 1
            while k < len(lines) and (lines[k].strip() == '' or lines[k].strip().startswith('@') or lines[k].strip().startswith('//')):
                k += 1
            if k < len(lines):
                next_line = lines[k].strip()
                if any(next_line.startswith(kw) for kw in class_keywords):
                    has_class_javadoc = True
                    break

    if not has_class_javadoc:
        rel = java_file.relative_to(base)
        results.append((str(rel), 'NO_CLASS_JAVADOC'))
    elif javadoc_lines <= 3:
        rel = java_file.relative_to(base)
        results.append((str(rel), f'MINIMAL_JAVADOC({javadoc_lines}_lines)'))

results.sort()
for path, issue in results:
    print(f'{issue}: {path}')
print(f'\nTotal files needing attention: {len(results)}')
