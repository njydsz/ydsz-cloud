const fs = require('fs');
const path = process.argv[2];
const content = 'WRITE_TEST_CONTENT';
fs.writeFileSync(path, content, 'utf-8');
console.log('Written: ' + fs.readFileSync(path, 'utf-8'));