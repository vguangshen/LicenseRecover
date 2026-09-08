from pathlib import Path
p=Path('.github/workflows/verify-source.yml')
s=p.read_text(encoding='utf-8')
old='''on:\n  push:\n    branches: [ main, "refactor/**" ]\n  pull_request:\n\njobs:\n'''
new='''on:\n  push:\n    branches: [ main, "refactor/**" ]\n  pull_request:\n\nconcurrency:\n  group: verify-source-${{ github.ref }}\n  cancel-in-progress: true\n\njobs:\n'''
if old not in s: raise SystemExit('concurrency anchor missing')
s=s.replace(old,new,1)
s=s.replace('''            sudo apt-get update\n            sudo apt-get install -y --no-install-recommends gcc-mingw-w64-x86-64 binutils-mingw-w64-x86-64\n''','''            timeout 120s sudo apt-get -o Acquire::Retries=3 -o Acquire::http::Timeout=30 -o Acquire::https::Timeout=30 update\n            timeout 180s sudo apt-get -o Acquire::Retries=3 -o Acquire::http::Timeout=30 -o Acquire::https::Timeout=30 install -y --no-install-recommends gcc-mingw-w64-x86-64 binutils-mingw-w64-x86-64\n''',1)
p.write_text(s,encoding='utf-8',newline='\n')
