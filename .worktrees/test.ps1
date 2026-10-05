$filePath = "D:\Code\open\ydsz-cloud\ydsz-system\ydsz-system-server\src\main\java\com\njyzsz\system\server\service\impl\DictServiceImpl.java"
$content = @'
package com.njyzsz.system.server.service.impl;
'@
[System.IO.File]::WriteAllText($filePath, $content)