[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$serverRoot = Split-Path -Parent $PSScriptRoot
$projectRoot = Split-Path -Parent $serverRoot
$localProperties = Join-Path $serverRoot "src\main\resources\application-local.properties"
$gradle = Join-Path $projectRoot "gradlew.bat"
$testBuildDir = Join-Path $env:TEMP "WedSiteOnlineTesting-mysql-tests"
$mysql = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"

if (-not (Test-Path -LiteralPath $localProperties)) {
    throw "Không tìm thấy application-local.properties. Hãy thiết lập MySQL local trước."
}

$properties = @{}
Get-Content -LiteralPath $localProperties | ForEach-Object {
    if ($_ -match '^\s*([^#][^=]*)=(.*)$') {
        $properties[$matches[1].Trim()] = $matches[2].Trim()
    }
}

$url = $properties['spring.datasource.url']
$username = $properties['spring.datasource.username']
$password = $properties['spring.datasource.password']
if (-not $url -or -not $username) {
    throw "Cấu hình datasource local không đầy đủ."
}

$testUrl = $url -replace '/online_testing(?=\?|$)', '/online_testing_test'
if ($testUrl -eq $url) {
    throw "Không thể chuyển URL local sang schema online_testing_test."
}

if (-not (Test-Path -LiteralPath $mysql)) {
    throw "Không tìm thấy MySQL client tại $mysql."
}

$env:MYSQL_PWD = $password
try {
    & $mysql --host=127.0.0.1 --port=3307 --user=$username --protocol=TCP `
        --execute="DROP DATABASE IF EXISTS ``online_testing_test``; CREATE DATABASE ``online_testing_test`` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
    if ($LASTEXITCODE -ne 0) {
        throw "Không thể tạo lại schema online_testing_test."
    }
} finally {
    Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
}

$env:SPRING_DATASOURCE_URL = $testUrl
$env:SPRING_DATASOURCE_USERNAME = $username
$env:SPRING_DATASOURCE_PASSWORD = $password
$env:SPRING_DATASOURCE_DRIVER_CLASS_NAME = "com.mysql.cj.jdbc.Driver"
$env:SPRING_JPA_HIBERNATE_DDL_AUTO = "create"

try {
    & $gradle :server:clean :server:mysqlTest --no-daemon "-PbuildDirOverride=$testBuildDir"
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
} finally {
    Remove-Item Env:\SPRING_DATASOURCE_URL -ErrorAction SilentlyContinue
    Remove-Item Env:\SPRING_DATASOURCE_USERNAME -ErrorAction SilentlyContinue
    Remove-Item Env:\SPRING_DATASOURCE_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:\SPRING_DATASOURCE_DRIVER_CLASS_NAME -ErrorAction SilentlyContinue
    Remove-Item Env:\SPRING_JPA_HIBERNATE_DDL_AUTO -ErrorAction SilentlyContinue
}
