import pathlib

base = pathlib.Path(r'd:/Code/ydsz/ydsz-pmis/ydsz-backend/ydsz-common/ydsz-common-base/src/main/java/com/njydsz/common/base/exporter')

files = [base / 'DefaultDocExporter.java', base / 'MarkdownDocExporter.java']

for f in files:
    text = f.read_text(encoding='utf-8')
    # Remove unused Component import
    text = text.replace('import org.springframework.stereotype.Component;\n', '')
    f.write_text(text, encoding='utf-8')
    print(f'Fixed: {f.name}')