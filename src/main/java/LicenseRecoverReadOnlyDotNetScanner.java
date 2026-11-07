import java.io.File;
import java.io.IOException;

/** One .NET pipeline: classify protocol, bind audited evidence, run the shared predicate, recheck. */
public final class LicenseRecoverReadOnlyDotNetScanner {
    private LicenseRecoverReadOnlyDotNetScanner() { }
    public static final class Result {
        public final LicenseRecoverLicenseState state;
        public final LicenseRecoverReadOnlyDotNetCatalog.Assessment protocol;
        public final LicenseRecoverReadOnlyCodeEvidence codeEvidence;
        Result(LicenseRecoverLicenseState state,LicenseRecoverReadOnlyDotNetCatalog.Assessment protocol) {
            this(state,protocol,LicenseRecoverReadOnlyCodeEvidence.unassessed("当前 .NET 代码结构尚未获得独立证明"));
        }
        Result(LicenseRecoverLicenseState state,LicenseRecoverReadOnlyDotNetCatalog.Assessment protocol,
               LicenseRecoverReadOnlyCodeEvidence codeEvidence) {
            this.state=state;this.protocol=protocol;this.codeEvidence=codeEvidence;
        }
    }
    public static LicenseRecoverLicenseState scan(File root) {return inspect(root).state;}
    public static Result inspect(File root) {
        LicenseRecoverReadOnlyDotNetCatalog.Assessment first=null;
        try {
            LicenseRecoverReadOnlyCodeEvidence codeEvidence=LicenseRecoverReadOnlyDotNetCodeEvidence.inspect(root);
            first=LicenseRecoverReadOnlyDotNetCatalog.assess(root,true);
            LicenseRecoverLicenseState state;
            if(first.upper!=null) {
                state=LicenseRecoverReadOnlyDotNetVerifier.verifyMatched(root,first.upper);
                if(!state.isScanError()&&state.acceptance==LicenseRecoverLicenseState.Acceptance.UNVERIFIED&&first.extended!=null)
                    state=combineDiagnostics(state,LicenseRecoverReadOnlyDotNetExtendedVerifier.inspectMatched(root,first.extended,
                            first.extended.legacy()?LicenseRecoverReadOnlyMachineIdentity.current():null).state);
            }
            else if(first.extended!=null)state=LicenseRecoverReadOnlyDotNetExtendedVerifier.inspectMatched(root,first.extended,
                    first.extended.legacy()?LicenseRecoverReadOnlyMachineIdentity.current():null).state;
            else state=LicenseRecoverLicenseState.unverified(first.summary()
                    +"；识别同族不等于授权通过；未按软件名、版本前缀或接口同名推定有效");
            if(state.isScanError())return new Result(state,null);
            LicenseRecoverReadOnlyDotNetCatalog.Assessment last=LicenseRecoverReadOnlyDotNetCatalog.assess(root,false);
            Result retained=retainIfStable(state,first,last);
            return retained.protocol==null?retained:new Result(retained.state,retained.protocol,codeEvidence);
        }catch(LicenseRecoverReadOnlyDotNetCatalog.Changed e) {
            return new Result(LicenseRecoverLicenseState.unverified(".NET 协议组件在读取期间发生变化"),null);
        }catch(IOException|SecurityException e) {
            return new Result(LicenseRecoverLicenseState.scanError(".NET 统一只读检查失败: "+e.getClass().getSimpleName()),null);
        }catch(IllegalStateException e) {
            return new Result(LicenseRecoverLicenseState.scanError(".NET 协议审核目录存在冲突，未进行授权判定"),null);
        }
    }
    /** A dual-component build retains both scoped findings when neither path proves acceptance. */
    static LicenseRecoverLicenseState combineDiagnostics(LicenseRecoverLicenseState modern,LicenseRecoverLicenseState compatibility) {
        if(modern.isScanError())return modern;if(compatibility.isScanError())return compatibility;
        if(modern.acceptance!=LicenseRecoverLicenseState.Acceptance.UNVERIFIED
                ||compatibility.acceptance!=LicenseRecoverLicenseState.Acceptance.UNVERIFIED
                ||modern.authorization!=LicenseRecoverLicenseState.Authorization.UNVERIFIED
                ||compatibility.authorization!=LicenseRecoverLicenseState.Authorization.UNVERIFIED
                ||modern.codeIntegrity!=LicenseRecoverLicenseState.CodeIntegrity.UNKNOWN
                ||compatibility.codeIntegrity!=LicenseRecoverLicenseState.CodeIntegrity.UNKNOWN)
            throw new IllegalArgumentException("only unresolved local diagnostics may be combined");
        LicenseRecoverLicenseState.LocalCondition condition=modern.localCondition;
        if((condition==LicenseRecoverLicenseState.LocalCondition.NOT_CHECKED||condition==LicenseRecoverLicenseState.LocalCondition.UNSUPPORTED)
                &&compatibility.localCondition!=LicenseRecoverLicenseState.LocalCondition.NOT_CHECKED)
            condition=compatibility.localCondition;
        String evidence="大写现代本地路径〔"+modern.evidence+"〕；小写兼容路径〔"+compatibility.evidence+"〕；未得出完整授权拒绝结论";
        return condition==LicenseRecoverLicenseState.LocalCondition.NOT_CHECKED?LicenseRecoverLicenseState.unverified(evidence)
                :LicenseRecoverLicenseState.localObservation(condition,evidence);
    }
    static Result retainIfStable(LicenseRecoverLicenseState state,LicenseRecoverReadOnlyDotNetCatalog.Assessment first,
            LicenseRecoverReadOnlyDotNetCatalog.Assessment last) {
        if(state.isScanError())return new Result(state,null);
        if(first==null||!first.sameInputs(last))return new Result(LicenseRecoverLicenseState.unverified(
                ".NET 协议组件或根配置在读取期间发生变化，已撤销当前结论"),null);
        return new Result(state,first);
    }
}
