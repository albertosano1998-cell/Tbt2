# Charge Stop 85

Android app prototype for battery-limit diagnostics on non-rooted devices using Shizuku.

## Included
- Live battery percentage and charging-state display.
- Saved adjustable target threshold (75–95%).
- Shizuku permission/status UI.
- Read-only scan of common Android power-supply paths for likely charging-control nodes and shell write access.
- GitHub Actions debug APK build artifact.

## Important limitation
This is not yet an automatic charging cutoff. Android has no public API for disabling physical battery charging, and a Shizuku shell does not guarantee write access to charging-control hardware. The scan is diagnostic only and does not write to system files. A real cutoff/resume implementation must be validated on the target Infinix X693 firmware before it can be safely enabled.

## Build and download
Open the Actions tab in this repository, choose the latest successful **Android debug APK** run, and download the **ChargeStop85-debug** artifact.
