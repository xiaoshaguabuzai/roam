# Roam 本机便携工具链（幂等）
# 用法：在仓库根目录执行  .\.tools\setup-toolchain.ps1
# 说明：本机没有系统级 JDK / Node / Gradle，全部放在 .tools/ 下（已 gitignore）。

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot          # E:\roam
$tools = Join-Path $repo '.tools'
$downloads = Join-Path $tools 'downloads'
New-Item -ItemType Directory -Force -Path $downloads | Out-Null

function Get-Portable($url, $zipName, $targetDir, $probeFile) {
    $zip = Join-Path $downloads $zipName
    if (Test-Path (Join-Path $targetDir $probeFile)) {
        Write-Host "[skip] $targetDir 已存在"
        return
    }
    if (-not (Test-Path $zip)) {
        Write-Host "[get ] $url"
        curl.exe -s -L --retry 3 --max-time 1800 -o $zip $url
        if ($LASTEXITCODE -ne 0) { throw "下载失败: $url" }
    }
    $tmp = Join-Path $tools '_extract'
    if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
    New-Item -ItemType Directory -Force -Path $tmp | Out-Null
    tar.exe -xf $zip -C $tmp
    $inner = Get-ChildItem $tmp -Directory | Select-Object -First 1
    if (-not $inner) { throw "解压后没有目录: $zip" }
    if (Test-Path $targetDir) { Remove-Item $targetDir -Recurse -Force }
    Move-Item $inner.FullName $targetDir
    Remove-Item $tmp -Recurse -Force
    Write-Host "[ok  ] $targetDir"
}

Get-Portable 'https://mirrors.tuna.tsinghua.edu.cn/Adoptium/21/jdk/x64/windows/OpenJDK21U-jdk_x64_windows_hotspot_21.0.13_9.zip' `
    'jdk21.zip' (Join-Path $tools 'jdk') 'bin\java.exe'

Get-Portable 'https://npmmirror.com/mirrors/node/v22.23.5/node-v22.23.5-win-x64.zip' `
    'node22.zip' (Join-Path $tools 'node') 'node.exe'

Get-Portable 'https://mirrors.cloud.tencent.com/gradle/gradle-8.14-bin.zip' `
    'gradle.zip' (Join-Path $tools 'gradle') 'bin\gradle.bat'

# 生成 env.ps1，供其他脚本 dot-source
$jdk = Join-Path $tools 'jdk'
$node = Join-Path $tools 'node'
$gradle = Join-Path $tools 'gradle'
$sdk = 'C:\Users\Administrator\AppData\Local\Android\Sdk'

@"
# 由 setup-toolchain.ps1 生成，勿手改
`$env:JAVA_HOME = '$jdk'
`$env:ANDROID_HOME = '$sdk'
`$env:ANDROID_SDK_ROOT = '$sdk'
`$env:GRADLE_USER_HOME = Join-Path 'E:\roam\.tools' 'gradle-home'
`$env:PATH = "$jdk\bin;$node;$gradle\bin;`$env:PATH"
`$env:PLAYWRIGHT_BROWSERS_PATH = Join-Path 'E:\roam\.tools' 'ms-playwright'
"@ | Set-Content -Encoding UTF8 (Join-Path $tools 'env.ps1')

Write-Host '[done] 生成 .tools\env.ps1'
& (Join-Path $tools 'env.ps1')
# 注意：java -version / gradle --version 会往 stderr 写，$ErrorActionPreference='Stop' 下会中断，
# 所以用 cmd /c 合并输出后再解析。
$javaLine = (cmd /c "`"$jdk\bin\java.exe`" -version 2>&1" | Select-Object -First 1)
$nodeLine = (cmd /c "`"$node\node.exe`" --version 2>&1" | Select-Object -First 1)
$gradleLine = (cmd /c "`"$gradle\bin\gradle.bat`" --version 2>&1" | Select-String 'Gradle ' | Select-Object -First 1)
Write-Host ('java  : ' + $javaLine)
Write-Host ('node  : ' + $nodeLine)
Write-Host ('gradle: ' + $gradleLine)

exit 0
