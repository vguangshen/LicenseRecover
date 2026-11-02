import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Independent local WMIC text rule. Never starts cmd or any vendor binary. */
public final class LicenseRecoverReadOnlyWmicCpuIdentity {
    private LicenseRecoverReadOnlyWmicCpuIdentity() { }
    private static final int LIMIT=32768;
    private static final String SOURCE="independent-wmic-cpu-text-v1";
    private static final String SCOPE="扫描进程命令环境；未读取 IIS 身份或进程缓存";
    private static Identity cached;
    private static long cachedAt;
    private static String cachedDirectory;

    public static final class Identity {
        public final boolean complete;
        public final String value,reason,source=SOURCE,scope=SCOPE;
        /** Synthetic consumers may inject fixtures; production obtains current(). */
        public Identity(boolean complete,String value,String reason) {
            if(complete&&(value==null||!value.matches("[\\x20-\\x7e]{1,256}")))
                throw new IllegalArgumentException("invalid CPU identity fixture");
            this.complete=complete;this.value=complete?value:null;this.reason=reason==null?"":reason;
        }
        @Override public String toString(){return "WmicCpuIdentity{complete="+complete+", source="+SOURCE+"}";}
    }
    private static Identity unknown(String reason){return new Identity(false,null,reason);}
    public static synchronized Identity current() {
        String directory=System.getProperty("user.dir","");long now=System.nanoTime();
        if(cached!=null&&directory.equals(cachedDirectory)&&now-cachedAt<TimeUnit.SECONDS.toNanos(5))return cached;
        cached=read();cachedAt=System.nanoTime();cachedDirectory=directory;return cached;
    }
    private static Identity read() {
        if(!System.getProperty("os.name","").toLowerCase(Locale.ROOT).startsWith("windows"))
            return unknown("当前平台未审核 WMIC 文本身份规则");
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);int remainingOutput=LIMIT;String stage="系统路径核对";
        try {
            String systemRoot=System.getenv("SystemRoot"),path=System.getenv("PATH"),extensions=System.getenv("PATHEXT");
            if(systemRoot==null||path==null||extensions==null||!extensions.toUpperCase(Locale.ROOT).contains(".EXE"))
                return unknown("命令搜索环境不完整，未猜测 CPU 身份");
            Path cwd=Paths.get(System.getProperty("user.dir","")).toAbsolutePath().normalize();
            List<Path> search=new ArrayList<Path>();search.add(cwd);
            for(String segment:path.split(";",-1)) {
                if(segment.isEmpty()){search.add(cwd);continue;}
                if(!segment.matches("[A-Za-z]:[\\\\/].*")||segment.indexOf('"')>=0||segment.indexOf('%')>=0)
                    return unknown("命令搜索路径包含未审核布局，未执行候选程序");
                search.add(Paths.get(segment).toAbsolutePath().normalize());
            }
            Path system=Paths.get(systemRoot,"System32");
            Path powershell=system.resolve("WindowsPowerShell/v1.0/powershell.exe");
            Path where=system.resolve("where.exe"),cmd=system.resolve("cmd.exe"),wmic=system.resolve("wbem/WMIC.exe");
            for(Path executable:Arrays.asList(powershell,where,cmd,wmic))requireExecutable(executable);
            // Fixed code only: registry values and search locations never become commands.
            String script="$ErrorActionPreference='Stop';"
                +"foreach($h in @([Microsoft.Win32.RegistryHive]::LocalMachine,[Microsoft.Win32.RegistryHive]::CurrentUser)){"
                +"foreach($v in @([Microsoft.Win32.RegistryView]::Registry32,[Microsoft.Win32.RegistryView]::Registry64)){"
                +"$b=[Microsoft.Win32.RegistryKey]::OpenBaseKey($h,$v);try{$k=$b.OpenSubKey('Software\\Microsoft\\Command Processor',$false);"
                +"if($null -ne $k){try{$a=$k.GetValue('AutoRun',$null,[Microsoft.Win32.RegistryValueOptions]::DoNotExpandEnvironmentNames);"
                +"if($null -ne $a -and [string]$a -ne ''){[Console]::WriteLine('LRW1-AUTORUN');exit 0}}finally{$k.Dispose()}}}finally{$b.Dispose()}}};"
                +"foreach($p in (@([IO.Directory]::GetCurrentDirectory())+($env:PATH -split ';'))){if($p -eq ''){continue};"
                +"$d=New-Object IO.DriveInfo ([IO.Path]::GetPathRoot($p));if($d.DriveType -ne [IO.DriveType]::Fixed){[Console]::WriteLine('LRW1-PATH');exit 0}};"
                +"[Console]::WriteLine('LRW1-CLEAR')";
            stage="只读命令环境核对";
            Captured policy=run(Arrays.asList(powershell.toString(),"-NoLogo","-NoProfile","-NonInteractive","-Command",script),cwd,deadline,remainingOutput);
            remainingOutput-=policy.total;
            String blocked=policyProblem(policy.stdout,policy.exit,policy.stderrSize);
            if(blocked!=null)return unknown(blocked);
            stage="本地搜索目录核对";
            checkSearchPaths(search);
            stage="命令处理器路径核对";
            Captured resolvedCmd=run(Arrays.asList(where.toString(),"/F","cmd.exe"),cwd,deadline,remainingOutput);
            remainingOutput-=resolvedCmd.total;
            if(resolvedCmd.exit!=0||resolvedCmd.stderrSize!=0||!onlyTrustedResolution(resolvedCmd.stdout,cmd))
                return unknown("命令处理器解析路径未证明一致，未执行候选程序");
            stage="WMIC 路径核对";
            Captured resolvedWmic=run(Arrays.asList(where.toString(),"/F","wmic"),cwd,deadline,remainingOutput);
            remainingOutput-=resolvedWmic.total;
            if(resolvedWmic.exit!=0||resolvedWmic.stderrSize!=0||!onlyTrustedResolution(resolvedWmic.stdout,wmic))
                return unknown("WMIC 搜索路径未证明为可信系统程序，未执行候选程序");
            requireExecutable(wmic);
            stage="WMIC 文本查询";
            Captured result=run(Arrays.asList(wmic.toString(),"cpu","get","processorId"),cwd,deadline,remainingOutput);
            // Native reads stdout only; stderr is drained and bounded separately.
            return parse(result.stdout);
        }catch(InterruptedException e){Thread.currentThread().interrupt();return unknown("WMIC 只读身份查询已取消");}
        catch(Exception e){return unknown("WMIC 只读身份未完成（"+stage+"），未猜测原生身份");}
    }
    private static void requireExecutable(Path p)throws IOException {
        checkPath(p,false);
        if(!Files.readAttributes(p,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS).isRegularFile())throw new IOException("not-regular");
    }
    private static void checkPath(Path path,boolean allowMissing)throws IOException {
        Path p=path.toAbsolutePath().normalize(),cursor=p.getRoot();
        if(p.toString().startsWith("\\\\"))throw new IOException("remote-path");
        for(Path part:p) {
            cursor=cursor.resolve(part);
            try {
                BasicFileAttributes a=Files.readAttributes(cursor,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
                if(a.isSymbolicLink()||a.isOther())throw new IOException("linked-path");
            }catch(NoSuchFileException missing){if(allowMissing)return;throw missing;}
        }
    }
    /** Reuse ancestor checks only within this one metadata pass, never across queries. */
    private static void checkSearchPaths(List<Path> search)throws IOException {
        Set<Path> checked=new HashSet<Path>();
        for(Path path:search) {
            Path p=path.toAbsolutePath().normalize(),cursor=p.getRoot();
            if(p.toString().startsWith("\\\\"))throw new IOException("remote-path");
            for(Path part:p) {
                cursor=cursor.resolve(part);if(checked.contains(cursor))continue;
                try {
                    BasicFileAttributes a=Files.readAttributes(cursor,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
                    if(a.isSymbolicLink()||a.isOther())throw new IOException("linked-search-path");
                    checked.add(cursor);
                }catch(NoSuchFileException missing){break;}
            }
        }
    }
    /** WHERE is read-only. Any alternate candidate is rejected, rather than choosing a favorable one. */
    static String policyProblem(byte[] stdout,int exit,int stderrSize) {
        String flag=ascii(stdout);
        if(exit!=0||stderrSize!=0||flag==null)return "只读命令环境审查未完成";
        if("LRW1-AUTORUN".equals(flag.trim()))return "命令处理器含未审核 AutoRun，未执行该配置";
        return "LRW1-CLEAR".equals(flag.trim())?null:"命令搜索位置不是已审核本地固定磁盘";
    }
    static boolean onlyTrustedResolution(byte[] bytes,Path trusted) {
        String raw=ascii(bytes);if(raw==null||raw.length()>LIMIT)return false;boolean found=false;
        for(String line:raw.split("\\r?\\n",-1)) {
            if(line.trim().isEmpty())continue;
            String value=line.trim();
            if(value.length()<3||value.charAt(0)!='"'||value.charAt(value.length()-1)!='"')return false;
            value=value.substring(1,value.length()-1);if(value.indexOf('"')>=0)return false;
            try {
                Path actual=Paths.get(value).toAbsolutePath().normalize();
                if(!actual.toString().equalsIgnoreCase(trusted.toAbsolutePath().normalize().toString()))return false;
            }catch(InvalidPathException e){return false;}
            found=true;
        }
        return found;
    }
    /** Only ASCII bytes have proven code-page-independent equivalence. No BOM stripping/UTF16 guessing. */
    private static String ascii(byte[] bytes) {
        if(bytes==null||bytes.length>LIMIT)return null;
        for(byte b:bytes){int v=b&255;if(v>126||(v<32&&v!=9&&v!=10&&v!=13))return null;}
        return new String(bytes,StandardCharsets.US_ASCII);
    }
    /** Pure exact Windows Getid text predicate; caller first proves executable/environment. */
    static Identity parse(byte[] bytes) {
        String raw=ascii(bytes);
        if(raw==null)return unknown("WMIC 输出编码或边界未证明与原生文本读取一致");
        List<String> lines=new ArrayList<String>();
        for(String line:raw.split("\\r\\n",-1))if(!line.isEmpty())lines.add(line);
        String value="unknow";
        for(int i=0;i<lines.size();i++)if("ProcessorId".equals(lines.get(i).trim())) {
            if(i+1>=lines.size())return knownUnknown();
            value=lines.get(i+1).trim();
        }
        if(value.isEmpty())value="unknow";
        if(!value.matches("[\\x20-\\x7e]{1,256}"))return unknown("WMIC 身份文本超出已审核范围");
        return new Identity(true,value,"独立只读 WMIC 文本规则完成；仅限扫描进程环境，不证明 IIS 缓存");
    }
    private static Identity knownUnknown(){return new Identity(true,"unknow","已审核文本规则进入明确 unknow 回退；不代替未审核环境");}
    private static final class Captured {
        final byte[] stdout;final int stderrSize,total,exit;
        Captured(byte[] stdout,int stderrSize,int total,int exit){this.stdout=stdout;this.stderrSize=stderrSize;this.total=total;this.exit=exit;}
    }
    private static Captured run(List<String> command,Path cwd,long deadline,int budget)throws Exception {
        if(budget<=0||System.nanoTime()>=deadline)throw new IOException("query-bound");
        Process process=new ProcessBuilder(command).directory(cwd.toFile()).start();
        ByteArrayOutputStream out=new ByteArrayOutputStream(),err=new ByteArrayOutputStream();
        final int[] count={0};final boolean[] failed={false};Object guard=new Object();
        Thread stdout=pump(process.getInputStream(),out,process,guard,count,failed,budget);
        Thread stderr=pump(process.getErrorStream(),err,process,guard,count,failed,budget);
        try {
            long left=deadline-System.nanoTime();
            if(left<=0||!process.waitFor(left,TimeUnit.NANOSECONDS))throw new IOException("query-timeout");
            for(Thread thread:Arrays.asList(stdout,stderr))if(thread.isAlive()) {
                left=deadline-System.nanoTime();if(left<=0)throw new IOException("query-timeout");
                thread.join(Math.max(1,TimeUnit.NANOSECONDS.toMillis(left)));
            }
            synchronized(guard) {
                if(failed[0]||stdout.isAlive()||stderr.isAlive())throw new IOException("query-output");
                return new Captured(out.toByteArray(),err.size(),count[0],process.exitValue());
            }
        }finally{if(process.isAlive())process.destroyForcibly();}
    }
    private static Thread pump(final InputStream input,final ByteArrayOutputStream output,final Process process,
            final Object guard,final int[] count,final boolean[] failed,final int budget) {
        Thread thread=new Thread(()->{
            try(InputStream in=input) {
                byte[] buffer=new byte[1024];int n;
                while((n=in.read(buffer))!=-1)synchronized(guard) {
                    if(count[0]+n>budget){failed[0]=true;process.destroyForcibly();return;}
                    count[0]+=n;output.write(buffer,0,n);
                }
            }catch(IOException e){synchronized(guard){failed[0]=true;}}
        },"LicenseRecover-WmicReadOnlyOutput");
        thread.setDaemon(true);thread.start();return thread;
    }
}
