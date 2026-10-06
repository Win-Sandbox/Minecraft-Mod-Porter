from pathlib import Path
import re
ROOT=Path(__file__).resolve().parents[1]
BUILD=ROOT/'build.gradle'
TEST=ROOT/'src/test/java'
text=BUILD.read_text(encoding='utf-8')
refs=[]
for m in re.finditer(r'io\.modporter\.[A-Za-z0-9_.]+',text): refs.append(m.group(0))
refs=set(x for x in refs if x.endswith('Test'))
missing=[];bad=[]
for fqcn in sorted(refs):
 rel=Path(*fqcn.split('.')).with_suffix('.java'); p=TEST/rel
 if not p.is_file():missing.append(fqcn);continue
 s=p.read_text(encoding='utf-8')
 if not re.search(r'public\s+static\s+void\s+main\s*\(\s*String\s*(?:\[\]|\.\.\.)',s):bad.append((fqcn,'missing main'))
print('registered test-like FQCN:',len(refs))
print('missing:',missing)
print('bad:',bad)
raise SystemExit(1 if missing or bad else 0)
