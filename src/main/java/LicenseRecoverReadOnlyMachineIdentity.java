import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Local OS metadata only. No vendor code, registry writes, temporary scripts or network calls. */
public final class LicenseRecoverReadOnlyMachineIdentity {
    private LicenseRecoverReadOnlyMachineIdentity() { }
    private static final int LIMIT = 32768;
    private static Snapshot cached;
    private static long cachedAt;
    public static final class Disk {
        public final String signature, serial;
        public Disk(String signature, String serial) { this.signature=signature;this.serial=serial; }
    }
    public static final class Snapshot {
        public final List<String> cpuIds, boards;
        public final List<Disk> disks;
        public final String hostname, reason;
        public final boolean complete;
        public Snapshot(List<String> cpuIds,List<Disk> disks,List<String> boards,String hostname) {
            this(cpuIds,disks,boards,hostname,true,"本机只读 WMI 元数据");
        }
        public Snapshot(List<String> cpuIds,List<Disk> disks,List<String> boards,String hostname,
                        boolean complete,String reason) {
            this.cpuIds=Collections.unmodifiableList(new ArrayList<String>(cpuIds));
            this.disks=Collections.unmodifiableList(new ArrayList<Disk>(disks));
            this.boards=Collections.unmodifiableList(new ArrayList<String>(boards));
            this.hostname=hostname;this.complete=complete;this.reason=reason;
        }
    }
    private static Snapshot unavailable(String reason) {
        return new Snapshot(Collections.<String>emptyList(),Collections.<Disk>emptyList(),
                Collections.<String>emptyList(),null,false,reason);
    }
    /** A short process-local cache keeps a batch from querying WMI for every application. */
    public static synchronized Snapshot current() {
        long now=System.nanoTime();
        if(cached!=null && now-cachedAt<TimeUnit.SECONDS.toNanos(5))return cached;
        cached=readLocal();cachedAt=System.nanoTime();return cached;
    }
    private static Snapshot readLocal() {
        if(!System.getProperty("os.name","").toLowerCase(Locale.ROOT).startsWith("windows"))
            return unavailable("当前平台未审核本机标识规则");
        String systemRoot=System.getenv("SystemRoot");
        if(systemRoot==null)return unavailable("缺少本机系统目录");
        File executable=new File(systemRoot,"System32/WindowsPowerShell/v1.0/powershell.exe");
        if(!executable.isFile())return unavailable("本机只读 WMI 查询不可用");
        // Fixed script: all data originate from local WMI; no application values become code.
        String script="$ErrorActionPreference='Stop';"
                +"function E($v){if($null -eq $v){return '~'};return [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes([string]$v))};"
                +"[Console]::WriteLine('LRID1');"
                +"[Console]::WriteLine(('HOST'+[char]9+(E ([Environment]::MachineName))));"
                +"foreach($x in @(Get-WmiObject -Query 'SELECT * FROM Win32_Processor')){[Console]::WriteLine(('CPU'+[char]9+(E $x.ProcessorId)))};"
                +"foreach($x in @(Get-WmiObject -Query 'SELECT * FROM Win32_DiskDrive')){[Console]::WriteLine(('DISK'+[char]9+(E $x.Signature)+[char]9+(E $x.SerialNumber)))};"
                +"foreach($x in @(Get-WmiObject -Query 'SELECT * FROM Win32_BaseBoard')){[Console]::WriteLine(('BOARD'+[char]9+(E $x.SerialNumber)))};"
                +"[Console]::WriteLine('END')";
        Process process=null;
        final ByteArrayOutputStream output=new ByteArrayOutputStream();
        final boolean[] overflow={false};
        try {
            process=new ProcessBuilder(executable.getAbsolutePath(),"-NoLogo","-NoProfile","-NonInteractive",
                    "-Command",script).redirectErrorStream(true).start();
            final Process running=process;
            Thread pump=new Thread(()->{
                try(InputStream in=running.getInputStream()) {
                    byte[] b=new byte[2048];int n;
                    while((n=in.read(b))!=-1) {
                        synchronized(output) {
                            if(output.size()+n>LIMIT){overflow[0]=true;running.destroyForcibly();return;}
                            output.write(b,0,n);
                        }
                    }
                }catch(IOException ignored){ }
            },"LicenseRecover-LocalWmi");
            pump.setDaemon(true);pump.start();
            if(!process.waitFor(3000,TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();return unavailable("本机只读 WMI 查询超时");
            }
            pump.join(300);
            if(process.exitValue()!=0||pump.isAlive()||overflow[0])return unavailable("本机只读 WMI 查询未完成");
            synchronized(output) {return parse(output.toByteArray());}
        }catch(InterruptedException ex) {
            Thread.currentThread().interrupt();return unavailable("本机只读 WMI 查询已取消");
        }catch(IOException|SecurityException ex) {
            return unavailable("本机只读 WMI 查询失败");
        }finally {if(process!=null&&process.isAlive())process.destroyForcibly();}
    }
    static Snapshot parse(byte[] data) {
        if(data==null||data.length>LIMIT)return unavailable("本机标识输出格式无效");
        try {
            String raw=new String(data,StandardCharsets.US_ASCII);
            String[] lines=raw.trim().split("\\r?\\n");
            if(lines.length<3||!"LRID1".equals(lines[0])||!"END".equals(lines[lines.length-1]))throw new IOException();
            List<String> cpus=new ArrayList<String>(),boards=new ArrayList<String>();List<Disk> disks=new ArrayList<Disk>();
            String hostname=null;boolean hasHost=false;
            for(int i=1;i<lines.length-1;i++) {
                String[] p=lines[i].split("\\t",-1);
                if(p.length==2&&"HOST".equals(p[0])&&!hasHost){hostname=decode(p[1]);hasHost=true;}
                else if(p.length==2&&"CPU".equals(p[0]))cpus.add(decode(p[1]));
                else if(p.length==2&&"BOARD".equals(p[0]))boards.add(decode(p[1]));
                else if(p.length==3&&"DISK".equals(p[0]))disks.add(new Disk(decode(p[1]),decode(p[2])));
                else throw new IOException();
                if(cpus.size()>64||boards.size()>64||disks.size()>256)throw new IOException();
            }
            if(!hasHost||hostname==null||hostname.isEmpty())throw new IOException();
            return new Snapshot(cpus,disks,boards,hostname,true,"本机只读 WMI 元数据");
        }catch(IOException|IllegalArgumentException ex){return unavailable("本机标识输出格式无效");}
    }
    private static String decode(String data)throws IOException {
        if("~".equals(data))return null;
        if(data.length()>8192||!data.matches("[A-Za-z0-9+/]*={0,2}"))throw new IOException();
        byte[] decoded=Base64.getDecoder().decode(data);
        String value=new String(decoded,StandardCharsets.UTF_8);
        if(!Arrays.equals(decoded,value.getBytes(StandardCharsets.UTF_8))||value.indexOf('\0')>=0)throw new IOException();
        return value;
    }
}
