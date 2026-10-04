param([Parameter(Mandatory = $true)][string]$KotlinLibDirectory)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$outputDirectory = Join-Path $root 'build/wp07-jvm'
New-Item -ItemType Directory -Force $outputDirectory | Out-Null
$libraryJars = Get-ChildItem -LiteralPath $KotlinLibDirectory -Filter '*.jar' | Where-Object { $_.Name -match '^(kotlin-stdlib-|junit-|hamcrest-core-|annotations-)' }
$classPath = ($libraryJars.FullName -join [IO.Path]::PathSeparator)
$sources = @()
foreach ($area in @('model', 'geometry', 'vision', 'data')) {
    $sources += Get-ChildItem -LiteralPath "$root/app/src/main/java/app/ascend/mobile/core/$area" -Filter '*.kt' -ErrorAction SilentlyContinue
    $sources += Get-ChildItem -LiteralPath "$root/app/src/test/java/app/ascend/mobile/core/$area" -Filter '*.kt' -ErrorAction SilentlyContinue
}
$jar = Join-Path $outputDirectory 'front-tests.jar'
& java -cp "$KotlinLibDirectory/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 17 -classpath $classPath -d $jar @($sources.FullName)
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$testNames = @($sources | Where-Object { $_.Name -like '*Test.kt' } | ForEach-Object {
    $package = (Select-String -LiteralPath $_.FullName -Pattern '^package (.+)$').Matches[0].Groups[1].Value
    "$package.$($_.BaseName)"
})
& java -cp "$jar$([IO.Path]::PathSeparator)$classPath" org.junit.runner.JUnitCore @testNames
exit $LASTEXITCODE
