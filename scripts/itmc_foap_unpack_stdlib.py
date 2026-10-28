#!/usr/bin/env python3
"""Recover VBPD/FOAP protected .NET IL MethodDef bodies, for offline inspection.

Pure Python 3 standard library; does NOT modify registration decisions or licenses.
Usage: python itmc_foap_unpack_stdlib.py input.dll recovered.dll [--json report.json]
Inputs are never overwritten. Supports PE32/PE32+, un-signed .NET assemblies
with a FOAP central record table and no PE overlay.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import struct
from pathlib import Path

RC4_KEY = bytes.fromhex('5f21b11aeadb1723b5879f1c2d77fc9b')

def read_num(b, offset, width):
    if offset < 0 or offset + width > len(b):
        raise ValueError('truncated data at 0x%X' % offset)
    return int.from_bytes(b[offset:offset+width], 'little')

def u16(b, o): return read_num(b, o, 2)
def u32(b, o): return read_num(b, o, 4)
def u64(b, o): return read_num(b, o, 8)
def align(v,n):return (v+n-1)//n*n

def rc4(data, key=RC4_KEY):
    s=list(range(256)); j=0
    for i in range(256):
        j=(j+s[i]+key[i%len(key)])&255
        s[i],s[j]=s[j],s[i]
    i=j=0; out=bytearray(len(data))
    for pos,c in enumerate(data):
        i=(i+1)&255
        j=(j+s[i])&255
        s[i],s[j]=s[j],s[i]
        out[pos]=c^s[(s[i]+s[j])&255]
    return bytes(out)

class PE:
    def __init__(self, b):
        if b[:2] != b'MZ':raise ValueError('not a PE file')
        self.b=b
        pe=u32(b,0x3c)
        if b[pe:pe+4]!=b'PE\0\0':raise ValueError('invalid PE signature')
        self.opt=pe+24
        self.opt_size=u16(b,pe+20)
        self.magic=u16(b,self.opt)
        if self.magic not in (0x10b,0x20b):raise ValueError('unsupported PE magic')
        self.dd=self.opt+(96 if self.magic==0x10b else 112)
        self.file_alignment=u32(b,self.opt+36)
        self.section_alignment=u32(b,self.opt+32)
        if self.file_alignment==0 or self.section_alignment==0:raise ValueError('invalid PE alignment')
        if u32(b,self.dd+4*8) or u32(b,self.dd+4*8+4):raise ValueError('signed/certificate-bearing PE is not supported')
        self.sections=[]
        for i in range(u16(b,pe+6)):
            o=self.opt+self.opt_size+i*40
            name=b[o:o+8].rstrip(b'\0').decode('ascii','replace')
            vs=u32(b,o+8);va=u32(b,o+12);rs=u32(b,o+16);ro=u32(b,o+20)
            if ro+rs>len(b):raise ValueError('section out of file bounds')
            self.sections.append((name,vs,va,rs,ro,o))
        if not self.sections:raise ValueError('no PE sections')
        self.cli=self.rva_to_offset(u32(b,self.dd+14*8))
        cli_flags=u32(b,self.cli+16)
        if cli_flags&0x8 or u32(b,self.cli+36):raise ValueError('strong-name signed .NET assembly is not supported')
        self.meta=self.rva_to_offset(u32(b,self.cli+8))
    def rva_to_offset(self,r):
        for name,vs,va,rs,ro,o in self.sections:
            if va<=r<va+max(vs,rs):
                offset=ro+r-va
                if offset>=len(self.b):raise ValueError('RVA outside raw data')
                return offset
        raise ValueError('unmapped RVA 0x%X' % r)
    def offset_to_rva(self,off):
        for name,vs,va,rs,ro,o in self.sections:
            if ro<=off<ro+rs:return va+off-ro
        raise ValueError('unmapped offset 0x%X' % off)

# Only tables 0..6 need row layouts; no other metadata is rewritten.
def method_fields(pe):
    b=pe.b; meta=pe.meta
    if u32(b,meta)!=0x424a5342:raise ValueError('missing .NET metadata signature')
    p=align(meta+16+u32(b,meta+12),4)
    count=u16(b,p+2);p+=4
    streams={}
    for _ in range(count):
        rel=u32(b,p);sz=u32(b,p+4)
        end=b.find(b'\0',p+8)
        if end<0:raise ValueError('bad metadata stream')
        name=b[p+8:end].decode('ascii')
        p=align(end+1,4)
        streams[name]=(meta+rel,sz)
    tbl=streams.get('#~',streams.get('#-'))
    if not tbl or '#Strings' not in streams:raise ValueError('missing required .NET metadata heaps')
    t=tbl[0]; flags=b[t+6]; valid=u64(b,t+8);p=t+24
    counts={}
    for i in range(64):
        if valid>>i&1:counts[i]=u32(b,p);p+=4
    si=4 if flags&1 else 2;gi=4 if flags&2 else 2;bi=4 if flags&4 else 2
    ix=lambda tab:4 if counts.get(tab,0)>=0x10000 else 2
    def coded(tabs,bits):return 4 if max(counts.get(i,0) for i in tabs)>=(1<<(16-bits)) else 2
    sizes={
        0:2+si+3*gi,
        1:coded((0,26,35,1),2)+2*si,
        2:4+2*si+coded((1,2,27),2)+ix(4)+ix(6),
        3:ix(4),
        4:2+si+bi,
        5:ix(6),
        6:8+si+bi+ix(8),
    }
    for i in range(6):p+=counts.get(i,0)*sizes[i]
    n=counts.get(6,0)
    if not n:raise ValueError('empty MethodDef table')
    return [p+i*sizes[6] for i in range(n)]

def method_length(b):
    if not b:raise ValueError('empty MethodBody')
    kind=b[0]&3
    if kind==2:total=1+(b[0]>>2)
    elif kind==3:
        flags=u16(b,0);header=(flags>>12)*4
        if header<12 or header>len(b):raise ValueError('invalid fat header')
        code=u32(b,4)
        total=header+code
        if flags&8:
            total=align(total,4)
            while True:
                if total+4>len(b):raise ValueError('truncated exception section')
                sectkind=b[total]
                fat=bool(sectkind&0x40)
                size=(read_num(b,total+1,3) if fat else b[total+1])
                if size<4 or total+size>len(b):raise ValueError('bad CIL data section size')
                # EH clauses are 12 or 24 bytes, beginning after 4-byte section header.
                if sectkind&1 and (size-4)%(24 if fat else 12):
                    raise ValueError('invalid exception handler size')
                total+=size
                if not sectkind&0x80:break
                total=align(total,4)
    else:raise ValueError('unsupported CIL method header type')
    if total!=len(b):raise ValueError('MethodBody length mismatch: %d != %d'%(total,len(b)))
    return total

def records(pe,methods):
    b=pe.b;candidates=[]
    start=0
    while True:
        off=b.find(b'FOAP',start)
        if off<0:break
        start=off+1
        try:
            count=u32(b,off+4)
            if count<1 or count>len(methods) or off+8+24*count>len(b):continue
            base=pe.offset_to_rva(off)
            result=[];seen=set()
            for i in range(count):
                r=off+8+i*24
                tok,rel,src,length,aux,mode=struct.unpack_from('<6I',b,r)
                rid=tok&0xffffff
                if tok>>24!=6 or not 1<=rid<=len(methods) or tok in seen:raise ValueError('invalid/duplicate MethodDef token')
                seen.add(tok)
                if (base+8+i*24+8+rel)&0xffffffff!=src:raise ValueError('bad FOAP relative pointer')
                if length<=4:raise ValueError('record size too short')
                src_off=pe.rva_to_offset(src)
                if src_off+length>len(b):raise ValueError('encrypted body outside PE')
                result.append((tok,src_off,length,aux,mode))
            # Validate a sample in discovery; full verification happens before writing.
            for tok,src_off,length,_,_ in result[:min(16,len(result))]:
                decrypted=rc4(b[src_off:src_off+length])
                if not decrypted.endswith(b'VBPD'):raise ValueError('bad encrypted trailer')
                method_length(decrypted[:-4])
            candidates.append((off,result))
        except (ValueError,struct.error):continue
    if not candidates:raise ValueError('no valid FOAP record table found')
    return max(candidates,key=lambda x:len(x[1]))

def restore(src,dst, report):
    if src.resolve()==dst.resolve():raise ValueError('input and output must be different')
    raw=src.read_bytes();pe=PE(raw)
    method_offsets=method_fields(pe)
    off,records_list=records(pe,method_offsets)
    decoded=[]
    for tok,src_off,length,aux,mode in records_list:
        content=rc4(raw[src_off:src_off+length])
        if not content.endswith(b'VBPD'):raise ValueError('invalid decrypted trailer for 0x%08X'%tok)
        method_length(content[:-4])
        decoded.append((tok,content[:-4]))
    last=pe.sections[-1];name,vs,va,rs,ro,header_off=last
    if ro+rs!=len(raw):raise ValueError('PE overlay/trailing bytes present; refusing to rewrite')
    out=bytearray(raw)
    appends=0
    for tok,body in decoded:
        new_offset=align(len(out),4)
        out.extend(b'\0'*(new_offset-len(out)))
        new_rva=va+new_offset-ro
        if new_rva>0xffffffff:raise ValueError('RVA overflow')
        out.extend(body)
        field=method_offsets[(tok&0xffffff)-1]
        struct.pack_into('<I',out,field,new_rva)
        appends+=len(body)
    unaligned_len=len(out)
    out.extend(b'\0'*(align(len(out),pe.file_alignment)-len(out)))
    new_raw=len(out)-ro
    new_virtual=unaligned_len-ro
    if max(new_raw,new_virtual)>0xffffffff:raise ValueError('section size overflow')
    struct.pack_into('<I',out,header_off+8,new_virtual)
    struct.pack_into('<I',out,header_off+16,new_raw)
    struct.pack_into('<I',out,pe.opt+56,align(va+new_virtual,pe.section_alignment))
    struct.pack_into('<I',out,pe.opt+8,u32(raw,pe.opt+8)+(new_raw-rs))
    # Ensure the output PE still contains exactly the intended CIL methods at new RVAs.
    updated=PE(bytes(out));updated_methods=method_fields(updated)
    for tok,body in decoded:
        new_rva=u32(out,updated_methods[(tok&0xffffff)-1])
        p=updated.rva_to_offset(new_rva)
        if out[p:p+len(body)]!=body:raise ValueError('post-write verification failed for 0x%08X'%tok)
    dst.parent.mkdir(parents=True,exist_ok=True)
    dst.write_bytes(out)
    info={
      'input':src.name,'input_sha256':hashlib.sha256(raw).hexdigest(),
      'output':dst.name,'output_sha256':hashlib.sha256(out).hexdigest(),
      'foap_offset':hex(off),'methoddef_count':len(method_offsets),
      'restored_method_count':len(decoded),'restored_body_bytes':appends,
      'output_size_bytes':len(out),'new_size_of_image':u32(out,pe.opt+56),
      'validation':'all encrypted records decoded; CIL body/EH lengths verified; output MethodDef.RVA re-checked',
      'runtime_authorization_tested':False
    }
    if report:
        report.parent.mkdir(parents=True,exist_ok=True)
        report.write_text(json.dumps(info,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
    return info

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('input',type=Path);parser.add_argument('output',type=Path)
    parser.add_argument('--json',type=Path)
    args=parser.parse_args()
    info=restore(args.input,args.output,args.json)
    print(json.dumps(info,indent=2,ensure_ascii=False))

if __name__=='__main__':main()
