$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$mods = Split-Path $root
$game = Split-Path $mods
$jdk = Join-Path $game 'jdk-23+7/bin'
$out = Join-Path $root ('build/' + [guid]::NewGuid())
$classes = Join-Path $out 'classes'
$jarDir = Join-Path $root 'jars'
New-Item -ItemType Directory -Force -Path $classes,$jarDir | Out-Null
$cp = (Join-Path $game 'starsector-core/*') + ';' + (Join-Path $mods 'Console Commands/jars/lw_Console.jar')
$sources = @(Get-ChildItem (Join-Path $root 'src') -Recurse -Filter '*.java' | ForEach-Object FullName)
& (Join-Path $jdk 'javac.exe') --release 17 -encoding UTF-8 -cp $cp -d $classes $sources
if ($LASTEXITCODE) { throw 'Peerless Skills compilation failed' }
$staged = Join-Path $out 'PeerlessSkills.jar'
& (Join-Path $jdk 'jar.exe') "-J-Djava.io.tmpdir=$out" cf $staged -C $classes .
if ($LASTEXITCODE) { throw 'Peerless Skills packaging failed' }
$target = Join-Path $jarDir 'PeerlessSkills.jar'
if ([IO.File]::Exists($target)) {
    [IO.File]::Replace($staged, $target, (Join-Path $out 'previous.jar'))
} else {
    [IO.File]::Move($staged, $target)
}
Write-Host "BUILD OK -> $target"
