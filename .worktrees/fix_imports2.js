const fs = require('fs');
const path = process.argv[2];
let content = fs.readFileSync(path, 'utf-8');

if (!content.includes('DictChangeEventPublisher;')) {
  const marker = 'DictItemExcelVO;';
  const idx = content.indexOf(marker);
  if (idx >= 0) {
    const insertAt = idx + marker.length;
    const newImports = '\nimport com.njyzsz.system.server.service.event.DictChangeEventConstants;\nimport com.njyzsz.system.server.service.event.DictChangeEventPublisher;';
    content = content.substring(0, insertAt) + newImports + content.substring(insertAt);
    fs.writeFileSync(path, content, 'utf-8');
    console.log('Imports inserted after DictItemExcelVO at position ' + insertAt);
  } else {
    console.log('MARKER NOT FOUND');
  }
} else {
  console.log('Already has DictChangeEventPublisher import');
}

console.log('HasConstants: ' + content.includes('import com.njyzsz.system.server.service.event.DictChangeEventConstants;'));
console.log('HasPublisher: ' + content.includes('import com.njyzsz.system.server.service.event.DictChangeEventPublisher;'));