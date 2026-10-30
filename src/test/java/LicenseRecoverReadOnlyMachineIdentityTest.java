import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class LicenseRecoverReadOnlyMachineIdentityTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static String e(String s){return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));}
    private static LicenseRecoverReadOnlyMachineIdentity.Snapshot parse(String s){return LicenseRecoverReadOnlyMachineIdentity.parse(s.getBytes(StandardCharsets.US_ASCII));}
    public static void main(String[] args) {
        String wire="LRID1\nHOST\t"+e("synthetic-host")+"\nCPU\t"+e("cpu-first")+"\nCPU\t"+e("cpu-last")
                +"\nDISK\t~\t"+e("disk-serial")+"\nDISK\t"+e("42")+"\t~\nBOARD\t"+e(" board ")+"\nEND\n";
        LicenseRecoverReadOnlyMachineIdentity.Snapshot s=parse(wire);
        check(s.complete&&s.cpuIds.size()==2,"complete ordered CPU enumeration");
        check("cpu-last".equals(s.cpuIds.get(1)),"CPU last row preserved");
        check(s.disks.get(0).signature==null&&"42".equals(s.disks.get(1).signature),"null disk signature not skipped or normalized");
        check(" board ".equals(s.boards.get(0)),"raw whitespace preserved for audited caller rule");
        check(!s.reason.contains("cpu-first")&&!s.reason.contains("disk-serial"),"hardware values excluded from reason");
        check(!parse(wire.replace("END","BROKEN")).complete,"partial output rejected");
        check(!parse(wire.replace("CPU\t"+e("cpu-first"),"CPU\t%bad%")).complete,"bad base64 rejected");
        check(!parse(wire.replace("END","HOST\t"+e("duplicate")+"\nEND")).complete,"duplicate host rejected");
        check(!parse(wire.replace("BOARD\t"+e(" board "),"INJECT\t"+e("bad"))).complete,"unknown records rejected");
        check(!LicenseRecoverReadOnlyMachineIdentity.parse(new byte[32769]).complete,"bounded output");
        boolean immutable=false;
        try{s.cpuIds.add("bad");}catch(UnsupportedOperationException expected){immutable=true;}
        check(immutable,"snapshot immutable");
        System.out.println("PASS: local machine metadata boundary; order/nulls/privacy/malformed/bounds");
    }
}
