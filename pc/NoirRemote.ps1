<#
Noir Remote for Windows: finds Noir on your Wi-Fi and opens its remote in its own window.
If Noir isn't running on the TV (say the TV fell asleep), it wakes the TV and starts Noir over ADB first.

  powershell -ExecutionPolicy Bypass -File pc\NoirRemote.ps1            open the remote
  powershell -ExecutionPolicy Bypass -File pc\NoirRemote.ps1 -Install   add "Noir Remote" to the Desktop and Start menu

The TV and PC must be on the same network. Starting Noir by itself needs ADB debugging on the Fire TV
(the same setting used to install the app) and adb from the Android SDK when you run -Install.
#>
param([switch]$Install)
$dir = Join-Path $env:LOCALAPPDATA 'NoirRemote'
$port = 8686
New-Item -ItemType Directory -Force $dir | Out-Null
Add-Type -AssemblyName System.Windows.Forms, System.Drawing

if ($Install) {
    Copy-Item $PSCommandPath "$dir\NoirRemote.ps1" -Force

    # Windows icon: ICO file holding PNGs of the app logo at the sizes Explorer asks for
    $src = [Drawing.Image]::FromFile((Resolve-Path "$PSScriptRoot\..\app\src\main\res\drawable-nodpi\ic_launcher.png"))
    $pngs = foreach ($size in 16, 32, 48, 256) {
        $m = New-Object IO.MemoryStream
        (New-Object Drawing.Bitmap $src, $size, $size).Save($m, [Drawing.Imaging.ImageFormat]::Png)
        , @($size, $m.ToArray())
    }
    $src.Dispose()
    $ico = New-Object IO.MemoryStream
    $w = New-Object IO.BinaryWriter $ico
    $w.Write([uint16]0); $w.Write([uint16]1); $w.Write([uint16]$pngs.Count)          # header: icon file, N images
    $offset = 6 + 16 * $pngs.Count
    foreach ($p in $pngs) {
        $side = if ($p[0] -eq 256) { 0 } else { $p[0] }                               # 256 is stored as 0
        $w.Write([byte]$side); $w.Write([byte]$side); $w.Write([byte]0); $w.Write([byte]0)
        $w.Write([uint16]1); $w.Write([uint16]32); $w.Write([uint32]$p[1].Length); $w.Write([uint32]$offset)
        $offset += $p[1].Length
    }
    foreach ($p in $pngs) { $w.Write($p[1]) }
    [IO.File]::WriteAllBytes("$dir\noir.ico", $ico.ToArray())

    # A private copy of adb, so the remote can start Noir on the TV even if the SDK moves
    $pt = @((Get-Command adb -ErrorAction SilentlyContinue).Source, "$env:ANDROID_HOME\platform-tools\adb.exe",
        "$env:ANDROID_SDK_ROOT\platform-tools\adb.exe", "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
        "$PSScriptRoot\..\..\toolchain\sdk\platform-tools\adb.exe") | Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1
    if ($pt) {
        New-Item -ItemType Directory -Force "$dir\adb" | Out-Null
        Copy-Item (Join-Path (Split-Path $pt) '*') "$dir\adb" -Include adb.exe, AdbWinApi.dll, AdbWinUsbApi.dll -Force
    }

    $shell = New-Object -ComObject WScript.Shell
    foreach ($folder in [Environment]::GetFolderPath('Desktop'), [Environment]::GetFolderPath('Programs')) {
        $l = $shell.CreateShortcut("$folder\Noir Remote.lnk")
        $l.TargetPath = 'powershell.exe'
        $l.Arguments = "-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File `"$dir\NoirRemote.ps1`""
        $l.IconLocation = "$dir\noir.ico"
        $l.Description = 'Control Noir on your Fire TV'
        $l.Save()
    }
    'Installed. Open "Noir Remote" from your Desktop or Start menu.' + $(if (-not $pt) { ' (adb not found: it can''t start Noir on the TV by itself)' })
    return
}

# Show a window right away: the launcher runs hidden, so without this a double-click looks like it did nothing
$splash = New-Object Windows.Forms.Form -Property @{
    Text = 'Noir Remote'; Width = 380; Height = 130; StartPosition = 'CenterScreen'; TopMost = $true
    FormBorderStyle = 'FixedSingle'; MaximizeBox = $false; MinimizeBox = $false
    BackColor = [Drawing.Color]::FromArgb(18, 16, 25); ForeColor = [Drawing.Color]::White
}
try { $splash.Icon = [Drawing.Icon]::ExtractAssociatedIcon("$dir\noir.ico") } catch {}
$label = New-Object Windows.Forms.Label -Property @{ Dock = 'Fill'; TextAlign = 'MiddleCenter'; Font = New-Object Drawing.Font('Segoe UI', 11) }
$splash.Controls.Add($label)
$splash.Show()
function Say($text) { $label.Text = $text; [Windows.Forms.Application]::DoEvents() }
function Fail($text) {
    [Windows.Forms.MessageBox]::Show($splash, $text, 'Noir Remote', 'OK', 'Warning') | Out-Null
    exit 1
}
Say 'Connecting to your Fire TV…'

# The TV is on the local network: skip Windows' proxy auto-detection, which alone can take over 2 s
[Net.WebRequest]::DefaultWebProxy = $null
function Test-Noir($ip) { try { (Invoke-RestMethod "http://${ip}:$port/hello" -TimeoutSec 3).app -eq 'noir' } catch { $false } }
function Test-Port($ip, $p) { $c = New-Object Net.Sockets.TcpClient; try { $c.ConnectAsync($ip, $p).Wait(800) -and $c.Connected } catch { $false } finally { $c.Dispose() } }

# Noir's remote only runs while Noir is open or playing; wake the TV and start it over ADB
function Start-NoirOnTv($ip) {
    $adb = "$dir\adb\adb.exe"
    if (-not (Test-Path $adb) -or -not (Test-Port $ip 5555)) { return $false }
    Say 'Starting Noir on your Fire TV…'
    & $adb connect "${ip}:5555" | Out-Null
    & $adb -s "${ip}:5555" shell 'input keyevent KEYCODE_WAKEUP; am start -n com.noir.music/.MainActivity' | Out-Null
    foreach ($i in 1..20) { if (Test-Noir $ip) { return $true }; Start-Sleep -Milliseconds 500; [Windows.Forms.Application]::DoEvents() }
    $false
}

# Scan this PC's networks for something answering on Noir's port
function Find-Noir {
    Say 'Looking for Noir on your Wi-Fi…'
    $nets = Get-NetIPAddress -AddressFamily IPv4 |
        Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*' } |
        ForEach-Object { $_.IPAddress -replace '\.\d+$', '' } | Select-Object -Unique
    $probes = foreach ($n in $nets) {
        foreach ($h in 1..254) { $c = New-Object Net.Sockets.TcpClient; [pscustomobject]@{ ip = "$n.$h"; c = $c; t = $c.ConnectAsync("$n.$h", $port) } }
    }
    Start-Sleep -Milliseconds 1500
    $open = $probes | Where-Object { $_.t.Status -eq 'RanToCompletion' } | ForEach-Object { $_.ip }
    $probes | ForEach-Object { $_.c.Dispose() }
    $open | Where-Object { Test-Noir $_ } | Select-Object -First 1
}

# The TV we used last time; if Noir isn't answering there, start it, else look around the network
$saved = "$dir\tv.txt"
$last = if (Test-Path $saved) { (Get-Content $saved).Trim() }
$ip = if ($last -and (Test-Noir $last)) { $last }
if (-not $ip -and $last -and (Start-NoirOnTv $last)) { $ip = $last }
if (-not $ip) { $ip = Find-Noir }
if (-not $ip) {
    Fail ("Couldn't reach Noir on your Fire TV.`n`n" +
        "- Check the TV is on and on the same Wi-Fi as this PC.`n" +
        "- Open Noir on the TV, then try again.`n`n" +
        "Tip: with ADB debugging on (Fire TV Settings > My Fire TV > Developer options), Noir Remote starts Noir for you.")
}

Set-Content $saved $ip
$url = "http://${ip}:$port/"
try { Start-Process msedge "--app=$url --window-size=1120,740" -ErrorAction Stop } catch { Start-Process $url }
Start-Sleep -Milliseconds 800 # let the remote window appear before the splash goes
$splash.Close()
