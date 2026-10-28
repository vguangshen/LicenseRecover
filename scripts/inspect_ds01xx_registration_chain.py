#!/usr/bin/env python3
"""Read-only .NET IL call-reference inspection for DS01xx registration flow.

Use this on *already unpacked* assemblies; no vendor executable is run.
Only selected type/method names and call edges are emitted. Never exports
application config, plaintext license strings, registration codes, or IL bodies.
Python standard library only.
"""
from __future__ import annotations

import argparse
import json
import struct
from pathlib import Path
from itmc_foap_unpack_stdlib import PE, u16, u32, u64, align

# ECMA-335 CIL instructions which have an immediate operand. Everything else
# in the supported one- and two-byte opcode spaces is operandless.
BYTE_OPS = set(range(0x0e,0x14)) | {0x1f,0xde,0xfe12,0xfe19} | set(range(0x2b,0x38))
WORD_OPS = {0xfe09,0xfe0a,0xfe0b,0xfe0c,0xfe0d,0xfe0e}
DWORD_OPS = {
    0x20,0x22,0x27,0x28,0x29,0x38,0x39,0x3a,0x3b,0x3c,0x3d,0x3e,0x3f,
    0x40,0x41,0x42,0x43,0x44,0x6f,0x70,0x71,0x72,0x73,0x74,0x75,
    0x79,0x7b,0x7c,0x7d,0x7e,0x7f,0x80,0x81,0x8c,0x8d,0x8f,
    0xa3,0xa4,0xa5,0xc2,0xc6,0xd0,0xdd,0xfe06,0xfe07,0xfe15,0xfe16,0xfe1c
}
QWORD_OPS = {0x21,0x23}
CALL_OPS = {0x28,0x6f,0x73,0xfe06,0xfe07}


class Metadata:
    def __init__(self, pe: PE):
        self.pe = pe
        b = pe.b
        root = pe.meta
        if u32(b,root) != 0x424a5342:
            raise ValueError('missing .NET CLR metadata')
        p = align(root+16+u32(b,root+12),4)
        count=u16(b,p+2);p+=4
        self.streams={}
        for _ in range(count):
            rel=u32(b,p);size=u32(b,p+4)
            e=b.find(b'\0',p+8)
            if e<0:raise ValueError('unterminated metadata stream name')
            name=b[p+8:e].decode('ascii')
            self.streams[name]=(root+rel,size)
            p=align(e+1,4)
        table = self.streams.get('#~') or self.streams.get('#-')
        if not table or '#Strings' not in self.streams:
            raise ValueError('missing metadata tables or strings')
        t=table[0];flags=b[t+6];valid=u64(b,t+8);p=t+24
        self.rows={}
        for i in range(64):
            if valid>>i&1:self.rows[i]=u32(b,p);p+=4
        self.si=4 if flags&1 else 2
        self.bi=4 if flags&4 else 2
        self.gi=4 if flags&2 else 2
        ix=self.ix;coded=self.coded;si=self.si;bi=self.bi;gi=self.gi
        self.sizes={
            0:2+si+3*gi, 1:coded((0,26,35,1),2)+2*si,
            2:4+2*si+coded((1,2,27),2)+ix(4)+ix(6), 3:ix(4),
            4:2+si+bi, 5:ix(6), 6:8+si+bi+ix(8), 7:ix(8),
            8:4+si, 9:ix(2)+coded((1,2,27),2),
            10:coded((2,1,26,6,27),3)+si+bi,
        }
        self.starts={}
        for k in range(11):
            self.starts[k]=p
            p+=self.sizes[k]*self.rows.get(k,0)
        self.methods={}
        self.method_names={}
        for i in range(1,self.rows.get(6,0)+1):
            o=self.offset(6,i)
            self.method_names[i]=self.string(self.read(o+8,si))
            self.methods[i]=u32(b,o)
        self.types={}
        for i in range(1,self.rows.get(2,0)+1):
            o=self.offset(2,i)
            name=self.string(self.read(o+4,si))
            ns=self.string(self.read(o+4+si,si))
            method_start=self.read(o+4+2*si+coded((1,2,27),2)+ix(4),ix(6))
            self.types[i]=((ns+'.' if ns else '')+name,method_start)
        self.qualified_methods={}
        types=list(self.types.values())
        for k,(type_name,start) in enumerate(types):
            end=types[k+1][1] if k+1<len(types) else len(self.methods)+1
            for rid in range(max(1,start),min(end,len(self.methods)+1)):
                self.qualified_methods[rid]=type_name+'.'+self.method_names[rid]
        self.members={}
        for i in range(1,self.rows.get(10,0)+1):
            o=self.offset(10,i)
            size=coded((2,1,26,6,27),3)
            parent=self.read(o,size)
            name=self.string(self.read(o+size,si))
            tag=parent&7;rid=parent>>3
            if tag==0:
                typ=self.types.get(rid,('unknown TypeDef',0))[0]
            elif tag==1:
                typ=self.type_ref(rid)
            else:
                typ='external'
            self.members[i]=typ+'.'+name

    def ix(self,table):
        return 4 if self.rows.get(table,0)>=0x10000 else 2

    def coded(self,tables,bits):
        return 4 if max(self.rows.get(i,0) for i in tables)>=(1<<(16-bits)) else 2

    def offset(self,table,rid):
        if rid<1 or rid>self.rows.get(table,0):raise ValueError('metadata RID outside table')
        return self.starts[table]+(rid-1)*self.sizes[table]

    def read(self,o,n):
        return int.from_bytes(self.pe.b[o:o+n],'little')

    def string(self,offset):
        start,sz=self.streams['#Strings']
        if offset<0 or offset>=sz:raise ValueError('string offset out of range')
        p=start+offset
        end=self.pe.b.find(b'\0',p,start+sz)
        if end<0:raise ValueError('unterminated metadata string')
        return self.pe.b[p:end].decode('utf-8','replace')

    def type_ref(self,rid):
        o=self.offset(1,rid)
        c=self.coded((0,26,35,1),2)
        name=self.string(self.read(o+c,self.si))
        ns=self.string(self.read(o+c+self.si,self.si))
        return (ns+'.' if ns else '')+name

    def target_name(self,token):
        table=token>>24;rid=token&0xffffff
        if table==6:return self.qualified_methods.get(rid,'unknown MethodDef')
        if table==10:return self.members.get(rid,'unknown MemberRef')
        return 'unresolved token %08X'%token


def call_edges(pe:PE,meta:Metadata,rid:int):
    """Parse the CIL code region of a MethodDef, retaining method call edges."""
    b=pe.b
    rva=meta.methods[rid]
    if not rva:return []
    p=pe.rva_to_offset(rva)
    if b[p]&3==2:
        st=p+1;code_size=b[p]>>2
    elif b[p]&3==3:
        header=(u16(b,p)>>12)*4
        if header<12:raise ValueError('invalid fat CIL method header')
        st=p+header;code_size=u32(b,p+4)
    else:raise ValueError('invalid CIL header')
    if code_size>10*1024*1024 or st+code_size>len(b):
        raise ValueError('CIL code outside file')
    code=b[st:st+code_size]
    edges=[];i=0
    while i<len(code):
        off=i;op=code[i];i+=1
        if op==0xfe:
            if i>=len(code):raise ValueError('truncated extended instruction')
            op=0xfe00|code[i];i+=1
        if op==0x45:
            if i+4>len(code):raise ValueError('truncated switch')
            operand_size=4+u32(code,i)*4
        elif op in BYTE_OPS:operand_size=1
        elif op in WORD_OPS:operand_size=2
        elif op in DWORD_OPS:operand_size=4
        elif op in QWORD_OPS:operand_size=8
        else:operand_size=0
        if i+operand_size>len(code):raise ValueError('truncated CIL operand')
        if op in CALL_OPS:
            token=int.from_bytes(code[i:i+4],'little')
            edges.append({'offset':off,'opcode':'newobj' if op==0x73 else 'call',
                          'target':meta.target_name(token)})
        i+=operand_size
    return edges


def inspect_web_assembly(path:Path):
    pe=PE(path.read_bytes());meta=Metadata(pe)
    targets=('ITMC.Web.Regester.Page_Load',
             'ITMC.Web.Regester.Button1_Click',
             'ITMC.Web.funpublic.CheckReg')
    out={}
    for method in targets:
        matches=[rid for rid,name in meta.qualified_methods.items() if name==method]
        if len(matches)!=1:raise ValueError('Expected exactly one method: '+method)
        rid=matches[0]
        edges=call_edges(pe,meta,rid)
        # Keep the report narrowly scoped to the native registration call chain.
        out[method]={'token':'0x%08X'%(0x06000000|rid),
                     'registration_calls':sorted(set(e['target'] for e in edges if
                        any(word in e['target'] for word in
                            ('RegeditMain.', 'funpublic.CheckSoftVersionID'))))}
    lookup=lambda name:out[name]['registration_calls']
    upper='ITMC.Regedit.RegeditMain.'
    lower='itmcRegedit.RegeditMain.'
    assertions={
      'registration_page_uses_uppercase_NewRegistry':
          upper+'NewRegistry' in lookup(targets[0]),
      'registration_submit_uses_uppercase_DoRegistry':
          upper+'DoRegistry' in lookup(targets[1]),
      'web_check_references_uppercase_CheckReInfo':
          upper+'CheckReInfo' in lookup(targets[2]),
      'web_check_references_lowercase_CheckReInfo':
          lower+'CheckReInfo' in lookup(targets[2])
    }
    return {
      'assembly':path.name,
      'methods':out,
      'static_dual_registration_chain_evidenced':all(assertions.values()),
      'assertions':assertions,
      'runtime_licensing_tested':False,
      'note':'Static call references; control-flow branch execution and license validity not proven.'
    }


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('web_assembly',type=Path)
    p.add_argument('--json',type=Path)
    args=p.parse_args()
    result=inspect_web_assembly(args.web_assembly)
    if args.json:
        args.json.parent.mkdir(parents=True,exist_ok=True)
        args.json.write_text(json.dumps(result,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
    print(json.dumps(result,indent=2,ensure_ascii=False))


if __name__=='__main__':main()
