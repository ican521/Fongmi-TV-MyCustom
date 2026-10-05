# 一键把本地 arm64 APK 复制到手机「下载」文件夹，并可自动安装（MTP 复制 + 可选 adb 安装）
# 双击 push-apk-to-phone.bat 运行
$ErrorActionPreference = 'Stop'

try {

# 1) 本地最新 arm64 APK
$apkDir = Join-Path $PSScriptRoot 'app\build\outputs\apk\mobile\debug'
$apk = Get-ChildItem -Path $apkDir -Filter '*arm64*.apk' -File -ErrorAction SilentlyContinue |
       Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $apk) { Write-Host "[X] 未找到 arm64 APK：$apkDir" -ForegroundColor Red; Read-Host "回车退出"; exit 1 }
Write-Host "[√] 源文件: $($apk.Name)  ($([math]::Round($apk.Length/1MB,1)) MB)" -ForegroundColor Green

# 2) 定位设备（MTP：此电脑里便携设备）
$shell = New-Object -ComObject Shell.Application
$pc = $shell.Namespace(17)
$devItem = $null
foreach ($i in $pc.Items()) {
    if ($i.IsFolder -and $i.Path -like '*\\?\usb#*' -and ($i.Name -like '*Xiaomi*' -or $i.Name -like '*手机*')) { $devItem = $i; break }
}
if (-not $devItem) {
    Write-Host "[X] 未在【此电脑】找到便携设备。请确认数据线已连、手机已解锁、USB 模式为【文件传输】。" -ForegroundColor Red
    Read-Host "回车退出"; exit 1
}
Write-Host "[√] 设备: $($devItem.Name)" -ForegroundColor Green

# 3) 等 MTP 内容就绪，进入「内部存储」
Write-Host "    正在读取手机内容..."
$devFolder = $null
for ($t = 0; $t -lt 20; $t++) {
    try { $f = $devItem.GetFolder; $c = @($f.Items()).Count } catch { $c = 0 }
    if ($c -gt 0) { $devFolder = $f; break }
    Start-Sleep 1
}
if (-not $devFolder) {
    Write-Host "[X] 读不到手机内容。请：①关掉所有打开着【此电脑\Xiaomi 13】的资源管理器窗口；②手机保持解锁；③拔线重连后再试。" -ForegroundColor Red
    Read-Host "回车退出"; exit 1
}
$storage = $null
foreach ($i in $devFolder.Items()) { if ($i.IsFolder) { $storage = $i; break } }   # 第一个即内部存储
$storageFolder = $storage.GetFolder

# 4) 进入「Download / 下载」
$dl = $null
foreach ($i in $storageFolder.Items()) { if ($i.IsFolder -and ($i.Name -eq 'Download' -or $i.Name -eq '下载')) { $dl = $i; break } }
if (-not $dl) { Write-Host "[X] 未找到 Download/下载 文件夹。" -ForegroundColor Red; Read-Host "回车退出"; exit 1 }
$dlFolder = $dl.GetFolder
Write-Host "[√] 目标: 内部存储 \ $($dl.Name)" -ForegroundColor Green

# 5) 先查 Download 里有没有这个 APK：有就先删，没有就直接复制
$existing = $null
foreach ($f in @($dlFolder.Items())) { if ($f.Name -eq $apk.Name) { $existing = $f; break } }
if ($existing) {
    Write-Host "    手机里已有同名文件，先删除旧的..." -ForegroundColor Cyan
    try { $existing.InvokeVerb("delete") } catch { }
    Start-Sleep -Seconds 1
} else {
    Write-Host "    手机里没有该文件，直接复制。" -ForegroundColor Cyan
}
Write-Host "    正在复制到手机（111MB，约 1~3 分钟），请勿拔线..."
$dlFolder.CopyHere($apk.FullName, 0x14)
$ok = $false
for ($s = 0; $s -lt 180; $s++) {
    Start-Sleep -Seconds 2
    foreach ($f in $dlFolder.Items()) { if ($f.Name -like "*$($apk.BaseName)*") { $ok = $true; break } }
    if ($ok) { break }
    if (($s % 5) -eq 0) { Write-Host ("    复制中... 已等待 {0} 秒" -f ($s * 2)) }
}
if ($ok) { Write-Host "[√] 已复制到手机【下载】：$($apk.Name)" -ForegroundColor Green }
else { Write-Host "[!] 超时未在【下载】看到文件。若有弹窗请点“是”；否则关掉手机相关资源管理器窗口、拔线重连再试。" -ForegroundColor Yellow }

# 6) 自动安装（需要手机开启「USB 调试」；否则跳过，提示手动点安装）
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
if (Test-Path $adb) {
    $devs = (& $adb devices) -join "`n"
    if ($devs -match '\tdevice\r?$') {
        Write-Host "    检测到 USB 调试已开，正在自动安装..." -ForegroundColor Cyan
        & $adb install -r $apk.FullName
        if ($LASTEXITCODE -eq 0) { Write-Host "[√] 安装成功！" -ForegroundColor Green }
        else { Write-Host "[!] 自动安装失败，请在手机上确认【允许通过 USB 安装】后重试，或手动点【下载】里的 APK 安装。" -ForegroundColor Yellow }
    } else {
        Write-Host "[i] 未开启/未授权 USB 调试，已跳过自动安装。" -ForegroundColor Yellow
        Write-Host "    请到手机【下载】点击 $($apk.Name) 手动安装；或开启 USB 调试后重跑本脚本可自动安装。" -ForegroundColor Cyan
    }
} else {
    Write-Host "[i] 请到手机【下载】点击 $($apk.Name) 安装。" -ForegroundColor Cyan
}
Read-Host "回车退出"

} catch {
    Write-Host ""
    Write-Host "[X] 脚本运行出错：" -ForegroundColor Red
    Write-Host ("    " + $_.Exception.Message) -ForegroundColor Red
    Write-Host ""
    Write-Host "请把这个黑窗口的内容截图发给我，我来帮你定位。" -ForegroundColor Yellow
    Read-Host "回车退出"
}
