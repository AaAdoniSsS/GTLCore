$ErrorActionPreference = 'Stop'
$graphRoot = Split-Path $PSScriptRoot -Parent
$graphTestClasses = Join-Path $graphRoot 'build/graph-test-classes'
New-Item -ItemType Directory -Force -Path $graphTestClasses | Out-Null
$graphJava = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin' } else { Split-Path (Get-Command javac.exe -ErrorAction Stop).Source -Parent }
$graphSources = @(Get-ChildItem -LiteralPath (Join-Path $graphRoot 'src/main/java/org/gtlcore/gtlcore/integration/ae2/graph/core') -Filter '*.java' | ForEach-Object FullName)
$graphSources += @(Get-ChildItem -LiteralPath (Join-Path $graphRoot 'src/test/java/org/gtlcore/gtlcore/integration/ae2/graph') -Filter '*.java' | Where-Object Name -notlike 'GraphAe*.java' | ForEach-Object FullName)
& (Join-Path $graphJava 'javac.exe') --release 17 -encoding UTF-8 -d $graphTestClasses @graphSources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& (Join-Path $graphJava 'java.exe') -ea -cp $graphTestClasses org.gtlcore.gtlcore.integration.ae2.graph.GraphCoreTest
exit $LASTEXITCODE
