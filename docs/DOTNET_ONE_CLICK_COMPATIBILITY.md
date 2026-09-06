# .NET one-click recovery compatibility matrix

This document records static compatibility checks performed against authorized ITMC deployment samples. Vendor binaries are **not** stored in this repository; only hashes and behavior findings are recorded.

## Verified sample families

| Sample | ITMC.Web.dll SHA-256 | ITMC.Regedit.dll SHA-256 | lowercase itmcRegedit.dll | Recovered ProName | Local product list | One-click detection |
|---|---|---|---|---|---|---|
| YX030101 | `acc93911ce4f4c20bbb7a0bba2276faad43135bfcd352b03f1e642ef522ccd0b` | `dad19a5a7227e2869e4b1c9afea31a203feec8199f5d2f570a9f97dafd0a8932` | yes | `YX0301` | YX030101, YX030102, YX030114-YX030124 (known embedded IDs) | `DOTNET_MODERN / YX0301` |
| YX030204 | `59dcf16597dcdbfbbcde3df34e702ff3df19d57fb218adce526ef7c758e4aa2c` | `222df2d024efb4b30510af9bf5f552646e4e2f75ea9b3795daa7a279d8a6f725` | yes | `YX0302` | YX030201-204, YX030210-213 | `DOTNET_MODERN / YX0302` |
| YX030308 | `57fa95aa72a15bfd569834374860f29dbef22a48294f7010e94846023a94ce35` | `f5a2c38dee0f694c33fdac2c5570c2431b955c594e244e87596f120c0eb07f99` | **no** | `YX0303` | YX030301-317, YX030320-322 | `DOTNET_MODERN / YX0303` |
| YX030322 | `57fa95aa72a15bfd569834374860f29dbef22a48294f7010e94846023a94ce35` | `f5a2c38dee0f694c33fdac2c5570c2431b955c594e244e87596f120c0eb07f99` | **no** | `YX0303` | YX030301-317, YX030320-322 | `DOTNET_MODERN / YX0303` |
| DS01xx family sample | `21464d180e30ed1f10c0c6b20e56381191ecd70aab06595f07a27dc4d559961a` | `25b6b2939534b8266d843f7bd7b942d9ee87fd48296c8ed05828ffc3de76c0d8` | yes | `itmcIEC` | DS0101-DS0107, DS0110, DS0112 | `DOTNET_MODERN / DS01xx / itmcIEC` |

Lowercase registration assembly hashes when present:

- YX030101: `e684d265332af25ccc3449c38a38f024ecbf87353b9c48710c5ae2b2433c5183`
- YX030204: `35e40be423f9ea8493edc29bbdfd406a030309241d6ec8c2f1ae3975ef7e5b5f`
- DS01xx sample: `48431b1c9be49ceba4f49d5dada802678d1ba033cbe939dc5650bb4174fb5d95`

## Static findings shared by these generations

The protected method bodies were recovered from FOAP/VBPD records for analysis. The modern uppercase registration path uses the application-native local-license flow:

1. obtain/resolve a vendor RegID;
2. construct `RegeditInfo`;
3. serialize the object as JSON;
4. frame it with six random digits before and after the JSON;
5. encrypt the framed payload with the product-local key `*ITMC{ProName}OK*`;
6. persist it as `regName` with `regType=1`;
7. later `CheckReInfo -> CheckLocalReg -> getLocalRegInfo` decrypts and deserializes the same object.

The DES implementation is consistent across the analyzed modern registration assemblies: DES/CBC with PKCS5-compatible padding, with both key and IV set to the first eight ASCII characters of the uppercase MD5 hex string of the supplied password.

A known YX0302 `regName` captured from an installed application successfully decrypts with `*ITMCYX0302OK*`, providing a real-format compatibility vector for the implementation.

## Machine identity differences

### YX0301 generation

`getRegNo()` uses the vendor `Computer` hardware values directly. It combines the selected board/CPU portion with the disk-signature portion to produce the 16-character RegID. It does not expose the later `WebSerSoftNo + GetCpuid` shortcut.

### YX0302 / YX0303 / analyzed DS01xx generation

These versions first attempt the existing `WebSerSoftNo` shortcut using the CPU ProcessorId key. If it cannot produce a usable existing RegNo, they fall back to the same board/CPU + disk machine-ID algorithm.

The one-click adapter checks registration-assembly metadata before enabling this shortcut so YX0301 is not forced through a later-generation path.

## Detector regression fixed for YX0303

YX030308 and YX030322 contain `ITMC.Web.dll` + uppercase `ITMC.Regedit.dll` but **do not contain lowercase `itmcRegedit.dll`**. Older `AppDetector.hasDotNetFiles()` required the lowercase file and therefore missed these applications.

The v1.2 branch now recognizes a .NET target when `ITMC.Web.dll` exists and **either** `ITMC.Regedit.dll` or `itmcRegedit.dll` is present.

## DS01xx finding

The analyzed DS01xx sample is not limited to the old browser registration-code UI. Its recovered `ITMC.Web.funpublic.CheckReg` also invokes the modern uppercase `ITMC.Regedit.RegeditMain` / `CheckReInfo` local-license path, with `ProName=itmcIEC`. Therefore the one-click adapter can use the native `regName` path instead of requiring the registration web page for this generation.

## Validation status

- FOAP/VBPD method-body recovery: completed for all five sample sets.
- ProName and product-ID extraction from the target assemblies: completed.
- Local-license crypto compatibility: statically verified; known YX0302 real `regName` decrypt vector verified.
- Java 8 build and packaging of the v1.2 one-click overlay: GitHub Actions verified.
- Final artifact contains `LicenseRecoverModernGUIAutoRecovery` and the updated `AppDetector`.
- Live Windows/IIS write-back should still be tested on an isolated copy before merging v1.2 to `main`; static compatibility does not replace an application-runtime regression test.
