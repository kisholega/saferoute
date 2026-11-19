# SafeRoute AI - interactive setup for alerts and public links.
# Run from the project folder:   powershell -ExecutionPolicy Bypass -File scripts\configure.ps1
# Writes the values into .env (which git ignores). Press Enter to skip any question.

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $root '.env'
if (-not (Test-Path $envFile)) { Copy-Item (Join-Path $root '.env.example') $envFile }

function Set-EnvValue([string]$key, [string]$value) {
    $lines = @(Get-Content $envFile -Encoding UTF8)
    $pattern = "^\s*#?\s*$key="
    $found = $false
    $out = foreach ($line in $lines) {
        if (-not $found -and $line -match $pattern) { $found = $true; "$key=$value" } else { $line }
    }
    if (-not $found) { $out += "$key=$value" }
    Set-Content -Path $envFile -Value $out -Encoding UTF8
}

function Read-Secret([string]$prompt) {
    $secure = Read-Host -Prompt $prompt -AsSecureString
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr) } finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) }
}

Write-Host ""
Write-Host "=== SafeRoute AI setup ===" -ForegroundColor Magenta
Write-Host "Values are saved in $envFile"
Write-Host ""

Write-Host "1) Public tracking links (Cloudflare quick tunnel, free)" -ForegroundColor Cyan
$tunnel = Read-Host "   Turn on public links so contacts can open them on any device? [Y/n]"
if ($tunnel -eq '' -or $tunnel -match '^[Yy]') { Set-EnvValue 'PUBLIC_TUNNEL' 'true' } else { Set-EnvValue 'PUBLIC_TUNNEL' 'false' }
if (-not (Get-Command cloudflared -ErrorAction SilentlyContinue) -and -not (Test-Path 'C:\Program Files (x86)\cloudflared\cloudflared.exe')) {
    Write-Host "   cloudflared is not installed. Install it with:  winget install Cloudflare.cloudflared" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "2) Email alerts via Gmail (needs an App Password: https://myaccount.google.com/apppasswords)" -ForegroundColor Cyan
$mail = Read-Host "   Gmail address (Enter to skip)"
if ($mail) {
    $pass = Read-Secret "   16-character App Password (input hidden)"
    Set-EnvValue 'MAIL_USERNAME' $mail
    Set-EnvValue 'MAIL_APP_PASSWORD' ($pass -replace '\s', '')
    Write-Host "   Email configured." -ForegroundColor Green
}

Write-Host ""
Write-Host "3) SMS alerts via Twilio (https://console.twilio.com)" -ForegroundColor Cyan
$sid = Read-Host "   Account SID, starts with AC (Enter to skip)"
if ($sid) {
    $token = Read-Secret "   Auth Token (input hidden)"
    $from = Read-Host "   Twilio phone number, e.g. +15551234567"
    Set-EnvValue 'TWILIO_ACCOUNT_SID' $sid
    Set-EnvValue 'TWILIO_AUTH_TOKEN' $token
    Set-EnvValue 'TWILIO_FROM_NUMBER' $from
    Write-Host "   SMS configured. Remember: trial accounts only text numbers verified in the Twilio console." -ForegroundColor Green
}

Write-Host ""
Write-Host "Done. Restart SafeRoute (Ctrl+C, then: mvn spring-boot:run) and press 'Test' on a contact card." -ForegroundColor Magenta
