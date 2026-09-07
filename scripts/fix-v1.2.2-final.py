#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_once(rel, old, new):
    path = ROOT / rel
    text = path.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"patch anchor not found in {rel}: {old[:120]!r}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8", newline="\n")


# Ensure one-click logs always go to the actual Runtime Log panel, not one of
# the new read-only Java-plan text areas.
replace_once(
    "src/main/java/LicenseRecoverModernGUIUiPatchLauncher.java",
    '''        final JTextArea logArea = findTextArea(frame.getContentPane());
''',
    '''        final JTextArea logArea = findTextAreaInTitledPanel(frame.getContentPane(), "运行日志");
''')

replace_once(
    "src/main/java/LicenseRecoverModernGUIUiPatchLauncher.java",
    '''    private static JTextArea findTextArea(Container root) {
''',
    '''    private static JTextArea findTextAreaInTitledPanel(Container root, String title) {
        JPanel panel = findTitledPanel(root, title);
        return panel == null ? null : findTextArea(panel);
    }

    private static JTextArea findTextArea(Container root) {
''')

# Make batch cancellation responsive even while the child process is quiet.
# The output reader runs independently; the worker thread polls the Process and
# can therefore react to SwingWorker.cancel(true) without blocking in readLine.
replace_once(
    "src/main/java/LicenseRecoverModernGUIAutoRecovery.java",
    '''    private static int run(List<String>cmd,Consumer<String>log)throws Exception{
        Process p=new ProcessBuilder(cmd).redirectErrorStream(true).start();
        try{
            BufferedReader r=new BufferedReader(new InputStreamReader(p.getInputStream(),StandardCharsets.UTF_8));
            String s;while((s=r.readLine())!=null)log.accept(s+"\\n");
            return p.waitFor();
        }catch(InterruptedException ex){
            p.destroy();
            try{p.waitFor();}catch(InterruptedException again){Thread.currentThread().interrupt();}
            try{if(p.isAlive())p.destroyForcibly();}catch(Throwable ignore){}
            Thread.currentThread().interrupt();
            throw ex;
        }
    }
''',
    '''    private static int run(List<String>cmd,Consumer<String>log)throws Exception{
        final Process p=new ProcessBuilder(cmd).redirectErrorStream(true).start();
        final BufferedReader r=new BufferedReader(new InputStreamReader(p.getInputStream(),StandardCharsets.UTF_8));
        Thread pump=new Thread(new Runnable(){
            public void run(){
                try{
                    String s;while((s=r.readLine())!=null)log.accept(s+"\\n");
                }catch(IOException ignore){}
            }
        },"LicenseRecover-OneClick-Output");
        pump.setDaemon(true);
        pump.start();
        try{
            while(true){
                if(Thread.currentThread().isInterrupted())throw new InterruptedException("cancelled");
                if(p.waitFor(200L,java.util.concurrent.TimeUnit.MILLISECONDS)){
                    try{pump.join(1000L);}catch(InterruptedException ex){throw ex;}
                    return p.exitValue();
                }
            }
        }catch(InterruptedException ex){
            p.destroy();
            try{
                if(!p.waitFor(1000L,java.util.concurrent.TimeUnit.MILLISECONDS))p.destroyForcibly();
            }catch(InterruptedException again){
                p.destroyForcibly();
            }
            Thread.currentThread().interrupt();
            throw ex;
        }finally{
            try{r.close();}catch(IOException ignore){}
        }
    }
''')

print("final v1.2.2 fixes applied")
