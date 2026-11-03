/** Code behavior and component provenance are separate from license and whole-application Patch proof. */
public final class LicenseRecoverReadOnlyCodeEvidence {
    public enum Behavior { UNASSESSED, AUDITED_STATIC_BYPASS }
    public enum Component { UNASSESSED, REFERENCE_COMPONENT_MATCH, REPLACEMENT_SHAPE_CANDIDATE }
    public final Behavior behavior;
    public final Component component;
    public final String behaviorEvidence, componentEvidence, evidenceSource;
    public final java.util.Map<String,String> componentHashes;
    public final boolean readOnlyAudited=true;

    private LicenseRecoverReadOnlyCodeEvidence(Behavior behavior,Component component,String gate,String part,String source) {
        this(behavior,component,gate,part,source,java.util.Collections.<String,String>emptyMap());
    }
    private LicenseRecoverReadOnlyCodeEvidence(Behavior behavior,Component component,String gate,String part,String source,
            java.util.Map<String,String> hashes) {
        if(behavior==null||component==null||gate==null||part==null||source==null)
            throw new IllegalArgumentException("complete code evidence is required");
        this.behavior=behavior;this.component=component;behaviorEvidence=gate;componentEvidence=part;evidenceSource=source;
        java.util.Map<String,String> copy=new java.util.TreeMap<String,String>();
        for(java.util.Map.Entry<String,String> e:hashes.entrySet()) {
            String path=e.getKey();if(path==null||path.isEmpty()||path.startsWith("/")||path.contains("\\")||path.contains(":")
                    ||path.indexOf(0)>=0||e.getValue()==null||!e.getValue().matches("[0-9a-f]{64}"))
                throw new IllegalArgumentException("invalid root-relative component receipt");
            java.nio.file.Path p=java.nio.file.Paths.get(path).normalize();
            if(p.isAbsolute()||p.startsWith("..")||!p.toString().replace('\\','/').equals(path))
                throw new IllegalArgumentException("invalid component receipt path");
            copy.put(path,e.getValue());
        }
        componentHashes=java.util.Collections.unmodifiableMap(copy);
    }
    public static LicenseRecoverReadOnlyCodeEvidence unassessed(String reason) {
        return new LicenseRecoverReadOnlyCodeEvidence(Behavior.UNASSESSED,Component.UNASSESSED,
                "当前代码门槛尚无完整静态证明",reason==null?"没有适用的组件对照证据":reason,"current-files-only");
    }
    static LicenseRecoverReadOnlyCodeEvidence referenceComponent(String evidence,String source) {
        require(evidence);require(source);
        return new LicenseRecoverReadOnlyCodeEvidence(Behavior.UNASSESSED,Component.REFERENCE_COMPONENT_MATCH,
                "组件原版对照不能证明应用层未修改",evidence,source);
    }
    static LicenseRecoverReadOnlyCodeEvidence replacementCandidate(String evidence,String source) {
        require(evidence);require(source);
        return new LicenseRecoverReadOnlyCodeEvidence(Behavior.UNASSESSED,Component.REPLACEMENT_SHAPE_CANDIDATE,
                "局部替换形态不能证明完整授权门槛已绕过",evidence,source);
    }
    static LicenseRecoverReadOnlyCodeEvidence staticBypass(LicenseRecoverReadOnlyCodeEvidence component,String evidence,String source) {
        if(component==null)throw new IllegalArgumentException("component observation required");
        require(evidence);require(source);
        return new LicenseRecoverReadOnlyCodeEvidence(Behavior.AUDITED_STATIC_BYPASS,component.component,
                evidence,component.componentEvidence,source+"; "+component.evidenceSource,component.componentHashes);
    }
    LicenseRecoverReadOnlyCodeEvidence bindComponents(java.util.Map<String,String> hashes) {
        if(hashes==null||hashes.isEmpty())throw new IllegalArgumentException("component receipts required");
        return new LicenseRecoverReadOnlyCodeEvidence(behavior,component,behaviorEvidence,componentEvidence,evidenceSource,hashes);
    }
    public String display() {
        if(behavior==Behavior.AUDITED_STATIC_BYPASS)return "当前代码静态放行（Patch 来源待核）";
        if(component==Component.REFERENCE_COMPONENT_MATCH)return "注册组件同原版对照（仅组件）";
        if(component==Component.REPLACEMENT_SHAPE_CANDIDATE)return "发现替换形态（Patch 待证）";
        return "代码修改状态待核";
    }
    public String summary() {return display()+"；"+behaviorEvidence+"；"+componentEvidence;}
    private static void require(String value) {if(value==null||value.trim().isEmpty())throw new IllegalArgumentException("audited code evidence required");}
}
