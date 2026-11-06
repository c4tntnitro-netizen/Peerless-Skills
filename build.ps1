$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$mods = Split-Path $root
$game = Split-Path $mods
$jdk = Join-Path $game 'jdk-23+7/bin'
$out = Join-Path $root ('build/' + [guid]::NewGuid())
$classes = Join-Path $out 'classes'
$jarDir = Join-Path $root 'jars'
New-Item -ItemType Directory -Force -Path $classes,$jarDir | Out-Null
$cp = (Join-Path $game 'starsector-core/*') + ';' + (Join-Path $mods 'Console Commands/jars/lw_Console.jar') + ';' + (Join-Path $mods 'SecondInCommand/jars/SecondInCommand.jar') + ';' + (Join-Path $mods 'LunaLib/jars/LunaLib.jar')
$sources = @(Get-ChildItem (Join-Path $root 'src') -Recurse -Filter '*.java' | ForEach-Object FullName)
& (Join-Path $jdk 'javac.exe') --release 17 -encoding UTF-8 -cp $cp -d $classes $sources
if ($LASTEXITCODE) { throw 'Peerless Skills compilation failed' }
$staged = Join-Path $out 'PeerlessSkills.jar'
& (Join-Path $jdk 'jar.exe') "-J-Djava.io.tmpdir=$out" cf $staged -C $classes .
if ($LASTEXITCODE) { throw 'Peerless Skills packaging failed' }
$infoPath = Join-Path $root 'mod_info.json'
$info = Get-Content -Raw -LiteralPath $infoPath | ConvertFrom-Json
$current = [IO.Path]::GetFileName($info.jars[0])
$candidates = @($current, 'PeerlessSkills.jar', 'PeerlessSkills.next.jar', 'PeerlessSkills.next2.jar') | Select-Object -Unique
$target = $null
foreach ($candidate in $candidates) {
    $path = Join-Path $jarDir $candidate
    try {
        if ([IO.File]::Exists($path)) { [IO.File]::Replace($staged, $path, (Join-Path $out 'previous.jar')) }
        else { [IO.File]::Move($staged, $path) }
        $target = $path
        break
    } catch {
        if ($_.Exception -isnot [IO.IOException] -and $_.Exception.InnerException -isnot [IO.IOException]) { throw }
    }
}
if (!$target) { throw 'All Peerless runtime JAR slots are locked; compiled JAR remains in the build directory.' }
$info.jars = @('jars/' + [IO.Path]::GetFileName($target))
$info | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $infoPath -Encoding utf8
Write-Host "BUILD OK -> $target"
