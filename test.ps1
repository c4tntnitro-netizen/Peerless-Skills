$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    $jdk = '../../jdk-23+7/bin'
    $runtimeJar = (Get-Content -Raw mod_info.json | ConvertFrom-Json).jars[0]
    $cp = '../../starsector-core/*;' + $runtimeJar + ';../Console Commands/jars/lw_Console.jar;../LazyLib/jars/internal/Kotlin-Runtime.jar;../LazyLib/jars/LazyLib.jar;../SecondInCommand/jars/SecondInCommand.jar;../LunaLib/jars/LunaLib.jar'
    $out = Join-Path 'build' ('tests-' + [guid]::NewGuid())
    New-Item -ItemType Directory -Force $out | Out-Null
    $sources = @(Get-ChildItem tools -Filter '*.java' | ForEach-Object FullName)
    & "$jdk/javac.exe" --release 17 -cp $cp -d $out $sources
    if ($LASTEXITCODE) { throw 'Test compilation failed' }
    foreach ($test in @('PeerlessRewardsRegression','PeerlessPointNotificationRegression','PeerlessProgressionRegression','PeerlessHelmsmanshipRegression','PeerlessCombatEnduranceRegression','PeerlessFieldModulationRegression','PrototypeRegression','ArmorDecayRegression','PeerlessCombatStatusRegression','PlayerContactRegression','StoryRoutesRegression','PeerlessConsoleRegression','SkillDescriptionRegression','PeerlessSkillIconRegression','MissileReloadHudRegression')) {
        & "$jdk/java.exe" '-Xverify:none' '-Dlog4j.defaultInitOverride=true' '-Djava.library.path=../../starsector-core/native/windows' -cp ($out+';'+$cp) "peerless.skills.$test"
        if ($LASTEXITCODE) { throw "$test failed" }
    }
} finally { Pop-Location }
