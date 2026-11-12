# Generates Android launcher icons (legacy + adaptive foreground) for Roam.
# Brand: near-black rounded square with a white "R".
Add-Type -AssemblyName System.Drawing

$res = 'E:\roam\composeApp\src\androidMain\res'
$densities = @(
    @{name='mdpi';    legacy=48;  fg=108},
    @{name='hdpi';    legacy=72;  fg=162},
    @{name='xhdpi';   legacy=96;  fg=216},
    @{name='xxhdpi';  legacy=144; fg=324},
    @{name='xxxhdpi'; legacy=192; fg=432}
)

function New-RoundedPath([int]$size, [single]$ratio) {
    $r = [single]($size * $ratio)
    $p = New-Object System.Drawing.Drawing2D.GraphicsPath
    $d = $r * 2
    $p.AddArc(0, 0, $d, $d, 180, 90)
    $p.AddArc($size - $d, 0, $d, $d, 270, 90)
    $p.AddArc($size - $d, $size - $d, $d, $d, 0, 90)
    $p.AddArc(0, $size - $d, $d, $d, 90, 90)
    $p.CloseFigure()
    return $p
}

function New-Canvas([int]$size) {
    $bmp = New-Object System.Drawing.Bitmap $size, $size
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
    $g.Clear([System.Drawing.Color]::Transparent)
    return @{ bmp = $bmp; g = $g }
}

function Draw-R($g, [int]$size, [single]$scale, [single]$centerShift) {
    $fontSize = [int]($size * $scale)
    $font = New-Object System.Drawing.Font('Segoe UI', $fontSize, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
    $fmt = New-Object System.Drawing.StringFormat
    $fmt.Alignment = [System.Drawing.StringAlignment]::Center
    $fmt.LineAlignment = [System.Drawing.StringAlignment]::Center
    $rect = New-Object System.Drawing.RectangleF 0, $centerShift, $size, $size
    $brush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::White)
    $g.DrawString('R', $font, $brush, $rect, $fmt)
}

foreach ($d in $densities) {
    $dir = Join-Path $res ("mipmap-" + $d.name)
    New-Item -ItemType Directory -Force -Path $dir | Out-Null

    # legacy square icon (API < 26)
    $c = New-Canvas $d.legacy
    $path = New-RoundedPath $d.legacy 0.22
    $bg = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 15, 17, 21))
    $c.g.FillPath($bg, $path)
    Draw-R $c.g $d.legacy 0.56 0
    $c.bmp.Save((Join-Path $dir 'ic_launcher.png'), [System.Drawing.Imaging.ImageFormat]::Png)
    $c.g.Dispose(); $c.bmp.Dispose()

    # round icon：圆形底，避免 lint IconLauncherShape
    $rnd = New-Canvas $d.legacy
    $circle = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 15, 17, 21))
    $rnd.g.FillEllipse($circle, 0, 0, $d.legacy, $d.legacy)
    Draw-R $rnd.g $d.legacy 0.56 0
    $rnd.bmp.Save((Join-Path $dir 'ic_launcher_round.png'), [System.Drawing.Imaging.ImageFormat]::Png)
    $rnd.g.Dispose(); $rnd.bmp.Dispose()

    # adaptive foreground: transparent, R inside the 72/108 safe zone
    $f = New-Canvas $d.fg
    Draw-R $f.g $d.fg 0.44 0
    $f.bmp.Save((Join-Path $dir 'ic_launcher_foreground.png'), [System.Drawing.Imaging.ImageFormat]::Png)
    $f.g.Dispose(); $f.bmp.Dispose()
}

# minSdk = 26，所以不需要 -v26 限定符
$anydpi = Join-Path $res 'mipmap-anydpi'
New-Item -ItemType Directory -Force -Path $anydpi | Out-Null
$adaptive = @'
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
    <monochrome android:drawable="@mipmap/ic_launcher_foreground" />
</adaptive-icon>
'@
Set-Content -Path (Join-Path $anydpi 'ic_launcher.xml') -Value $adaptive -Encoding UTF8
Set-Content -Path (Join-Path $anydpi 'ic_launcher_round.xml') -Value $adaptive -Encoding UTF8

$v26 = Join-Path $res 'mipmap-anydpi-v26'
if (Test-Path $v26) { Remove-Item $v26 -Recurse -Force }

$values = Join-Path $res 'values'
New-Item -ItemType Directory -Force -Path $values | Out-Null
@'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">#0F1115</color>
</resources>
'@ | Set-Content -Path (Join-Path $values 'colors.xml') -Encoding UTF8

Get-ChildItem $res -Recurse -File | Select-Object @{n='KB';e={[math]::Round($_.Length/1KB,1)}},FullName
