param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Email = "",
    [string]$Password = "Password123",
    [string]$FullName = "Auth Test User"
)

if ([string]::IsNullOrWhiteSpace($Email)) {
    $timestamp = Get-date -Format "yyyyMMddHHmmss"
    $Email = "auth-test-$timestamp@example.com"
}

function Invoke-JsonRequest {
    param(
        [string]$Method,
        [string]$Url,
        [object]$Body
    )

    try{
        if ($null -eq $Body) {
            return Invoke-RestMethod -Method $Method -Uri $Url
        }

        $json = $Body | ConvertTo-Json -Depth 10

        return Invoke-RestMethod `
            -Method $Method `
            -Uri $Url `
            -ContentType "application/json" `
            -Body $json
    }
    catch {
        Write-Host "Request failed: $Method $Url" -ForegroundColor Red

        if ($_.ErrorDetails.Message) {
            Write-Host $_.ErrorDetails.Message -ForegroundColor Yellow
        }

        throw
    }
}

function Test-ExpectedError {
    param(
        [string]$Name,
        [string]$Method,
        [string]$Url,
        [object]$Body,
        [int]$ExpectedStatus
    )

    Write-Host ""
    Write-Host $Name -ForegroundColor Cyan

    $json = $Body | ConvertTo-Json -Depth 10

    try {
        $response = Invoke-RestMethod `
            -Method $Method `
            -Uri $Url `
            -ContentType "application/json" `
            -Body $json

        Write-Host "[FAIL] Expected HTTP $ExpectedStatus, but the request succeeded." -ForegroundColor Red
        $response | ConvertTo-Json -Depth 10
    }
    catch {
        $actualStatus = [int]$_.Exception.Response.StatusCode

        if ($actualStatus -eq $ExpectedStatus) {
            Write-Host "[PASS] Received expected HTTP $actualStatus." -ForegroundColor Green
        }
        else {
            Write-Host "[FAIL] Expected HTTP $ExpectedStatus, but received HTTP $actualStatus." -ForegroundColor Red
        }

        if ($_.ErrorDetails.Message) {
            try {
                $_.ErrorDetails.Message | ConvertFrom-Json | ConvertTo-Json -Depth 10
            }
            catch {
                Write-Host $_.ErrorDetails.Message -ForegroundColor Yellow
            }
        }
    }
}

Write-Host "Testing auth API at: $BaseUrl" -ForegroundColor Cyan
Write-Host "Email: $Email" -ForegroundColor Cyan

$registerBody = @{
    fullName = $FullName
    email = $Email
    password = $Password
}

Write-Host ""
Write-Host "1. Registering user..." -ForegroundColor Green

$registerResponse = Invoke-JsonRequest `
    -Method "Post" `
    -Url "$BaseUrl/api/auth/student/register" `
    -Body $registerBody

$registerResponse | ConvertTo-Json -Depth 10

$loginBody = @{
    email = $Email
    password = $Password
}

Write-Host ""
Write-Host "2. Logging in..." -ForegroundColor Green

$loginResponse = Invoke-JsonRequest `
    -Method "Post" `
    -Url "$BaseUrl/api/auth/student/login" `
    -Body $loginBody

$loginResponse | ConvertTo-Json -Depth 10

Write-Host ""
Write-Host "Token header value:" -ForegroundColor Green
Write-Host "$($loginResponse.tokenType) $($loginResponse.token)"

$invalidRegisterBody = @{
    fullName = ""
    email = "not-an-email"
    password = "123"
}

Test-ExpectedError `
    -Name "3. Register validation errors..." `
    -Method "Post" `
    -Url "$BaseUrl/api/auth/student/register" `
    -Body $invalidRegisterBody `
    -ExpectedStatus 400

$duplicateRegisterBody = @{
    fullName = $FullName
    email = $Email
    password = $Password
}

Test-ExpectedError `
    -Name "4. Registering duplicate email..." `
    -Method "Post" `
    -Url "$BaseUrl/api/auth/student/register" `
    -Body $duplicateRegisterBody `
    -ExpectedStatus 409

$wrongPasswordBody = @{
    email = $Email
    password = "WrongPassword123"
}

Test-ExpectedError `
    -Name "5. Logging in with wrong password..." `
    -Method "Post" `
    -Url "$BaseUrl/api/auth/student/login" `
    -Body $wrongPasswordBody `
    -ExpectedStatus 401
$validationId = Get-Date -Format "yyyyMMddHHmmssfff"

$loginBlankEmailBody = @{
    email = ""
    password = $Password
}

Test-ExpectedError `
    -Name "6. Login with blank email..." `
    -Method "Post" `
    -Url "$BaseUrl/api/auth/student/login" `
    -Body $loginBlankEmailBody `
    -ExpectedStatus 400

$loginBlankPasswordBody = @{
    email = $Email
    password = ""
}

Test-ExpectedError `
    -Name "7. Login with blank password..." `
    -Method "Post" `
    -Url "$BaseUrl/api/auth/student/login" `
    -Body $loginBlankPasswordBody `
    -ExpectedStatus 400

$registerBlankFullNameBody = @{
    fullName = ""
    email = "blank-name-$validationId@example.com"
    password = $Password
}

Test-ExpectedError `
    -Name "8. Register with blank full name..." `
    -Method "Post" `
    -Url "$BaseUrl/api/auth/student/register" `
    -Body $registerBlankFullNameBody `
    -ExpectedStatus 400

$tooLongFullName = ("A" * 101) -join ""

$registerLongFullNameBody = @{
    fullName = $tooLongFullName
    email = "long-name-$validationId@example.com"
    password = $Password
}

Test-ExpectedError `
    -Name "9. Register with full name over 100 characters..." `
    -Method "Post" `
    -Url "$BaseUrl/api/auth/student/register" `
    -Body $registerLongFullNameBody `
    -ExpectedStatus 400

$registerBlankEmailBody = @{
    fullName = $FullName
    email = ""
    password = $Password
}

Test-ExpectedError `
    -Name "10. Register with blank email..." `
    -Method "Post" `
    -Url "$BaseUrl/api/auth/student/register" `
    -Body $registerBlankEmailBody `
    -ExpectedStatus 400

$registerBlankPasswordBody = @{
    fullName = $FullName
    email = "blank-password-$validationId@example.com"
    password = ""
}

Test-ExpectedError `
    -Name "11. Register with blank password..." `
    -Method "Post" `
    -Url "$BaseUrl/api/auth/student/register" `
    -Body $registerBlankPasswordBody `
    -ExpectedStatus 400

$registerShortPasswordBody = @{
    fullName = $FullName
    email = "short-password-$validationId@example.com"
    password = "1234567"
}

Test-ExpectedError `
    -Name "12. Register with password shorter than 8 characters..." `
    -Method "Post" `
    -Url "$BaseUrl/api/auth/student/register" `
    -Body $registerShortPasswordBody `
    -ExpectedStatus 400

$tooLongPassword = ("A" * 73) -join ""

$registerLongPasswordBody = @{
    fullName = $FullName
    email = "long-password-$validationId@example.com"
    password = $tooLongPassword
}

Test-ExpectedError `
    -Name "13. Register with password over 72 characters..." `
    -Method "Post" `
    -Url "$BaseUrl/api/auth/student/register" `
    -Body $registerLongPasswordBody `
    -ExpectedStatus 400    
