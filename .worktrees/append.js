const fs = require('fs');
const path = process.argv[2];
const append = process.argv[3];
fs.appendFileSync(path, append, 'utf-8');
console.log('Appended ' + append.length + ' chars to ' + path);