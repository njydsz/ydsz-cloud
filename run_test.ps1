Set-Location d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend
& mvn -pl ydsz-pmis-workflow -am test '-Dtest=FlowTaskServiceImplTest' '-Dsurefire.failIfNoSpecifiedTests=false' 2>&1 | Select-Object -Last 200
