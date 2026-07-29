import pathlib, re

f = pathlib.Path('ydsz-backend/ydsz-common/ydsz-common-json/src/main/java/com/njydsz/common/json/cache/FieldMeta.java')
content = f.read_text(encoding='utf-8')

# Add comments to empty catch blocks that just return or fall through
# Pattern: catch (Throwable e) {\n} → add comment
content = content.replace(
    'catch (Throwable e) {\n}\n',
    'catch (Throwable e) {\n    // MethodHandle.invoke 异常（如 SecurityException/IllegalAccessException），回退到反射路径\n}\n'
)
# Also fix the empty catch at line 331-332
content = content.replace(
    'catch (Exception ignored) {\n}\n',
    'catch (Exception ignored) {\n    // VarHandle 不可用（如 GraalVM Native Image），回退到 MethodHandle\n}\n'
)

f.write_text(content, encoding='utf-8')
print('P2-4: Empty catch blocks annotated')
