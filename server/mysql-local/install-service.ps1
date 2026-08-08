[CmdletBinding()]
param(
    [string]$CredentialPath
)

$ErrorActionPreference = "Stop"
$serviceName = "OnlineTestingMySQL"
$instanceRoot = "C:\ProgramData\MySQL\OnlineTestingMySQL"
$configPath = Join-Path $instanceRoot "my.ini"
$mysqlBin = "C:\Program Files\MySQL\MySQL Server 8.0\bin"
$mysql = Join-Path $mysqlBin "mysql.exe"
$mysqld = Join-Path $mysqlBin "mysqld.exe"
$resultPath = Join-Path $PSScriptRoot "install-result.txt"
$localPassword = $env:ONLINE_TESTING_MYSQL_PASSWORD
if (-not $localPassword -and $CredentialPath) {
    $credential = Import-Clixml -LiteralPath $CredentialPath
    $localPassword = $credential.GetNetworkCredential().Password
}

try {
    if (-not $localPassword) {
        throw "Thiếu biến môi trường ONLINE_TESTING_MYSQL_PASSWORD."
    }
    if ($localPassword -notmatch '^[A-Za-z0-9_@#%+=.!-]{4,128}$') {
        throw "Mật khẩu local chứa ký tự không được hỗ trợ bởi bộ cài tự động."
    }
    if (-not (Test-Path -LiteralPath $configPath)) {
        throw "Không tìm thấy cấu hình MySQL tại $configPath."
    }

    $service = Get-Service -Name $serviceName -ErrorAction SilentlyContinue
    if (-not $service) {
        & $mysqld --install $serviceName --defaults-file=$configPath
        if ($LASTEXITCODE -ne 0) {
            throw "Không thể đăng ký Windows service $serviceName."
        }
    }

    & sc.exe config $serviceName start= auto | Out-Null
    Start-Service -Name $serviceName

    $env:MYSQL_PWD = $localPassword
    try {
        & $mysql --host=127.0.0.1 --port=3307 --user=online_testing_app `
            --protocol=TCP --database=online_testing --connect-timeout=3 `
            --execute="SELECT 1;" 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) {
            "SUCCESS" | Set-Content -LiteralPath $resultPath -Encoding ASCII
            Write-Host "OnlineTestingMySQL đã được cài đặt và đang hoạt động." -ForegroundColor Green
            return
        }
    } finally {
        Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
    }

    $deadline = (Get-Date).AddSeconds(45)
    do {
        try {
            & $mysql --host=127.0.0.1 --port=3307 --user=root --protocol=TCP `
                --connect-timeout=2 --execute="SELECT 1;" 2>$null | Out-Null
            if ($LASTEXITCODE -eq 0) {
                break
            }
        } catch {
        }
        Start-Sleep -Seconds 1
    } while ((Get-Date) -lt $deadline)

    if ((Get-Date) -ge $deadline) {
        throw "MySQL không sẵn sàng trên cổng 3307 sau 45 giây."
    }

    $sql = @"
ALTER USER 'root'@'localhost' IDENTIFIED BY '$localPassword';
CREATE DATABASE IF NOT EXISTS ``online_testing``
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'online_testing_app'@'localhost' IDENTIFIED BY '$localPassword';
ALTER USER 'online_testing_app'@'localhost' IDENTIFIED BY '$localPassword';
GRANT ALL PRIVILEGES ON ``online_testing``.* TO 'online_testing_app'@'localhost';
CREATE USER IF NOT EXISTS 'online_testing_app'@'127.0.0.1' IDENTIFIED BY '$localPassword';
ALTER USER 'online_testing_app'@'127.0.0.1' IDENTIFIED BY '$localPassword';
GRANT ALL PRIVILEGES ON ``online_testing``.* TO 'online_testing_app'@'127.0.0.1';
FLUSH PRIVILEGES;
"@

    $sql | & $mysql --host=127.0.0.1 --port=3307 --user=root --protocol=TCP
    if ($LASTEXITCODE -ne 0) {
        throw "Không thể tạo database hoặc user ứng dụng."
    }

    $env:MYSQL_PWD = $localPassword
    try {
        & $mysql --host=127.0.0.1 --port=3307 --user=online_testing_app `
            --protocol=TCP --database=online_testing --execute="SELECT DATABASE();"
        if ($LASTEXITCODE -ne 0) {
            throw "User ứng dụng không kết nối được vào database."
        }
    } finally {
        Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
    }

    "SUCCESS" | Set-Content -LiteralPath $resultPath -Encoding ASCII
    Write-Host "OnlineTestingMySQL đã được cài đặt thành công." -ForegroundColor Green
} catch {
    ("FAILED: " + $_.Exception.Message) | Set-Content -LiteralPath $resultPath -Encoding UTF8
    Write-Error $_
    exit 1
} finally {
    Remove-Item Env:\ONLINE_TESTING_MYSQL_PASSWORD -ErrorAction SilentlyContinue
    if ($CredentialPath -and (Test-Path -LiteralPath $CredentialPath)) {
        Remove-Item -LiteralPath $CredentialPath -Force -ErrorAction SilentlyContinue
    }
}
