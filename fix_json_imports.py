import os, re

auth_dir = r'd:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-common\ydsz-pmis-common-auth\src\main\java\com\njydsz\pmis\common\auth'
files = [
    'desensitize/ColumnDesensitizationService.java',
    'service/impl/RedisRoleColumnPermissionResolver.java',
    'service/impl/RedisRolePermissionLoader.java',
    'service/impl/RedisRoleDataPermissionResolver.java'
]

for f in files:
    fp = os.path.join(auth_dir, f)
    with open(fp, 'r', encoding='utf-8-sig') as fh:
        c = fh.read()
    
    # Fix ObjectMapper mapper = JsonUtils.getMapper(); ... mapper.readTree(
    c = re.sub(r'ObjectMapper mapper = JsonUtils\.getMapper\(\);\s*\n\s*JsonNode root = mapper\.readTree\(', 'JsonNode root = YdszJson.readTree(', c)
    c = c.replace('JsonUtils.getMapper().readTree(', 'YdszJson.readTree(')
    c = c.replace('mapper.readTree(', 'YdszJson.readTree(')
    
    # Fix .properties() -> .asMap().entrySet() on JsonNode/ObjectNode
    c = c.replace('.properties()', '.asMap().entrySet()')
    
    # Fix JsonNode.isEmpty() -> JsonNode.isMissing() (only for specific JsonNode variables)
    for var in ['menuNode', 'parsed', 'obj', 'node', 'rule', 'visibleColumns', 'editableColumns', 'desensitizeRules', 'root']:
        c = c.replace(f'!{var}.isEmpty()', f'!{var}.isMissing()')
        c = c.replace(f'{var}.isEmpty()', f'{var}.isMissing()')
    
    # Fix imports
    c = c.replace('import com.fasterxml.jackson.databind.JsonNode;', 'import com.njydsz.pmis.common.json.tree.JsonNode;')
    c = c.replace('import com.fasterxml.jackson.databind.ObjectMapper;\n', '')
    c = c.replace('import com.fasterxml.jackson.databind.node.ObjectNode;', 'import com.njydsz.pmis.common.json.tree.ObjectNode;')
    c = c.replace('import com.fasterxml.jackson.databind.node.ArrayNode;', 'import com.njydsz.pmis.common.json.tree.ArrayNode;')
    c = c.replace('import com.njydsz.pmis.common.util.json.JsonUtils;', 'import com.njydsz.pmis.common.json.YdszJson;')
    
    with open(fp, 'w', encoding='utf-8') as fh:
        fh.write(c)
    print(f'Fixed: {f}')

print('Done!')
