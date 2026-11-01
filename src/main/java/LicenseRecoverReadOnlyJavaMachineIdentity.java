import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Independent equivalent of the reviewed Java Windows C-volume primary identity branch. */
public final class LicenseRecoverReadOnlyJavaMachineIdentity {
    private LicenseRecoverReadOnlyJavaMachineIdentity() { }
    private static Identity cached;
    private static long cachedAt;
    public static final class Identity {
        public final boolean complete;
        public final String machineId, rule, reason;
        public Identity(boolean complete,String machineId,String rule,String reason) {
            this.complete=complete;this.machineId=machineId;this.rule=rule;this.reason=reason;
        }
    }
    private static Identity unknown(String reason) {
        return new Identity(false,null,"windows-dir-c-gbk-primary-v1",reason);
    }
    public static synchronized Identity current() {
        if(cached!=null&&System.nanoTime()-cachedAt<TimeUnit.SECONDS.toNanos(5))return cached;
        cached=read();cachedAt=System.nanoTime();return cached;
    }
    private static Identity read() {
        if(!System.getProperty("os.name","").toLowerCase(Locale.ROOT).startsWith("windows"))
            return unknown("Java 当前平台的机器绑定规则未审核");
        String systemRoot=System.getenv("SystemRoot");
        if(systemRoot==null)return unknown("缺少本机系统目录");
        File powershell=new File(systemRoot,"System32/WindowsPowerShell/v1.0/powershell.exe");
        if(!powershell.isFile())return unknown("Java 本机身份独立读取不可用");
        // Refuse AutoRun instead of executing it. /d also prevents either registry
        // AutoRun branch from running. No application input is interpolated here.
        String script="$ErrorActionPreference='Stop';$blocked=$false;"
            +"foreach($h in @([Microsoft.Win32.RegistryHive]::LocalMachine,[Microsoft.Win32.RegistryHive]::CurrentUser)){"
            +"foreach($v in @([Microsoft.Win32.RegistryView]::Registry32,[Microsoft.Win32.RegistryView]::Registry64)){"
            +"$b=[Microsoft.Win32.RegistryKey]::OpenBaseKey($h,$v);try{$k=$b.OpenSubKey('Software\\Microsoft\\Command Processor',$false);"
            +"if($null -ne $k){try{$a=$k.GetValue('AutoRun',$null,[Microsoft.Win32.RegistryValueOptions]::DoNotExpandEnvironmentNames);"
            +"if($null -ne $a -and [string]$a -ne ''){$blocked=$true}}finally{$k.Dispose()}}}finally{$b.Dispose()}}};"
            +"if($blocked){[Console]::WriteLine('LRJID1-AUTORUN');exit 0};"
            +"$s=New-Object System.Diagnostics.ProcessStartInfo;"
            +"$s.FileName=[IO.Path]::Combine($env:SystemRoot,'System32\\cmd.exe');$s.Arguments='/d /c dir C:';"
            +"$s.UseShellExecute=$false;$s.CreateNoWindow=$true;$s.RedirectStandardOutput=$true;$s.RedirectStandardError=$true;"
            +"$s.StandardOutputEncoding=[Text.Encoding]::GetEncoding(936);"
            +"$p=New-Object System.Diagnostics.Process;$p.StartInfo=$s;[void]$p.Start();"
            +"$phrase=[string][char]0x5377+[char]0x7684+[char]0x5e8f+[char]0x5217+[char]0x53f7+[char]0x662f+' ';"
            +"$value=$null;try{for($i=0;$i -lt 32;$i++){$l=$p.StandardOutput.ReadLine();if($null -eq $l){break};"
            +"$at=$l.IndexOf($phrase,[StringComparison]::Ordinal);if($at -ge 0){$value=$l.Substring($at+$phrase.Length);break}}}"
            +"finally{if(!$p.HasExited){$p.Kill()};$p.Dispose()};"
            +"if($null -eq $value -or $value.Length -eq 0){[Console]::WriteLine('LRJID1-FALLBACK');exit 0};"
            +"[Console]::WriteLine('LRJID1');[Console]::WriteLine([Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($value)));"
            +"[Console]::WriteLine('END')";
        Process process=null;final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        final boolean[] overflow={false};
        try {
            process=new ProcessBuilder(powershell.getAbsolutePath(),"-NoLogo","-NoProfile","-NonInteractive","-Command",script)
                    .redirectErrorStream(true).start();
            final Process running=process;
            Thread pump=new Thread(()->{
                try(InputStream in=running.getInputStream()) {
                    byte[] buffer=new byte[1024];int n;
                    while((n=in.read(buffer))!=-1) {
                        synchronized(bytes){if(bytes.size()+n>16384){overflow[0]=true;running.destroyForcibly();return;}bytes.write(buffer,0,n);}
                    }
                }catch(IOException ignored){ }
            },"LicenseRecover-JavaVolumeIdentity");
            pump.setDaemon(true);pump.start();
            if(!process.waitFor(3000,TimeUnit.MILLISECONDS)){process.destroyForcibly();return unknown("Java 本机身份独立读取超时");}
            pump.join(300);
            if(process.exitValue()!=0||pump.isAlive()||overflow[0])return unknown("Java 本机身份独立读取未完成");
            synchronized(bytes){return parse(bytes.toByteArray());}
        }catch(InterruptedException ex){Thread.currentThread().interrupt();return unknown("Java 本机身份读取已取消");}
        catch(IOException|SecurityException ex){return unknown("Java 本机身份独立读取失败");}
        finally{if(process!=null&&process.isAlive())process.destroyForcibly();}
    }
    static Identity parse(byte[] bytes) {
        if(bytes==null||bytes.length>16384)return unknown("Java 本机身份输出格式无效");
        String raw=new String(bytes,StandardCharsets.US_ASCII).trim();
        if("LRJID1-AUTORUN".equals(raw))return unknown("命令处理器存在未审核 AutoRun，未执行该配置");
        if("LRJID1-FALLBACK".equals(raw))return unknown("Java 本机身份需要尚未审核的回退分支");
        String[] lines=raw.split("\\r?\\n",-1);
        try {
            if(lines.length!=3||!"LRJID1".equals(lines[0])||!"END".equals(lines[2])
                    ||!lines[1].matches("[A-Za-z0-9+/]+={0,2}"))throw new IllegalArgumentException();
            byte[] valueBytes=Base64.getDecoder().decode(lines[1]);
            String value=new String(valueBytes,StandardCharsets.UTF_8);
            if(value.isEmpty()||value.length()>256||!Arrays.equals(valueBytes,value.getBytes(StandardCharsets.UTF_8))
                    ||!value.matches("[\\x20-\\x7e]+"))throw new IllegalArgumentException();
            // Native getIdentifierByWindows does NOT trim. getMotherboardSN then
            // truncates the first 16 chars or right-pads with literal zeroes.
            StringBuilder id=new StringBuilder(value);while(id.length()<16)id.append('0');
            return new Identity(true,id.substring(0,16),"windows-dir-c-gbk-primary-v1",
                    "独立读取匹配 Java 已审核的 Windows C卷首选机器规则");
        }catch(IllegalArgumentException ex){return unknown("Java 本机身份输出格式无效");}
    }
}
