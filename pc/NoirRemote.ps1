<#
Noir Remote for Windows: finds Noir on your Wi-Fi and opens its remote in its own window.

  powershell -ExecutionPolicy Bypass -File pc\NoirRemote.ps1            open the remote
  powershell -ExecutionPolicy Bypass -File pc\NoirRemote.ps1 -Install   add "Noir Remote" to the Desktop and Start menu

Noir must be open (or playing) on the Fire TV, and the TV and PC must be on the same network.
#>
param([switch]$Install)
$dir = Join-Path $env:LOCALAPPDATA 'NoirRemote'
$port = 8686
New-Item -ItemType Directory -Force $dir | Out-Null

if ($Install) {
    Copy-Item $PSCommandPath "$dir\NoirRemote.ps1" -Force
    # Windows icon = ICO file wrapping a 256 px PNG of the app logo
    Add-Type -AssemblyName System.Drawing
    $src = [Drawing.Image]::FromFile((Resolve-Path "$PSScriptRoot\..\app\src\main\res\drawable-nodpi\ic_launcher.png"))
    $png = New-Object IO.MemoryStream
    (New-Object Drawing.Bitmap $src, 256, 256).Save($png, [Drawing.Imaging.ImageFormat]::Png)
    $src.Dispose()
    $ico = New-Object IO.MemoryStream
    $w = New-Object IO.BinaryWriter $ico
    $w.Write([uint16]0); $w.Write([uint16]1); $w.Write([uint16]1)                  # header: icon file with 1 image
    $w.Write([byte]0); $w.Write([byte]0); $w.Write([byte]0); $w.Write([byte]0)     # 256x256 (stored as 0), no palette
    $w.Write([uint16]1); $w.Write([uint16]32); $w.Write([uint32]$png.Length); $w.Write([uint32]22)
    $w.Write($png.ToArray())
    [IO.File]::WriteAllBytes("$dir\noir.ico", $ico.ToArray())

    $shell = New-Object -ComObject WScript.Shell
    foreach ($folder in [Environment]::GetFolderPath('Desktop'), [Environment]::GetFolderPath('Programs')) {
        $l = $shell.CreateShortcut("$folder\Noir Remote.lnk")
        $l.TargetPath = 'powershell.exe'
        $l.Arguments = "-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File `"$dir\NoirRemote.ps1`""
        $l.IconLocation = "$dir\noir.ico"
        $l.Description = 'Control Noir on your Fire TV'
        $l.Save()
    }
    'Installed. Open "Noir Remote" from your Desktop or Start menu.'
    return
}

# The TV is on the local network: skip Windows' proxy auto-detection, which alone can take over 2 s
[Net.WebRequest]::DefaultWebProxy = $null
function Test-Noir($ip) { try { (Invoke-RestMethod "http://${ip}:$port/hello" -TimeoutSec 3).app -eq 'noir' } catch { $false } }

# Try the TV we found last time, else scan this PC's networks for something answering on Noir's port
$saved = "$dir\tv.txt"
$ip = if (Test-Path $saved) { Get-Content $saved }
if (-not ($ip -and (Test-Noir $ip))) {
    $nets = Get-NetIPAddress -AddressFamily IPv4 |
        Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*' } |
        ForEach-Object { $_.IPAddress -replace '\.\d+$', '' } | Select-Object -Unique
    $probes = foreach ($n in $nets) {
        foreach ($h in 1..254) { $c = New-Object Net.Sockets.TcpClient; [pscustomobject]@{ ip = "$n.$h"; c = $c; t = $c.ConnectAsync("$n.$h", $port) } }
    }
    Start-Sleep -Milliseconds 1500
    $open = $probes | Where-Object { $_.t.Status -eq 'RanToCompletion' } | ForEach-Object { $_.ip }
    $probes | ForEach-Object { $_.c.Dispose() }
    $ip = $open | Where-Object { Test-Noir $_ } | Select-Object -First 1
}

if (-not $ip) {
    Add-Type -AssemblyName PresentationFramework
    [Windows.MessageBox]::Show("Couldn't find Noir on your network.`n`nOpen Noir on your Fire TV (same Wi-Fi as this PC) and try again.", 'Noir Remote') | Out-Null
    exit 1
}
Set-Content $saved $ip
$url = "http://${ip}:$port/"
try { Start-Process msedge "--app=$url --window-size=1120,740" -ErrorAction Stop } catch { Start-Process $url }
