param(
    [Parameter(Mandatory = $true)][string]$KotlinLibDirectory,
    [Parameter(Mandatory = $true)][string]$JsonLibDirectory
)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskOutput = Join-Path $taskRoot 'build/wp09-jvm'
New-Item -ItemType Directory -Force $taskOutput | Out-Null
$taskJars = @(Get-ChildItem -LiteralPath $KotlinLibDirectory -Filter '*.jar' | Where-Object Name -Match '^(kotlin-stdlib-|junit-|hamcrest-core-|annotations-)')
$taskJars += @(Get-ChildItem -LiteralPath $JsonLibDirectory -Filter '*.jar')
$taskClasspath = $taskJars.FullName -join [IO.Path]::PathSeparator
$taskSources = @()
foreach ($taskArea in @('model', 'geometry', 'profile')) {
    $taskSources += Get-ChildItem "$taskRoot/app/src/main/java/app/ascend/mobile/core/$taskArea" -Filter '*.kt'
    $taskSources += Get-ChildItem "$taskRoot/app/src/test/java/app/ascend/mobile/core/$taskArea" -Filter '*.kt'
}
$taskJar = Join-Path $taskOutput 'profile-tests.jar'
& java -cp "$KotlinLibDirectory/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 17 -classpath $taskClasspath -d $taskJar @($taskSources.FullName)
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$taskTests = @($taskSources | Where-Object Name -Like '*Test.kt' | ForEach-Object {
    $taskPackage = (Select-String -LiteralPath $_.FullName -Pattern '^package (.+)$').Matches[0].Groups[1].Value
    "$taskPackage.$($_.BaseName)"
})
& java -cp "$taskJar$([IO.Path]::PathSeparator)$taskClasspath$([IO.Path]::PathSeparator)$taskRoot/app/src/test/resources" org.junit.runner.JUnitCore @taskTests
exit $LASTEXITCODE
