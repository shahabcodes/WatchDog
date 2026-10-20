<#
Creates Watch Dog's private release signing key and the "rotation proof" that
lets phones accept it as an update from the old shared debug key.

Run it yourself, once:   powershell -ExecutionPolicy Bypass -File D:\FMS\tools\signing\create-release-key.ps1

It will:
  1. ask you for a password once (typed twice to confirm) and use it for every step
  2. create the release keystore in %USERPROFILE%\WatchDog-signing
  3. create keystore\signing-lineage.bin in the repo (public; the old key vouching for the new one)
  4. print the new key's fingerprints (needed in Google Cloud Console)
  5. offer to store the keystore and password as GitHub secrets via `gh`

BACK UP %USERPROFILE%\WatchDog-signing AND THE PASSWORD. If they are lost, phones
can only get updates again after an uninstall (which deletes the app's data).
#>
$ErrorActionPreference = 'Stop'

$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$outDir = Join-Path $env:USERPROFILE 'WatchDog-signing'
$ks = Join-Path $outDir 'watchdog-release.p12'
$lineage = Join-Path $repo 'keystore\signing-lineage.bin'
$debugKs = Join-Path $repo 'keystore\debug.keystore'
$bt = Get-ChildItem "$env:LOCALAPPDATA\Android\Sdk\build-tools" -Directory |
    Sort-Object { [version]($_.Name -replace '[^0-9.]', '') } | Select-Object -Last 1
$apksigner = Join-Path $bt.FullName 'apksigner.bat'

if (Test-Path $lineage) { throw "$lineage already exists - the release key was set up before." }
if (-not (Test-Path $debugKs)) { throw "Missing $debugKs (run from the Watch Dog repo)." }

function Read-Plain([string]$prompt) {
    $secure = Read-Host $prompt -AsSecureString
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr) } finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) }
}

# A key left over from an unfinished run was never used for anything, so it can
# be replaced; once the lineage exists (checked above) this never happens.
if (Test-Path $ks) {
    $answer = Read-Host "An unfinished release key exists at $ks (never used). Replace it with a new one? (y/n)"
    if ($answer -ne 'y') { throw "Stopped. Nothing changed." }
    Remove-Item $ks -Force
}
New-Item -ItemType Directory -Force $outDir | Out-Null

Write-Host "`n1/5  Choose the release key password (at least 12 characters). Nothing shows while you type."
Write-Host "     Save it in your password manager.`n"
while ($true) {
    $pw = Read-Plain "Password"
    $pw2 = Read-Plain "Type it again"
    if ($pw -ne $pw2) { Write-Host "They don't match - try again.`n"; continue }
    if ($pw.Length -lt 12) { Write-Host "Use at least 12 characters - try again.`n"; continue }
    break
}
$env:WD_RELEASE_KS_PASS = $pw
try {
    Write-Host "`n2/5  Creating the release key..."
    & keytool -genkeypair -keystore $ks -storetype PKCS12 -alias watchdog -keyalg RSA -keysize 4096 `
        -validity 36500 -dname "CN=Watch Dog release, O=shahaabapps" `
        -storepass:env WD_RELEASE_KS_PASS -keypass:env WD_RELEASE_KS_PASS
    if ($LASTEXITCODE -ne 0) { throw "keytool failed." }

    Write-Host "`n3/5  Creating the rotation proof..."
    & $apksigner rotate --out $lineage `
        --old-signer --ks $debugKs --ks-pass pass:android --ks-key-alias androiddebugkey `
        --new-signer --ks $ks --ks-pass env:WD_RELEASE_KS_PASS --ks-key-alias watchdog
    if ($LASTEXITCODE -ne 0) { throw "apksigner rotate failed." }

    Write-Host "`n4/5  Fingerprints (Signer #2 is the NEW release key - copy its SHA-1):`n"
    & $apksigner lineage --in $lineage --print-certs | Select-String "DN|SHA-1|SHA-256"

    Write-Host "`n5/5  GitHub secrets"
    $answer = Read-Host "Store the keystore and password as GitHub secrets now with gh? (y/n)"
    if ($answer -eq 'y') {
        [Convert]::ToBase64String([IO.File]::ReadAllBytes($ks)) | gh secret set RELEASE_KEYSTORE_B64 --repo shahabcodes/WatchDog
        if ($LASTEXITCODE -ne 0) { throw "Setting RELEASE_KEYSTORE_B64 failed." }
        $pw | gh secret set RELEASE_KEYSTORE_PASSWORD --repo shahabcodes/WatchDog
        if ($LASTEXITCODE -ne 0) { throw "Setting RELEASE_KEYSTORE_PASSWORD failed." }
        Write-Host "Both secrets stored."
    }
} finally {
    $env:WD_RELEASE_KS_PASS = $null
    $pw = $null; $pw2 = $null
}

Write-Host "`nDone. Next:"
Write-Host "  - BACK UP $outDir and the password (password manager + an offline copy)."
Write-Host "  - Add Signer #2's SHA-1 to the Firebase API key in Google Cloud Console (see the steps Claude gave you)."
Write-Host "  - keystore\signing-lineage.bin is public and gets committed; the .p12 must never be committed."
