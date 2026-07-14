import pathlib

p = pathlib.Path(r'd:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-common\ydsz-pmis-common-util\src\main\java\com\njydsz\pmis\common\util\security\Base64Utils.java')
t = p.read_text(encoding='utf-8')

old_start = t.index('/**\n * Base64 \u7f16\u7801/\u89e3\u7801\u5de5\u5177\u7c7b')
old_end = t.index('public class Base64Utils {', old_start)

new_javadoc = """/**
 * Base64 \u7f16\u7801/\u89e3\u7801\u5de5\u5177\u7c7b\uff08\u5df2\u5e9f\u5f03\uff09
 *
 * <p>\u6807\u51c6\u7f16\u7801/\u89e3\u7801\u8bf7\u76f4\u63a5\u4f7f\u7528 {@link java.util.Base64}\u3002
 * \u4fdd\u7559\u6b64\u7c7b\u4ec5\u7528\u4e8e\u6587\u4ef6\u4e0e Base64 \u4e92\u8f6c\u529f\u80fd\u3002
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * @deprecated \u6807\u51c6\u7f16\u7801/\u89e3\u7801\u8bf7\u76f4\u63a5\u4f7f\u7528 {@link java.util.Base64}
 * @see Base64
 */
@Deprecated(since = "1.4.0", forRemoval = true)
"""

t = t[:old_start] + new_javadoc + t[old_end:]
p.write_text(t, encoding='utf-8')
print('OK')
