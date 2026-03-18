# 运行字段提取器Demo
$ErrorActionPreference = "Stop"

cd $PSScriptRoot

$jsoupJar = "$env:USERPROFILE\.m2\repository\org\jsoup\jsoup\1.17.2\jsoup-1.17.2.jar"

if (-not (Test-Path $jsoupJar)) {
    Write-Error "jsoup JAR not found at: $jsoupJar"
    Write-Host "Please ensure you have built the project with Maven to download jsoup."
    exit 1
}

Write-Host "Compiling field extractor demo..."
Write-Host "-----------------------------------------"

$classpath = ".;extractor;$jsoupJar"
javac -encoding UTF-8 -cp $classpath FieldExtractorDemo.java extractor/*.java

if ($LASTEXITCODE -ne 0) {
    Write-Error "Compilation failed!"
    exit 1
}

Write-Host ""
Write-Host "Compilation complete. Running demo..."
Write-Host "========================================="
Write-Host ""

java -cp $classpath FieldExtractorDemo
