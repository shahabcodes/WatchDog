<#
Creates Watch Dog's private release signing key and the "rotation proof" that
lets phones accept it as an update from the old shared debug key.

Run it yourself, once, from the repo root:   .\tools\signing\create-release-key.ps1

It will:
  1. create the release keystore in %USERPROFILE%\WatchDog-signing (asks you for a password)
  2. create keystore\signing-lineage.bin in the repo (public; the old key vouching for the new one)
  3. print the new key's fingerprints (needed in Google Cloud Console)
  4. offer to store the keystore and password as GitHub secrets via `gh` (you type the password)

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

if (Test-Path $ks) { throw "A release key already exists at $ks - not overwriting it." }
if (Test-Path $lineage) { throw "$lineage already exists - the key was created before." }
if (-not (Test-Path $debugKs)) { throw "Missing $debugKs (run from the Watch Dog repo)." }
New-Item -ItemType Directory -Force $outDir | Out-Null

Write-Host "`n1/4  Creating the release key. Choose a strong password (at least 12 characters)"
Write-Host "     and type it when keytool asks (twice). Save it in your password manager.`n"
& keytool -genkeypair -keystore $ks -storetype PKCS12 -alias watchdog -keyalg RSA -keysize 4096 `
    -validity 36500 -dname "CN=Watch Dog release, O=shahaabapps"
if ($LASTEXITCODE -ne 0) { throw "keytool failed." }

Write-Host "`n2/4  Creating the rotation proof. Type the SAME password when apksigner asks.`n"
& $apksigner rotate --out $lineage `
    --old-signer --ks $debugKs --ks-pass pass:android --ks-key-alias androiddebugkey `
    --new-signer --ks $ks --ks-key-alias watchdog
if ($LASTEXITCODE -ne 0) { throw "apksigner rotate failed." }

Write-Host "`n3/4  Fingerprints (the LAST certificate is the new release key):`n"
& $apksigner lineage --in $lineage --print-certs

Write-Host "`n4/4  GitHub secrets"
$answer = Read-Host "Store the keystore and password as GitHub secrets now with gh? (y/n)"
if ($answer -eq 'y') {
    [Convert]::ToBase64String([IO.File]::ReadAllBytes($ks)) | gh secret set RELEASE_KEYSTORE_B64 --repo shahabcodes/WatchDog
    Write-Host "Now type the keystore password for RELEASE_KEYSTORE_PASSWORD:"
    gh secret set RELEASE_KEYSTORE_PASSWORD --repo shahabcodes/WatchDog
}

Write-Host "`nDone. Next:"
Write-Host "  - BACK UP $outDir and the password (password manager + an offline copy)."
Write-Host "  - Add the new SHA-1 above to the Firebase API key in Google Cloud Console (see the steps Claude gave you)."
Write-Host "  - keystore\signing-lineage.bin is public and gets committed; the .p12 must never be committed."
