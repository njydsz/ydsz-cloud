const fs = require('fs');
const path = process.argv[2];
let content = fs.readFileSync(path, 'utf-8');

// Check if imports already present
if (!content.includes('import com.njyzsz.system.server.service.event.DictChangeEventPublisher')) {
  // Find the DictItemExcelVO import and add after it
  const lines = content.split('\n');
  let insertIdx = -1;
  for (let i = 0; i < lines.length; i++) {
    if (lines[i].trim().startsWith('import com.njyzsz.system.server.vo.DictItemExcelVO;')) {
      insertIdx = i + 1;
      break;
    }
  }
  if (insertIdx >= 0) {
    lines.splice(insertIdx, 0, 
      'import com.njyzsz.system.server.service.event.DictChangeEventConstants;',
      'import com.njyzsz.system.server.service.event.DictChangeEventPublisher;');
    content = lines.join('\n');
    fs.writeFileSync(path, content, 'utf-8');
    console.log('Added imports at line ' + insertIdx);
  } else {
    console.log('WARNING: Could not find DictItemExcelVO import');
  }
} else {
  console.log('Imports already present');
}

// Verify all changes
const checks = [
  ['DictChangeEventPublisher import', 'import com.njyzsz.system.server.service.event.DictChangeEventPublisher;'],
  ['DictChangeEventConstants import', 'import com.njyzsz.system.server.service.event.DictChangeEventConstants;'],
  ['field declaration', 'private final DictChangeEventPublisher dictChangeEventPublisher;'],
  ['save() EVENT_TYPE_CREATED', 'DictChangeEventConstants.EVENT_TYPE_CREATED'],
  ['update() EVENT_TYPE_UPDATED', 'DictChangeEventConstants.EVENT_TYPE_UPDATED'],
  ['remove() EVENT_TYPE_DELETED', 'DictChangeEventConstants.EVENT_TYPE_DELETED'],
];
for (const [name, search] of checks) {
  console.log(name + ': ' + content.includes(search));
}