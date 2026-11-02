import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Optional, read-only physical 32-bit YunClient source. Values never enter diagnostics. */
public final class LicenseRecoverReadOnlyDotNetRegistrySource {
    private LicenseRecoverReadOnlyDotNetRegistrySource() { }
    private static final int LIMIT=262144;
    public static final class Snapshot {
        public final boolean complete, present;
        final String softNo, localSoftNo;
        public final String reason;
        Snapshot(boolean complete,boolean present,String softNo,String localSoftNo,String reason) {
            this.complete=complete;this.present=present;this.softNo=softNo;this.localSoftNo=localSoftNo;this.reason=reason;
        }
        public boolean hasOverride() {return complete&&present&&nonblank(softNo);}
        public boolean hasLocalPayload() {return complete&&present&&nonblank(localSoftNo);}
        boolean same(Snapshot other) {
            return other!=null&&complete&&other.complete&&present==other.present
                    &&Objects.equals(softNo,other.softNo)&&Objects.equals(localSoftNo,other.localSoftNo);
        }
        public String observation() {
            if(!complete)return "本机组合注册来源未完成只读核验；"+reason;
            if(!present)return "当前扫描上下文未发现已审核位置的组合注册配置；不排除站点缓存或其他授权方式";
            if(hasLocalPayload())return "发现本机组合注册配置及候选本地载荷；网站产品映射、导入条件与机器覆盖规则尚未形成完整接受证明";
            if(hasOverride())return "发现本机组合注册配置和机器覆盖号，但没有组合本地载荷；配置存在不证明授权有效，也不排除其他授权方式";
            return "已读取本机组合注册配置，但未取得可验证的覆盖号或本地载荷；未判整个站点未激活";
        }
        @Override public String toString() {return observation();}
    }
    private static boolean nonblank(String s) {return s!=null&&!s.trim().isEmpty();}
    private static Snapshot unavailable(String reason) {return new Snapshot(false,false,null,null,reason);}

    /** No cache: a second call must actually observe possible changes to registry values. */
    public static Snapshot readCurrent() {
        if(!System.getProperty("os.name","").toLowerCase(Locale.ROOT).startsWith("windows"))
            return unavailable("当前平台未审核注册表视图");
        String root=System.getenv("SystemRoot");
        if(!LicenseRecoverReadOnlyWmicCpuIdentity.trustedWindowsRoot(root))
            return unavailable("系统目录未通过可信路径核验，未执行外部读取器");
        File executable=new File(root,"System32/WindowsPowerShell/v1.0/powershell.exe");
        if(!unlinkedRegularFile(executable.toPath()))return unavailable("系统只读读取器路径不可验证");
        // Fixed path and two relevant values only. No CRM user/password, vendor code,
        // registry expansion, writable key, temporary script or network request.
        String script="$ErrorActionPreference='Stop';$b=[Microsoft.Win32.RegistryKey]::OpenBaseKey([Microsoft.Win32.RegistryHive]::LocalMachine,[Microsoft.Win32.RegistryView]::Registry32);"
                +"try{$k=$b.OpenSubKey('SOFTWARE\\YunClient\\Soft',$false);try{if($null -eq $k){[Console]::WriteLine('LRREG1');[Console]::WriteLine('ABSENT');[Console]::WriteLine('END')}else{"
                +"$lines=New-Object System.Collections.Generic.List[string];$lines.Add('LRREG1');$lines.Add('PRESENT');"
                +"foreach($n in @('SoftNo','LocalSoftNo')){if($k.GetValueNames() -notcontains $n){$lines.Add($n+[char]9+'~');continue};"
                +"if($k.GetValueKind($n) -ne [Microsoft.Win32.RegistryValueKind]::String){[Console]::WriteLine('LRREG1-UNSUPPORTED');exit 0};"
                +"$v=$k.GetValue($n,$null,[Microsoft.Win32.RegistryValueOptions]::DoNotExpandEnvironmentNames);if($v -isnot [string] -or $v.Length -gt 65536){throw 'bound'};"
                +"$lines.Add($n+[char]9+[Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($v)))};$lines.Add('END');foreach($l in $lines){[Console]::WriteLine($l)}}}finally{if($null -ne $k){$k.Dispose()}}}finally{$b.Dispose()}";
        Process process=null;final ByteArrayOutputStream output=new ByteArrayOutputStream();final boolean[] overflow={false};
        try {
            process=new ProcessBuilder(executable.getAbsolutePath(),"-NoLogo","-NoProfile","-NonInteractive","-Command",script).redirectErrorStream(true).start();
            final Process running=process;
            Thread pump=new Thread(()->{try(InputStream in=running.getInputStream()) {
                byte[] block=new byte[2048];int n;while((n=in.read(block))!=-1)synchronized(output) {
                    if(output.size()+n>LIMIT){overflow[0]=true;running.destroyForcibly();return;}output.write(block,0,n);
                }
            }catch(IOException ignored){ }},"LicenseRecover-RegistrySource");
            pump.setDaemon(true);pump.start();
            if(!process.waitFor(3000,TimeUnit.MILLISECONDS)){process.destroyForcibly();return unavailable("组合注册来源读取超时");}
            pump.join(300);
            if(process.exitValue()!=0||pump.isAlive()||overflow[0])return unavailable("组合注册来源读取不完整或权限不足");
            synchronized(output){return parse(output.toByteArray());}
        }catch(InterruptedException e){Thread.currentThread().interrupt();return unavailable("组合注册来源读取已取消");}
        catch(IOException|SecurityException e){return unavailable("组合注册来源读取不可用");}
        finally{if(process!=null&&process.isAlive())process.destroyForcibly();}
    }
    /** Reject symlinks/junctions along the executable path before starting any process. */
    static boolean unlinkedRegularFile(Path executable) {
        try {
            Path p=executable.toAbsolutePath().normalize(),cursor=p.getRoot();
            if(cursor==null||p.toString().startsWith("\\\\"))return false;
            for(Path part:p) {
                cursor=cursor.resolve(part);
                BasicFileAttributes a=Files.readAttributes(cursor,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
                if(a.isSymbolicLink()||a.isOther())return false;
            }
            return Files.readAttributes(p,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS).isRegularFile();
        }catch(IOException|SecurityException e){return false;}
    }
    static Snapshot parse(byte[] bytes) {
        if(bytes==null||bytes.length>LIMIT)return unavailable("组合注册来源输出超限");
        String raw=new String(bytes,StandardCharsets.US_ASCII);
        if("LRREG1-UNSUPPORTED".equals(raw.trim()))return unavailable("组合注册值类型尚未审核，未展开或执行其中内容");
        String[] lines=raw.replace("\r\n","\n").split("\n",-1);
        int count=lines.length;if(count>0&&lines[count-1].isEmpty())count--;
        try {
            if(count==3&&"LRREG1".equals(lines[0])&&"ABSENT".equals(lines[1])&&"END".equals(lines[2]))
                return new Snapshot(true,false,null,null,"已读取明确的物理32位注册表视图");
            if(count!=5||!"LRREG1".equals(lines[0])||!"PRESENT".equals(lines[1])||!"END".equals(lines[4]))throw new IllegalArgumentException();
            String soft=decode(lines[2],"SoftNo"),local=decode(lines[3],"LocalSoftNo");
            return new Snapshot(true,true,soft,local,"已读取明确的物理32位注册表视图");
        }catch(IllegalArgumentException e){return unavailable("组合注册来源输出格式未通过核验");}
    }
    private static String decode(String line,String name) {
        String prefix=name+"\t";if(!line.startsWith(prefix))throw new IllegalArgumentException();
        String b64=line.substring(prefix.length());if("~".equals(b64))return null;
        if(!b64.matches("[A-Za-z0-9+/]*={0,2}"))throw new IllegalArgumentException();
        byte[] bytes=Base64.getDecoder().decode(b64);String s=new String(bytes,StandardCharsets.UTF_8);
        if(s.length()>65536||s.indexOf('\0')>=0||!Arrays.equals(bytes,s.getBytes(StandardCharsets.UTF_8)))throw new IllegalArgumentException();
        return s;
    }
}
