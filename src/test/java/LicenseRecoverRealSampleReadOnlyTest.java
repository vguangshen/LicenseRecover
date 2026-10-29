import java.io.File;
import java.lang.reflect.*;
import java.util.Arrays;

/** Opt-in local real-copy integration; prints no config/registration values. */
public final class LicenseRecoverRealSampleReadOnlyTest {
    public static void main(String[] args) throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("independent scan-input parent required");
        System.setProperty("java.awt.headless","true");
        Object gui=LicenseRecoverModernGUI.class.getDeclaredConstructor().newInstance();
        Method inspect=LicenseRecoverModernGUI.class.getDeclaredMethod("inspectBatchDirectory",File.class);
        inspect.setAccessible(true);
        File[] children=new File(args[0]).listFiles(File::isDirectory);
        if(children==null||children.length!=26)throw new AssertionError("expected 26 independent application copies");
        Arrays.sort(children);int detected=0,errors=0;
        for(File child:children) {
            Object result=inspect.invoke(gui,child);
            Field cellsField=result.getClass().getDeclaredField("cells");cellsField.setAccessible(true);
            Object[] cells=(Object[])cellsField.get(result);
            Field d=result.getClass().getDeclaredField("detected"),e=result.getClass().getDeclaredField("errors");
            d.setAccessible(true);e.setAccessible(true);detected+=d.getInt(result);errors+=e.getInt(result);
            if(!"未验证".equals(cells[7]))throw new AssertionError("unexpected state for "+child.getName()+": "+cells[7]);
            if(!String.valueOf(cells[8]).contains("REJECTED_SIDE_EFFECTS"))throw new AssertionError("missing audited rejection for "+child.getName());
            System.out.println(child.getName()+": UNVERIFIED; file-only evidence; unsafe native verifier rejected");
        }
        if(detected!=26||errors!=0)throw new AssertionError("detected="+detected+", errors="+errors);
        System.out.println("PASS: actual GUI inspection, 26 detected, 0 errors; no license verdict fabricated");
    }
}
