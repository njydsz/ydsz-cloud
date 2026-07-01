Set-Location d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend
& mvn -pl ydsz-pmis-workflow -am test 2>&1 | Select-Object -Last 150
