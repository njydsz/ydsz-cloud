import os

filepath = r"d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-common\ydsz-pmis-common-json\src\main\java\com\njydsz\pmis\common\json\asm\AsmBeanCodecGenerator.java"

with open(filepath, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('com/remisoft/json', 'com/njydsz/pmis/common/json')
content = content.replace('RemiSerializer', 'YdszSerializer')
content = content.replace('RemiDeserializer', 'YdszDeserializer')

with open(filepath, 'w', encoding='utf-8') as f:
    f.write(content)

print("Fixed ASM internal class name references")
