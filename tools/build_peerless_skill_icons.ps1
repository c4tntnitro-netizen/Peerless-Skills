param([switch]$PreviewOnly)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$modRoot = Split-Path $PSScriptRoot -Parent
$coreRoot = Join-Path (Split-Path (Split-Path $modRoot -Parent) -Parent) 'starsector-core'
$catalog = Get-Content -LiteralPath (Join-Path $modRoot 'docs/PEERLESS_SKILL_ICONS.json') -Raw | ConvertFrom-Json

# These are authored bitmap variants. This step only scales them for the game.
foreach ($asset in $catalog.assets) {
    $target = Join-Path $modRoot $asset.runtime
    if ($PreviewOnly -or $asset.preserveExisting) {
        if (-not (Test-Path -LiteralPath $target)) { throw "Missing preserved runtime icon: $target" }
        continue
    }
    $master = [System.Drawing.Bitmap]::new((Join-Path $modRoot $asset.master))
    $output = [System.Drawing.Bitmap]::new(64, 64, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($output)
    $attributes = [System.Drawing.Imaging.ImageAttributes]::new()
    try {
        if ($master.Width -ne $master.Height) { throw "Master is not square: $($asset.id)" }
        $g.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
        $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        $attributes.SetWrapMode([System.Drawing.Drawing2D.WrapMode]::TileFlipXY)
        $g.DrawImage($master, [System.Drawing.Rectangle]::new(0, 0, 64, 64), 0, 0, $master.Width, $master.Height, [System.Drawing.GraphicsUnit]::Pixel, $attributes)
        $output.Save($target, [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $attributes.Dispose(); $g.Dispose(); $output.Dispose(); $master.Dispose()
    }
}

# A compact before/after sheet: each pair is shown at its actual 64px game size.
$sheet = [System.Drawing.Bitmap]::new(1000, 656)
$g = [System.Drawing.Graphics]::FromImage($sheet)
$titleFont = [System.Drawing.Font]::new('Segoe UI', 19, [System.Drawing.FontStyle]::Bold)
$nameFont = [System.Drawing.Font]::new('Segoe UI', 11)
$smallFont = [System.Drawing.Font]::new('Segoe UI', 9)
$white = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(231, 235, 239))
$muted = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(150, 159, 171))
$gold = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(246, 211, 110))
$bronze = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(220, 149, 93))
try {
    $g.Clear([System.Drawing.Color]::FromArgb(15, 20, 27))
    $g.DrawString('PEERLESS / ELITE ICON VARIANTS', $titleFont, $white, 24, 15)
    $g.DrawString('12 gold / 2 bronze   |   Original left, Peerless right   |   Actual 64px size', $smallFont, $muted, 26, 52)
    for ($i = 0; $i -lt $catalog.assets.Count; $i++) {
        $asset = $catalog.assets[$i]
        $x = 24 + ($i % 4) * 246
        $y = 92 + [Math]::Floor($i / 4) * 139
        $brush = if ($asset.metal -eq 'bronze') { $bronze } else { $gold }
        $g.DrawString($asset.name, $nameFont, $white, $x, $y)
        $g.DrawString("$($asset.branch) / $($asset.metal)", $smallFont, $brush, $x, ($y + 22))
        $original = [System.Drawing.Bitmap]::new((Join-Path $coreRoot $asset.source))
        $variant = [System.Drawing.Bitmap]::new((Join-Path $modRoot $asset.runtime))
        try {
            if ($variant.Width -ne 64 -or $variant.Height -ne 64) { throw "Wrong runtime dimensions: $($asset.id)" }
            $g.DrawImage($original, [System.Drawing.Rectangle]::new($x, ($y + 46), 64, 64), 0, 0, $original.Width, $original.Height, [System.Drawing.GraphicsUnit]::Pixel)
            $g.DrawImage($variant, [System.Drawing.Rectangle]::new(($x + 84), ($y + 46), 64, 64), 0, 0, 64, 64, [System.Drawing.GraphicsUnit]::Pixel)
        } finally { $original.Dispose(); $variant.Dispose() }
    }
    $preview = Join-Path $modRoot 'docs/assets/peerless_skill_icons_v1.png'
    New-Item -ItemType Directory -Force (Split-Path $preview -Parent) | Out-Null
    $sheet.Save($preview, [System.Drawing.Imaging.ImageFormat]::Png)
    Write-Output "Prepared $($catalog.assets.Count) icons; preview: $preview"
} finally {
    $g.Dispose(); $sheet.Dispose(); $titleFont.Dispose(); $nameFont.Dispose(); $smallFont.Dispose()
    $white.Dispose(); $muted.Dispose(); $gold.Dispose(); $bronze.Dispose()
}
