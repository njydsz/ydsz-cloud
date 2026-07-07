$env:MAVEN_OPTS = "-Xmx2g"
$tests = "SystemConstantsTest,AuditFieldFillerTest,CommonConstantsTest,BaseDOTest,SecurityContextTest"
Set-Location "d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-common"
& mvn test "-Dtest=$tests" "-Dmaven.test.failure.ignore=true" "-DfailIfNoTests=false" -q 2>&1 | Select-Object -Last 80
