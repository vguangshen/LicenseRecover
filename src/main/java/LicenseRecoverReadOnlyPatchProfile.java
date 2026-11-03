import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Compiled, reviewed evidence for ONE build and ONE patch recipe. Product/mode
 * is only a candidate key, never a vendor release version or authorization proof.
 * No scanner imports these records from a target directory or a user manifest.
 */
public final class LicenseRecoverReadOnlyPatchProfile {
    final String profileId,productMode,buildId,recipeId,auditRef;
    final LicenseRecoverReadOnlyPatchScanner.Runtime runtime;
    final List<FixedBinding> fixedBindings;
    final List<TargetPair> targets;

    static final class FixedBinding {
        final String role,path,sha256;
        FixedBinding(String role,String path,String sha256) {
            this.role=role;this.path=path;this.sha256=sha256;
        }
    }
    static final class TargetPair {
        final String role,path,originalSha256,patchedSha256;
        TargetPair(String role,String path,String originalSha256,String patchedSha256) {
            this.role=role;this.path=path;this.originalSha256=originalSha256;this.patchedSha256=patchedSha256;
        }
    }

    /** Schema is validated by the scanner, including records not applicable to this scan. */
    LicenseRecoverReadOnlyPatchProfile(String profileId,LicenseRecoverReadOnlyPatchScanner.Runtime runtime,
            String productMode,String buildId,String recipeId,String auditRef,
            FixedBinding[] fixedBindings,TargetPair[] targets) {
        this.profileId=profileId;this.runtime=runtime;this.productMode=productMode;
        this.buildId=buildId;this.recipeId=recipeId;this.auditRef=auditRef;
        this.fixedBindings=fixedBindings==null?null:Collections.unmodifiableList(Arrays.asList(fixedBindings.clone()));
        this.targets=targets==null?null:Collections.unmodifiableList(Arrays.asList(targets.clone()));
    }
}
