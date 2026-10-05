const fs = require('fs');
const path = process.argv[2];
let content = fs.readFileSync(path, 'utf-8');

// 1. Add imports after the last existing import
const lastImport = 'import com.njyzsz.system.server.vo.DictItemExcelVO;';
const newImports = lastImport + '\n' +
  'import com.njyzsz.system.server.service.event.DictChangeEventConstants;\n' +
  'import com.njyzsz.system.server.service.event.DictChangeEventPublisher;';
content = content.replace(lastImport, newImports);

// 2. Add field after the last existing field (eventPublisher)
const lastField = '  /** Spring 事件发布器（用于异步创建版本快照，P3-2 版本快照异步化） */\n' +
  '  private final ApplicationEventPublisher eventPublisher;';
const newField = lastField + '\n\n' +
  '  /** Redis 字典变更事件发布器（用于 SSE 多端广播） */\n' +
  '  private final DictChangeEventPublisher dictChangeEventPublisher;';
content = content.replace(lastField, newField);

// 3. Add publisher call after dictRepository.insertItem(dto); in save()
const saveEnd = '    dictRepository.insertItem(dto);\n    return dto.getId();';
const saveNew = '    dictRepository.insertItem(dto);\n' +
  '    dictChangeEventPublisher.publishDictItemEvent(\n' +
  '        dto.getTypeCode(), dto.getItemCode(), DictChangeEventConstants.EVENT_TYPE_CREATED);\n' +
  '    return dto.getId();';
content = content.replace(saveEnd, saveNew);

// 4. Add publisher call in updateById after cache eviction
const updateEnd = '      }\n    }\n    return updated;';
const updateNew = '      }\n    }\n' +
  '    if (updated) {\n' +
  '      dictChangeEventPublisher.publishDictItemEvent(\n' +
  '          dto.getTypeCode(), dto.getItemCode(), DictChangeEventConstants.EVENT_TYPE_UPDATED);\n' +
  '    }\n' +
  '    return updated;';
content = content.replace(updateEnd, updateNew);

// 5. Add publisher call in removeById
const removeEnd = '      evictDictList(vo.getTypeCode());\n    }\n    return removed;';
const removeNew = '      evictDictList(vo.getTypeCode());\n' +
  '      dictChangeEventPublisher.publishDictItemEvent(\n' +
  '          vo.getTypeCode(), vo.getItemCode(), DictChangeEventConstants.EVENT_TYPE_DELETED);\n' +
  '    }\n' +
  '    return removed;';
content = content.replace(removeEnd, removeNew);

fs.writeFileSync(path, content, 'utf-8');
console.log('DictItemServiceImpl updated: ' + content.length + ' chars');
console.log('Has DictChangeEventPublisher import: ' + content.includes('import com.njyzsz.system.server.service.event.DictChangeEventPublisher;'));
console.log('Has field decl: ' + content.includes('dictChangeEventPublisher;'));
console.log('Has EVENT_TYPE_CREATED: ' + content.includes('EVENT_TYPE_CREATED'));
console.log('Has EVENT_TYPE_UPDATED: ' + content.includes('EVENT_TYPE_UPDATED'));
console.log('Has EVENT_TYPE_DELETED: ' + content.includes('EVENT_TYPE_DELETED'));