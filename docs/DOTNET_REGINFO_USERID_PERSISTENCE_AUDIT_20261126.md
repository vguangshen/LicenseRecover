# Proposed narrow RegeditInfo.UserID persistence fix (2026-11-26)

## Evidence
- Exact source binary ITMC.Regedit.dll SHA256: 222df2d024efb4b30510af9bf5f552646e4e2f75ea9b3795daa7a279d8a6f725.
- Its RegeditMain.set_UserID writes WebSerUserID to site-root config.xml; this does NOT populate RegeditInfo.UserID.
- Its native DoRegistry creates a RegeditInfo object and serializes it without an explicit set_UserID. Its independent network path does call RegeditInfo.set_UserID.
- Certain website functionality examines RegeditInfo.UserID and checks for the limited known identities. This static observation does not certify a real deployed authorization.

## Proposed repair
After a successful native DoRegistry only, and only for this exact hash-pinned uppercase DLL:
1. Snapshot and durable-backup the target registration files, regardless of optional backup setting.
2. Read the fresh generated regName, decrypt the existing vendor-format envelope with the product-specific local key already implemented in LicenseRecover.
3. Verify the six-character wrapper, matching ProName, selected RegStr token, nonempty RegID, and a single UserID JSON member.
4. Fill UserID only when currently null or empty, preserving the rest of the native JSON and its original six-character wrappers byte-for-byte. Already matching fwq is a no-op. Another nonempty UserID, unrecognized structure, and inconsistent products or versions fail closed.
5. Encrypt, write, read back, and complete existing native registration and website validation. On any exception, the coordinator restores original snapshots.

No vendor DLLs are rewritten. Lowercase VM-protected chains, unrelated uppercase builds, and DS01xx Legacy paths are deliberately excluded until their actual record formats have been independently validated.

## Testing and limitations
Synthetic regression tests cover field-preserving round trip, idempotency, wrong product, wrong version, duplicate UserID, empty RegID, and nonempty mismatching identity.
This is a draft for source/CI validation and a backed-up real authorized test deployment. The user's ZIP lacks the site-root config.xml, so no real stored license payload has been examined. Do NOT publish as an established real-world functional fix without that verification.
