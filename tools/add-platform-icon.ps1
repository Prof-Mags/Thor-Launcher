<#
.SYNOPSIS
    Downscales an Icon Forge console render into a platform drawable.

.DESCRIPTION
    The bundled platform artwork in `core/ui` is 256px PNGs in `drawable-nodpi`,
    resized from the 1024px sources. This does that one step, so adding a system
    is "run this, add a line to PlatformIcons".

    nodpi rather than a density bucket because there is one copy at one size and
    it must not be pre-scaled by density: the grid draws it at whatever the cell
    is, which varies with the user's grid setting rather than with the screen.

    Alpha is preserved throughout -- these are console renders on transparency,
    and flattening one onto white puts a box round it on a dark grid.

.PARAMETER Slug
    Platform ids to add, as named in `BuiltInPlatforms`. The source file is
    "<Slug>-<Style>.png" and the output is "platform_<Slug>.png".

.EXAMPLE
    ./tools/add-platform-icon.ps1 -Slug amiga,msx
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string[]] $Slug,
    [string] $Source = "$env:USERPROFILE\Documents\Icon Forge",
    [string] $Style = 'console',
    [string] $Destination = (Join-Path $PSScriptRoot '..\core\ui\src\main\res\drawable-nodpi'),
    [int] $Size = 256
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

foreach ($name in $Slug) {
    $in = Join-Path $Source "$name-$Style.png"
    if (-not (Test-Path -LiteralPath $in)) { throw "No source image: $in" }

    # Not $source: PowerShell variables are case-insensitive, so that would
    # overwrite the $Source parameter and break the next iteration.
    $image = [System.Drawing.Image]::FromFile($in)
    try {
        $canvas = New-Object System.Drawing.Bitmap $Size, $Size,
            ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        try {
            $g = [System.Drawing.Graphics]::FromImage($canvas)
            try {
                $g.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
                $g.InterpolationMode =
                    [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
                $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
                $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
                $g.DrawImage($image, 0, 0, $Size, $Size)
            } finally { $g.Dispose() }

            $out = Join-Path $Destination "platform_$name.png"
            $canvas.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
            "{0,-16} {1}x{2} -> {3}x{4}  {5:N0} bytes" -f $name,
                $image.Width, $image.Height, $Size, $Size, (Get-Item $out).Length
        } finally { $canvas.Dispose() }
    } finally { $image.Dispose() }
}

