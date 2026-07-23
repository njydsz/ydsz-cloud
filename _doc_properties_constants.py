import pathlib
import re

file = pathlib.Path(r'd:/Code/ydsz/ydsz-pmis/ydsz-backend/ydsz-common/ydsz-common-base/src/main/java/com/njydsz/common/base/config/DocProperties.java')
text = file.read_text(encoding='utf-8')

# Add import if not present
if 'import com.njydsz.common.base.constant.DocConstants;' not in text:
    text = text.replace(
        'import com.njydsz.common.base.constant.BaseFilterOrders;',
        'import com.njydsz.common.base.constant.BaseFilterOrders;\nimport com.njydsz.common.base.constant.DocConstants;'
    )

# Replace hardcoded default values with DocConstants
text = re.sub(
    r'private String apiDocsPath = "/v3/api-docs";',
    'private String apiDocsPath = DocConstants.DEFAULT_API_DOCS_PATH;',
    text
)
text = re.sub(
    r'private String knife4jPath = "/doc.html";',
    'private String knife4jPath = DocConstants.DEFAULT_KNIFE4J_PATH;',
    text
)
text = re.sub(
    r'private String format = "json";',
    'private String format = DocConstants.FORMAT_JSON;',
    text
)
text = re.sub(
    r'private String version = "1.0.0";',
    'private String version = DocConstants.DEFAULT_API_VERSION;',
    text
)

file.write_text(text, encoding='utf-8')
print('Updated DocProperties to use DocConstants')