[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$SourceDirectory,

    [switch]$PackageOnly
)

$expectedKitCommit = "3e223118e6e8fb6208693ecf3952e77cd096f587"
$sourcePath = (Resolve-Path -LiteralPath $SourceDirectory -ErrorAction Stop).Path
$kitScript = Join-Path $sourcePath "nix-android.sh"
if (-not (Test-Path -LiteralPath $kitScript -PathType Leaf)) {
    throw "FFmpegKitNext source directory is missing nix-android.sh: $sourcePath"
}

$actualKitCommit = (& git -C $sourcePath rev-parse HEAD 2>$null).Trim()
if ($LASTEXITCODE -ne 0 -or $actualKitCommit -ne $expectedKitCommit) {
    throw "FFmpegKitNext commit must be $expectedKitCommit; found '$actualKitCommit'."
}

$patchPath = (Resolve-Path -LiteralPath (
        Join-Path $PSScriptRoot "..\third_party\ffmpeg-kit-next\clearcut-security.patch"
    ) -ErrorAction Stop).Path
function ConvertTo-WslPath([string]$value) {
    $shellValue = $value.Replace("'", "'\''")
    $translated = (& wsl.exe bash -lc "wslpath -a '$shellValue'").Trim()
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($translated)) {
        throw "Could not translate the Windows path into WSL: $value"
    }
    return $translated
}

$sourceWsl = ConvertTo-WslPath $sourcePath
$patchWsl = ConvertTo-WslPath $patchPath

function ConvertTo-WslShellLiteral([string]$value) {
    return "'" + $value.Replace("'", "'\''") + "'"
}

$sourceLiteral = ConvertTo-WslShellLiteral $sourceWsl
$patchLiteral = ConvertTo-WslShellLiteral $patchWsl
$skipFfmpeg = if ($PackageOnly) { " --skip-ffmpeg" } else { "" }
$command = @"
set -e
cd $sourceLiteral
git config core.autocrlf false
git ls-files -z | while IFS= read -r -d '' path; do
  case "`$path" in
    *.sh|*.nix|*.mk|*.cmake|*.m4|*.ac|*.in|*.gradle|*.properties|*.txt)
      sed -i 's/\r`$//' "`$path"
      ;;
  esac
done
if git apply --reverse --check $patchLiteral >/dev/null 2>&1; then
  :
elif git apply --check $patchLiteral; then
  git apply $patchLiteral
else
  echo "ClearCut FFmpeg security patch does not apply cleanly." >&2
  exit 2
fi
bash ./nix-android.sh -p android-r27d$skipFfmpeg --enable-libass --enable-libjxl --enable-android-media-codec --enable-android-zlib --enable-openh264 --jobs=8
"@

& wsl.exe bash -lc $command
if ($LASTEXITCODE -ne 0) {
    throw "FFmpegKitNext build failed with exit code $LASTEXITCODE."
}
