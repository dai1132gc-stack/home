# MySQL root password reset script (requires Administrator)
# Usage: run PowerShell as Administrator, then:
#   & "D:\graduate\home\docs\reset-root.ps1"
# New root password: Root@2026 (see init-reset.sql)

$ErrorActionPreference = 'Stop'

$id = [System.Security.Principal.WindowsIdentity]::GetCurrent()
$principal = New-Object System.Security.Principal.WindowsPrincipal($id)
if (-not $principal.IsInRole([System.Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Write-Host "ERROR: Please run PowerShell as Administrator." -ForegroundColor Red
    exit 1
}

$mysqld = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqld.exe"
$myini  = "C:\ProgramData\MySQL\MySQL Server 8.0\my.ini"
$initfile = "D:\graduate\home\docs\init-reset.sql"

Write-Host "== 1/6 Stop MySQL80 service ==" -ForegroundColor Cyan
net stop MySQL80 2>&1 | Out-Host

Write-Host "== 2/6 Kill all leftover mysqld processes ==" -ForegroundColor Cyan
Get-Process mysqld -ErrorAction SilentlyContinue | ForEach-Object {
    Write-Host "  killing mysqld PID=$($_.Id)"
    Stop-Process -Id $_.Id -Force
}
Start-Sleep -Seconds 2

Write-Host "== 3/6 Start mysqld with --init-file to reset password ==" -ForegroundColor Cyan
$argString = "--defaults-file=`"$myini`" --init-file=`"$initfile`" --console"
$proc = Start-Process -FilePath $mysqld -ArgumentList $argString -PassThru -NoNewWindow
Write-Host "  mysqld started PID=$($proc.Id), waiting 15s for init-file..."
Start-Sleep -Seconds 15

Write-Host "== 4/6 Stop init-file mysqld ==" -ForegroundColor Cyan
if (-not $proc.HasExited) {
    Stop-Process -Id $proc.Id -Force
    Start-Sleep -Seconds 3
}

Write-Host "== 5/6 Start MySQL80 service ==" -ForegroundColor Cyan
net start MySQL80 2>&1 | Out-Host

Write-Host "== 6/6 Done. Root password is now: Root@2026 ==" -ForegroundColor Green
Write-Host ""
Write-Host "Next: init W1 database. Run this in PowerShell:" -ForegroundColor Yellow
Write-Host '  Get-Content D:\graduate\home\docs\init-db.sql | mysql -u root -p' -ForegroundColor Yellow
Write-Host "  (enter root password: Root@2026)" -ForegroundColor Yellow
Write-Host ""
Write-Host "Then verify:" -ForegroundColor Yellow
Write-Host '  mysql -u realestate_dev -pDy97513 -e "SHOW DATABASES LIKE ''realestate'';"' -ForegroundColor Yellow
