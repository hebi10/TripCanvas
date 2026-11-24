param(
    [int]$ExpectedVersionCode = 15,
    [string]$OutputPath = ".\tripcanvas-release-a07-device-check.json"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    throw "ADB platform-tools를 찾지 못했습니다. Android Studio 없이 platform-tools만 설치해도 됩니다."
}

$state = (& adb get-state 2>&1 | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $state -ne "device") {
    throw "USB 디버깅을 허용한 Android 기기 한 대만 연결해야 합니다. 기기 ID는 보고서에 기록하지 않습니다."
}

$packageName = "com.hebi.tripcanvas"
$packageInfo = (& adb shell dumpsys package $packageName 2>&1 | Out-String)
if ($LASTEXITCODE -ne 0 -or $packageInfo -notmatch "Package \[$packageName\]") {
    throw "TripCanvas 앱 설치를 확인할 수 없습니다."
}

$versionMatch = [regex]::Match($packageInfo, "versionCode=(\d+)")
if (-not $versionMatch.Success) {
    throw "설치된 TripCanvas의 versionCode를 확인하지 못했습니다."
}

$versionCode = [int]$versionMatch.Groups[1].Value
$nameMatch = [regex]::Match($packageInfo, "versionName=([^\s]+)")
$versionName = if ($nameMatch.Success) { $nameMatch.Groups[1].Value } else { "unknown" }

$installerRaw = (& adb shell pm list packages -i $packageName 2>&1 | Out-String)
$installerMatch = [regex]::Match($installerRaw, "installer=(\S+)")
$installer = if ($installerMatch.Success) { $installerMatch.Groups[1].Value } else { "unknown" }
$sdk = (& adb shell getprop ro.build.version.sdk | Out-String).Trim()
$model = (& adb shell getprop ro.product.model | Out-String).Trim()

$versionMatches = ($versionCode -eq $ExpectedVersionCode)
$playInstallerReported = ($installer -eq "com.android.vending")
$result = if ($versionMatches -and $playInstallerReported) {
    "METADATA_MATCH"
} elseif (-not $versionMatches) {
    "VERSION_MISMATCH"
} else {
    "INSTALL_SOURCE_NOT_VERIFIED"
}

$report = [ordered]@{
    appPackage = $packageName
    observedAtLocal = (Get-Date).ToString("o")
    deviceModel = $model
    androidSdk = $sdk
    expectedVersionCode = $ExpectedVersionCode
    installedVersionCode = $versionCode
    installedVersionName = $versionName
    installerPackage = $installer
    versionCodeMatches = $versionMatches
    playInstallerReported = $playInstallerReported
    result = $result
    disclaimer = "설치 메타데이터 조회만 수행. Google Play 테스터 승인, 실제 업데이트 경로, 폴더블, TalkBack, SAF 권한 검증과는 별도."
}

$fullPath = [System.IO.Path]::GetFullPath($OutputPath)
$directory = [System.IO.Path]::GetDirectoryName($fullPath)
[System.IO.Directory]::CreateDirectory($directory) | Out-Null
$json = $report | ConvertTo-Json -Depth 4
[System.IO.File]::WriteAllText($fullPath, $json + [Environment]::NewLine, (New-Object System.Text.UTF8Encoding($false)))
Write-Output ("기기 설치 메타데이터 점검: " + $result)
Write-Output ("결과 파일: " + $fullPath)
