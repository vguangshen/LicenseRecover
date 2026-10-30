import java.io.File;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Opt-in local real-copy integration; prints no config/registration values. */
public final class LicenseRecoverRealSampleReadOnlyTest {
    public static void main(String[] args) throws Exception {
        if(args.length!=3 || !"--expected".equals(args[1]))
            throw new IllegalArgumentException("independent scan-input parent --expected private-expectations.csv");
        Map<String, Expected> expected = readExpectations(args[2]);
        System.setProperty("java.awt.headless","true");
        Object gui=LicenseRecoverModernGUI.class.getDeclaredConstructor().newInstance();
        Method inspect=LicenseRecoverModernGUI.class.getDeclaredMethod("inspectBatchDirectory",File.class);
        inspect.setAccessible(true);
        Field timeoutField=LicenseRecoverModernGUI.class.getDeclaredField("BATCH_SCAN_DIRECTORY_TIMEOUT_MILLIS");
        timeoutField.setAccessible(true);
        long timeoutMillis=timeoutField.getLong(null);
        File[] children=new File(args[0]).listFiles(File::isDirectory);
        if(children==null||children.length!=expected.size())
            throw new AssertionError("independent copy count must exactly match the explicit expectation map");
        Arrays.sort(children);int detected=0,errors=0,accepted=0;
        for(File child:children) {
            Expected verdict=expected.get(child.getName());
            if(verdict==null)throw new AssertionError("unexpected application copy: "+child.getName());
            long started=System.nanoTime();
            Object result=LicenseRecoverModernGUI.runBoundedBatchInspection(()->inspect.invoke(gui,child),timeoutMillis);
            long elapsedMillis=java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started);
            Field cellsField=result.getClass().getDeclaredField("cells");cellsField.setAccessible(true);
            Object[] cells=(Object[])cellsField.get(result);
            Field d=result.getClass().getDeclaredField("detected"),e=result.getClass().getDeclaredField("errors");
            d.setAccessible(true);e.setAccessible(true);detected+=d.getInt(result);errors+=e.getInt(result);
            LicenseRecoverReadOnlyInspection.Result current=LicenseRecoverReadOnlyInspection.inspect(child);
            if(current.state.visualStatus()!=verdict.visual || current.state.acceptance!=verdict.acceptance)
                throw new AssertionError("unexpected evidence state for "+child.getName()+": "
                        +current.state.visualStatus()+"/"+current.state.acceptance);
            if(!current.state.display().equals(String.valueOf(cells[7]))
                    || LicenseRecoverLicenseState.visualStatus(String.valueOf(cells[7]))!=verdict.visual)
                throw new AssertionError("GUI and current inspection disagree for "+child.getName());
            if(String.valueOf(cells[8]).trim().isEmpty())throw new AssertionError("missing detection reason for "+child.getName());
            if(current.state.acceptance==LicenseRecoverLicenseState.Acceptance.VERIFIED_USABLE) {
                accepted++;
                if(current.state.authorization!=LicenseRecoverLicenseState.Authorization.UNVERIFIED
                        || current.state.evidence.trim().isEmpty())
                    throw new AssertionError("entry acceptance cannot fabricate native activation or omit evidence");
            }
            System.out.println(child.getName()+": "+current.state.visualStatus()
                    +"; acceptance="+current.state.acceptance+"; native="+current.state.authorization
                    +"; GUI-inspection-ms="+elapsedMillis);
        }
        if(detected!=expected.size()||errors!=0)throw new AssertionError("detected="+detected+", errors="+errors);
        System.out.println("PASS: actual GUI inspection, "+detected+" detected, 0 errors; "+accepted
                +" current entries accepted; all results match the explicit private expectation map");
    }

    /** Private oracle: application,visualStatus,acceptance; never inferred from the scan under test. */
    private static Map<String,Expected> readExpectations(String path) throws Exception {
        List<String> lines=Files.readAllLines(Paths.get(path),StandardCharsets.UTF_8);
        if(lines.isEmpty() || !"application,visualStatus,acceptance".equals(lines.get(0)))
            throw new IllegalArgumentException("expected UTF-8 CSV header: application,visualStatus,acceptance");
        Map<String,Expected> expected=new LinkedHashMap<String,Expected>();
        for(int i=1;i<lines.size();i++) {
            String line=lines.get(i);
            if(line.trim().isEmpty())continue;
            String[] parts=line.split(",",-1);
            if(parts.length!=3 || !parts[0].matches("[A-Za-z0-9._-]+")
                    || ".".equals(parts[0]) || "..".equals(parts[0]))
                throw new IllegalArgumentException("malformed expectation row "+(i+1));
            Expected row=new Expected(LicenseRecoverLicenseState.VisualStatus.valueOf(parts[1]),
                    LicenseRecoverLicenseState.Acceptance.valueOf(parts[2]));
            if(expected.put(parts[0],row)!=null)throw new IllegalArgumentException("duplicate expectation application");
        }
        if(expected.isEmpty())throw new IllegalArgumentException("expected applications must be explicit");
        return expected;
    }

    private static final class Expected {
        final LicenseRecoverLicenseState.VisualStatus visual;
        final LicenseRecoverLicenseState.Acceptance acceptance;
        Expected(LicenseRecoverLicenseState.VisualStatus visual, LicenseRecoverLicenseState.Acceptance acceptance) {
            this.visual=visual;this.acceptance=acceptance;
        }
    }
}
