#!/usr/bin/env python3
"""Static DS01xx .NET registration mode selector inspector (read-only).

Analyzes recovered .NET CIL of each registration DLL. Reports which
CheckNet / CheckLocalReg / CheckDog functions are *referenced in order*
by the regType 1, 2, and 3 branches of RegeditMain.CheckReInfo.
Does not execute application code, interpret a license, or write to a site.
"""
from __future__ import annotations
import argparse
import json
from pathlib import Path
from inspect_ds01xx_registration_chain import (
    Metadata, PE, BYTE_OPS, WORD_OPS, DWORD_OPS, QWORD_OPS,
)
from itmc_foap_unpack_stdlib import u16, u32


def instructions(pe, meta, rid):
    """Decode CIL opcodes and their encoded operands, preserving offsets."""
    b=pe.b
    rva=meta.methods[rid]
    if not rva:
        return []
    pos=pe.rva_to_offset(rva)
    if b[pos]&3==2:
        start=pos+1
        size=b[pos]>>2
    elif b[pos]&3==3:
        header=(u16(b,pos)>>12)*4
        if header<12:
            raise ValueError('invalid fat MethodBody header')
        start=pos+header
        size=u32(b,pos+4)
    else:
        raise ValueError('unrecognized MethodBody')
    if size>10*1024*1024 or start+size>len(b):
        raise ValueError('MethodBody code out of range')
    code=b[start:start+size]
    result=[]
    i=0
    while i<len(code):
        offset=i
        op=code[i]
        i+=1
        if op==0xfe:
            if i>=len(code):raise ValueError('truncated extended CIL opcode')
            op=0xfe00|code[i]
            i+=1
        if op==0x45:
            if i+4>len(code):raise ValueError('truncated switch')
            width=4+u32(code,i)*4
        elif op in BYTE_OPS:width=1
        elif op in WORD_OPS:width=2
        elif op in DWORD_OPS:width=4
        elif op in QWORD_OPS:width=8
        else:width=0
        if i+width>len(code):
            raise ValueError('truncated CIL operand')
        arg=int.from_bytes(code[i:i+width], 'little') if width else None
        i+=width
        result.append((offset,i,op,arg,width))
    return result


def user_string(meta, token):
    """Decode only #US string constants needed to locate literal regType."""
    if token>>24!=0x70 or '#US' not in meta.streams:
        return None
    origin,size=meta.streams['#US']
    p=token&0xffffff
    if p>=size:
        return None
    b=meta.pe.b
    start=origin+p
    prefix=b[start]
    if prefix<0x80:
        length=prefix;header=1
    elif prefix<0xc0:
        length=((prefix&0x3f)<<8)|b[start+1];header=2
    elif prefix<0xe0:
        length=((prefix&0x1f)<<24)|(b[start+1]<<16)|(b[start+2]<<8)|b[start+3];header=4
    else:
        return None
    if p+header+length>size or length<1 or (length-1)%2:
        return None
    try:
        return b[start+header:start+header+length-1].decode('utf-16-le')
    except UnicodeDecodeError:
        return None


def mode_branch_target(inst, meta, literal):
    """Find a string equality branch for regType n; never guess from mere DLL names."""
    for k,(off,end,op,arg,width) in enumerate(inst):
        if op!=0x72 or user_string(meta,arg)!=literal:
            continue
        # The source pattern is ldstr n -> string comparison -> brtrue.
        for candidate in inst[k+1:k+5]:
            o,nxt,opcode,operand,operand_width=candidate
            if opcode in (0x2d,0x3a):
                if opcode==0x2d:
                    delta=operand-256 if operand>=128 else operand
                else:
                    delta=operand-0x100000000 if operand>=0x80000000 else operand
                return nxt+delta
    return None


def mode_order_from_instructions(inst, meta, mode):
    start=mode_branch_target(inst,meta,mode)
    if start is None:
        return []
    orders=[]
    for offset,end,op,operand,width in inst:
        if offset<start:continue
        if op in (0x28,0x6f):
            name=meta.target_name(operand)
            if name.endswith('.CheckNet'):type_name='CheckNet'
            elif name.endswith('.CheckLocalReg'):type_name='CheckLocalReg'
            elif name.endswith('.CheckHASPDog'):type_name='CheckHASPDog'
            elif name.endswith('.CheckDog'):type_name='CheckDog'
            else:continue
            if type_name not in orders:
                orders.append(type_name)
        if len(orders)==3:
            break
        if offset>start+200:
            break
    return orders


def inspect_registration_assembly(path):
    pe=PE(path.read_bytes())
    meta=Metadata(pe)
    names=[(rid,name) for rid,name in meta.qualified_methods.items()
           if name.endswith('.RegeditMain.CheckReInfo')]
    if len(names)!=1:
        raise ValueError('Expected one RegeditMain.CheckReInfo in '+path.name)
    rid,name=names[0]
    inst=instructions(pe,meta,rid)
    priorities={mode:mode_order_from_instructions(inst,meta,mode)
                for mode in ('1','2','3')}
    proven=all(len(v)==3 for v in priorities.values())
    return {'assembly':path.name,
            'method':name,
            'token':'0x%08X'%(0x06000000|rid),
            'reg_type_check_priority':priorities,
            'static_priorities_proven':proven,
            'runtime_license_validity_tested':False,
            'note':'Static branch and call order only. CheckReInfo return semantics and selected runtime path require native tests.'}


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('assemblies',nargs='+',type=Path)
    p.add_argument('--json',type=Path)
    args=p.parse_args()
    result={'assemblies':[inspect_registration_assembly(p) for p in args.assemblies]}
    if args.json:
        args.json.parent.mkdir(parents=True,exist_ok=True)
        args.json.write_text(json.dumps(result,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
    print(json.dumps(result,indent=2,ensure_ascii=False))


if __name__=='__main__':
    main()
