import pathlib

file = pathlib.Path(r'd:/Code/ydsz/ydsz-pmis/ydsz-backend/ydsz-common/ydsz-common-base/src/main/resources/META-INF/additional-spring-configuration-metadata.json')
text = file.read_text(encoding='utf-8')

# Remove trace-related entries (lines 58-98, after security-headers and before doc)
text = text.splitlines()

# Find start and end indices
start_idx = None
end_idx = None
for i, line in enumerate(text):
    if 'ydsz.base.trace.enabled' in line:
        start_idx = i - 5  # Include opening brace and leading whitespace
    if start_idx is not None and 'ydsz.doc.enabled' in line:
        end_idx = i - 5
        break

if start_idx is not None and end_idx is not None:
    text = text[:start_idx] + text[end_idx:]
    file.write_text('\n'.join(text) + '\n', encoding='utf-8')
    print(f'Removed trace entries (lines {start_idx}-{end_idx})')
else:
    print(f'Could not find trace entries (start={start_idx}, end={end_idx})')