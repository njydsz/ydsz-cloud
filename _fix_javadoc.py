import pathlib

base = pathlib.Path(r'd:/Code/ydsz/ydsz-pmis/ydsz-backend/ydsz-common/ydsz-common-base/src/main/java/com/njydsz/common/base/filter')

files = [base / 'AbstractContentCachingFilter.java', base / 'RequestContextCleanupFilter.java']

for f in files:
    text = f.read_text(encoding='utf-8')
    # Remove empty line before Javadoc closing */
    text = text.replace(' * @since 1.0.0\n * \n */', ' * @since 1.0.0\n */')
    f.write_text(text, encoding='utf-8')
    print(f'Fixed: {f.name}')