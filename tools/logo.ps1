# Draws Noir's icon (512x512) and Fire TV banner (640x360) as PNGs. Usage: logo.ps1 <outDir>
param([string]$out)
Add-Type -AssemblyName System.Drawing
$D = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias

function C($hex, $a = 255) { $c = [System.Drawing.ColorTranslator]::FromHtml($hex); [System.Drawing.Color]::FromArgb($a, $c) }

function Glow($g, $cx, $cy, $rx, $ry, $hex, $alpha) {
    $p = New-Object System.Drawing.Drawing2D.GraphicsPath
    $p.AddEllipse($cx - $rx, $cy - $ry, 2 * $rx, 2 * $ry)
    $b = New-Object System.Drawing.Drawing2D.PathGradientBrush($p)
    $b.CenterColor = C $hex $alpha
    $b.SurroundColors = @((C $hex 0))
    $g.FillPath($b, $p)
}

# Ring with a violet->pink gradient and five equalizer bars inside, centred on (cx, cy) with ring radius r
function Emblem($g, $cx, $cy, $r) {
    $rect = New-Object System.Drawing.RectangleF(($cx - $r), ($cy - $r), (2 * $r), (2 * $r))
    $ringBrush = New-Object System.Drawing.Drawing2D.LinearGradientBrush($rect, (C '#B48CFF'), (C '#FF5FA2'), 45.0)
    $ring = New-Object System.Drawing.Pen($ringBrush, [single]($r * 0.12))
    $g.DrawEllipse($ring, $rect)

    $heights = 0.55, 0.95, 1.3, 0.95, 0.55
    $w = $r * 0.15; $gap = $r * 0.11
    $x0 = $cx - (2 * ($w + $gap))
    $barRect = New-Object System.Drawing.RectangleF(($cx - $r), ($cy - $r - 1), (2 * $r), (2 * $r + 2))
    $barBrush = New-Object System.Drawing.Drawing2D.LinearGradientBrush($barRect, (C '#FFFFFF'), (C '#C9B3FF'), 90.0)
    $bar = New-Object System.Drawing.Pen($barBrush, [single]$w)
    $bar.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
    $bar.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
    for ($i = 0; $i -lt 5; $i++) {
        $x = $x0 + $i * ($w + $gap)
        $h = $heights[$i] * $r * 0.5
        $g.DrawLine($bar, [single]$x, [single]($cy - $h), [single]$x, [single]($cy + $h))
    }
}

function Spaced($g, $text, $font, $brush, $x, $y, $spacing) {
    foreach ($ch in $text.ToCharArray()) {
        $s = [string]$ch
        $g.DrawString($s, $font, $brush, [single]$x, [single]$y, [System.Drawing.StringFormat]::GenericTypographic)
        $x += $g.MeasureString($s, $font, 1000, [System.Drawing.StringFormat]::GenericTypographic).Width + $spacing
    }
}

# --- Icon ---
$bmp = New-Object System.Drawing.Bitmap 512, 512
$g = [System.Drawing.Graphics]::FromImage($bmp); $g.SmoothingMode = $D; $g.Clear([System.Drawing.Color]::Transparent)
$path = New-Object System.Drawing.Drawing2D.GraphicsPath
$rr = 220; $path.AddArc(0, 0, $rr, $rr, 180, 90); $path.AddArc(512 - $rr, 0, $rr, $rr, 270, 90)
$path.AddArc(512 - $rr, 512 - $rr, $rr, $rr, 0, 90); $path.AddArc(0, 512 - $rr, $rr, $rr, 90, 90); $path.CloseFigure()
$bg = New-Object System.Drawing.Drawing2D.LinearGradientBrush((New-Object System.Drawing.Rectangle 0, 0, 512, 512), (C '#2A1060'), (C '#07040F'), 60.0)
$g.FillPath($bg, $path)
$g.SetClip($path)
Glow $g 256 230 300 260 '#7A4DFF' 150
Glow $g 400 420 220 180 '#FF5FA2' 60
Emblem $g 256 256 150
$bmp.Save("$out\ic_launcher.png", [System.Drawing.Imaging.ImageFormat]::Png)

# --- Banner ---
$bmp = New-Object System.Drawing.Bitmap 640, 360
$g = [System.Drawing.Graphics]::FromImage($bmp); $g.SmoothingMode = $D
$g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
$bg = New-Object System.Drawing.Drawing2D.LinearGradientBrush((New-Object System.Drawing.Rectangle 0, 0, 640, 360), (C '#1C0B40'), (C '#050308'), 0.0)
$g.FillRectangle($bg, 0, 0, 640, 360)
Glow $g 165 180 260 230 '#7A4DFF' 160
Glow $g 560 330 260 150 '#FF5FA2' 45
Emblem $g 165 180 100
$white = New-Object System.Drawing.SolidBrush (C '#FFFFFF')
$violet = New-Object System.Drawing.SolidBrush (C '#C9A8FF')
Spaced $g 'NOIR' (New-Object System.Drawing.Font('Segoe UI Black', 92, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)) $white 305 92 6
Spaced $g 'MUSIC' (New-Object System.Drawing.Font('Segoe UI Semibold', 34, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)) $violet 311 206 17
$bmp.Save("$out\banner.png", [System.Drawing.Imaging.ImageFormat]::Png)
"done"
