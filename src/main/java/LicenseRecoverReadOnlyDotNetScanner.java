import java.io.File;
import java.io.IOException;

/** One .NET pipeline: classify protocol, bind audited evidence, run the shared predicate, recheck. */
public final class LicenseRecoverReadOnlyDotNetScanner {
    private LicenseRecoverReadOnlyDotNetScanner() { }
    public static final class Result {
        public final LicenseRecoverLicenseState state;
        public final LicenseRecoverReadOnlyDotNetCatalog.Assessment protocol;
        Result(LicenseRecoverLicenseState state,LicenseRecoverReadOnlyDotNetCatalog.Assessment protocol) {
            this.state=state;this.protocol=protocol;
        }
    }
    public static LicenseRecoverLicenseState scan(File root) {return inspect(root).state;}
    public static Result inspect(File root) {
        LicenseRecoverReadOnlyDotNetCatalog.Assessment first=null;
        try {
            first=LicenseRecoverReadOnlyDotNetCatalog.assess(root,true);
            LicenseRecoverLicenseState state;
            if(first.upper!=null)state=LicenseRecoverReadOnlyDotNetVerifier.verifyMatched(root,first.upper);
            else if(first.extended!=null)state=LicenseRecoverReadOnlyDotNetExtendedVerifier.inspectMatched(root,first.extended,
                    first.extended.legacy()?LicenseRecoverReadOnlyMachineIdentity.current():null).state;
            else state=LicenseRecoverLicenseState.unverified(first.summary()
                    +"；识别同族不等于授权通过；未按软件名、版本前缀或接口同名推定有效");
            if(state.isScanError())return new Result(state,null);
            LicenseRecoverReadOnlyDotNetCatalog.Assessment last=LicenseRecoverReadOnlyDotNetCatalog.assess(root,false);
            return retainIfStable(state,first,last);
        }catch(LicenseRecoverReadOnlyDotNetCatalog.Changed e) {
            return new Result(LicenseRecoverLicenseState.unverified(".NET 协议组件在读取期间发生变化"),null);
        }catch(IOException|SecurityException e) {
            return new Result(LicenseRecoverLicenseState.scanError(".NET 统一只读检查失败: "+e.getClass().getSimpleName()),null);
        }catch(IllegalStateException e) {
            return new Result(LicenseRecoverLicenseState.scanError(".NET 协议审核目录存在冲突，未进行授权判定"),null);
        }
    }
    static Result retainIfStable(LicenseRecoverLicenseState state,LicenseRecoverReadOnlyDotNetCatalog.Assessment first,
            LicenseRecoverReadOnlyDotNetCatalog.Assessment last) {
        if(state.isScanError())return new Result(state,null);
        if(first==null||!first.sameInputs(last))return new Result(LicenseRecoverLicenseState.unverified(
                ".NET 协议组件或根配置在读取期间发生变化，已撤销当前结论"),null);
        return new Result(state,first);
    }
}
