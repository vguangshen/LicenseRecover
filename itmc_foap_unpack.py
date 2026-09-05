#!/usr/bin/env python3
"""Static MethodBody restorer for the authorized ITMC FOAP/VBPD samples.

Dependencies:
    pip install pefile dnfile dncil

The tool finds the central FOAP record table, RC4-decrypts each full
ECMA-335 MethodBody, strips the trailing VBPD integrity marker, appends
all bodies to the last PE section, and updates MethodDef.RVA values.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import struct
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

import dnfile
import pefile
from dncil.cil.body import CilMethodBody
from dncil.cil.body.reader import CilMethodBodyReaderBase

FOAP = b"FOAP"
TRAILER = b"VBPD"
RC4_KEY = bytes.fromhex("5f21b11aeadb1723b5879f1c2d77fc9b")
RECORD_SIZE = 24


class BytesReader(CilMethodBodyReaderBase):
    def __init__(self, data: bytes):
        self.data = data
        self.pos = 0

    def read(self, n: int) -> bytes:
        chunk = self.data[self.pos : self.pos + n]
        self.pos += len(chunk)
        return chunk

    def tell(self) -> int:
        return self.pos

    def seek(self, rva: int) -> int:
        self.pos = rva
        return self.pos


@dataclass(frozen=True)
class Record:
    token: int
    rel32: int
    body_rva: int
    encrypted_size: int
    auxiliary: int
    mode: int
    record_rva: int


def align_up(value: int, alignment: int) -> int:
    return (value + alignment - 1) // alignment * alignment


def signed32(value: int) -> int:
    return value if value < 0x80000000 else value - 0x100000000


def rc4(data: bytes, key: bytes = RC4_KEY) -> bytes:
    state = list(range(256))
    j = 0
    for i in range(256):
        j = (j + state[i] + key[i % len(key)]) & 0xFF
        state[i], state[j] = state[j], state[i]

    out = bytearray(len(data))
    i = j = 0
    for index, value in enumerate(data):
        i = (i + 1) & 0xFF
        j = (j + state[i]) & 0xFF
        state[i], state[j] = state[j], state[i]
        out[index] = value ^ state[(state[i] + state[j]) & 0xFF]
    return bytes(out)


class Image:
    def __init__(self, path: Path):
        self.path = path
        self.data = path.read_bytes()
        self.pe = pefile.PE(data=self.data)

    def rva_to_offset(self, rva: int) -> int:
        return self.pe.get_offset_from_rva(rva)

    def read_rva(self, rva: int, size: int) -> bytes:
        offset = self.rva_to_offset(rva)
        result = self.data[offset : offset + size]
        if len(result) != size:
            raise ValueError(f"RVA 0x{rva:X}: wanted {size} bytes, got {len(result)}")
        return result

    def offset_to_rva(self, offset: int) -> int:
        return self.pe.get_rva_from_offset(offset)


def parse_records(image: Image, table_rva: int) -> list[Record]:
    header = image.read_rva(table_rva, 8)
    if header[:4] != FOAP:
        raise ValueError("not a FOAP table")
    count = struct.unpack_from("<I", header, 4)[0]
    if not (1 <= count <= 2_000_000):
        raise ValueError(f"unreasonable FOAP record count: {count}")

    records: list[Record] = []
    for index in range(count):
        record_rva = table_rva + 8 + index * RECORD_SIZE
        fields = struct.unpack("<6I", image.read_rva(record_rva, RECORD_SIZE))
        record = Record(*fields, record_rva=record_rva)
        if (record.token & 0xFF000000) != 0x06000000:
            raise ValueError(f"record {index}: invalid MethodDef token 0x{record.token:08X}")
        expected = (record.record_rva + 8 + signed32(record.rel32)) & 0xFFFFFFFF
        if expected != record.body_rva:
            raise ValueError(
                f"record {index}: rel32 target 0x{expected:X} != body RVA 0x{record.body_rva:X}"
            )
        if record.encrypted_size <= len(TRAILER):
            raise ValueError(f"record {index}: invalid encrypted size")
        records.append(record)
    return records


def discover_table(image: Image) -> tuple[int, list[Record]]:
    start = 0
    candidates: list[tuple[int, list[Record]]] = []
    while True:
        offset = image.data.find(FOAP, start)
        if offset < 0:
            break
        start = offset + 1
        try:
            rva = image.offset_to_rva(offset)
            records = parse_records(image, rva)
            # Validate several encrypted records before accepting the table.
            for record in records[: min(16, len(records))]:
                decrypted = rc4(image.read_rva(record.body_rva, record.encrypted_size))
                if not decrypted.endswith(TRAILER):
                    raise ValueError("missing VBPD trailer")
                body_bytes = decrypted[: -len(TRAILER)]
                body = CilMethodBody(BytesReader(body_bytes))
                if body.size != len(body_bytes):
                    raise ValueError("MethodBody size mismatch")
            candidates.append((rva, records))
        except Exception:
            continue

    if not candidates:
        raise RuntimeError("no valid FOAP table found")
    # The central table is the valid candidate with the most records.
    candidates.sort(key=lambda item: len(item[1]), reverse=True)
    return candidates[0]


def decrypt_bodies(image: Image, records: Iterable[Record]):
    for record in records:
        encrypted = image.read_rva(record.body_rva, record.encrypted_size)
        decrypted = rc4(encrypted)
        if not decrypted.endswith(TRAILER):
            raise RuntimeError(f"0x{record.token:08X}: missing VBPD trailer")
        body_bytes = decrypted[: -len(TRAILER)]
        body = CilMethodBody(BytesReader(body_bytes))
        if body.size != len(body_bytes):
            raise RuntimeError(
                f"0x{record.token:08X}: parsed size {body.size} != {len(body_bytes)}"
            )
        yield record, body_bytes, body


def patch_image(source: Path, output: Path, records: list[Record], report_csv: Path) -> dict:
    image = Image(source)
    mutable = bytearray(image.data)
    pe = image.pe

    if pe.OPTIONAL_HEADER.DATA_DIRECTORY[4].VirtualAddress:
        raise RuntimeError("signed/certificate-bearing PE is not supported by this prototype")

    last = pe.sections[-1]
    last_raw_end = last.PointerToRawData + last.SizeOfRawData
    if last_raw_end != len(mutable):
        raise RuntimeError("PE overlay detected; refusing to rewrite automatically")

    file_alignment = pe.OPTIONAL_HEADER.FileAlignment
    section_alignment = pe.OPTIONAL_HEADER.SectionAlignment
    raw_start = last.PointerToRawData
    old_raw_size = last.SizeOfRawData

    current_offset = align_up(len(mutable), 4)
    mutable.extend(b"\x00" * (current_offset - len(mutable)))
    current_rva = last.VirtualAddress + (current_offset - raw_start)
    current_rva = align_up(current_rva, 4)
    wanted_offset = raw_start + (current_rva - last.VirtualAddress)
    mutable.extend(b"\x00" * (wanted_offset - len(mutable)))
    current_offset = wanted_offset

    metadata = dnfile.dnPE(str(source))
    method_rows = metadata.net.mdtables.MethodDef.rows

    rows = []
    eh_methods = eh_clauses = total_body_bytes = 0
    for record, body_bytes, body in decrypt_bodies(image, records):
        padding = (-current_offset) & 3
        if padding:
            mutable.extend(b"\x00" * padding)
            current_offset += padding
            current_rva += padding

        new_rva = current_rva
        mutable.extend(body_bytes)
        current_offset += len(body_bytes)
        current_rva += len(body_bytes)

        rid = record.token & 0x00FFFFFF
        if not (1 <= rid <= len(method_rows)):
            raise RuntimeError(f"bad MethodDef RID in token 0x{record.token:08X}")
        method_row = method_rows[rid - 1]
        rva_field_offset = (
            method_row.struct.__file_offset__
            + method_row.struct.__field_offsets__["Rva"]
        )
        struct.pack_into("<I", mutable, rva_field_offset, new_rva)

        clause_count = len(body.exception_handlers)
        if clause_count:
            eh_methods += 1
            eh_clauses += clause_count
        total_body_bytes += len(body_bytes)
        rows.append(
            {
                "token": f"0x{record.token:08X}",
                "old_encrypted_rva": f"0x{record.body_rva:X}",
                "new_method_rva": f"0x{new_rva:X}",
                "method_body_size": len(body_bytes),
                "header_size": body.header_size,
                "code_size": body.code_size,
                "eh_clauses": clause_count,
                "auxiliary": f"0x{record.auxiliary:X}",
                "mode": record.mode,
            }
        )

    final_size = align_up(len(mutable), file_alignment)
    mutable.extend(b"\x00" * (final_size - len(mutable)))

    new_raw_size = final_size - raw_start
    new_virtual_size = current_rva - last.VirtualAddress
    section_header_offset = last.get_file_offset()
    struct.pack_into("<I", mutable, section_header_offset + 8, new_virtual_size)
    struct.pack_into("<I", mutable, section_header_offset + 16, new_raw_size)

    optional_offset = pe.OPTIONAL_HEADER.get_file_offset()
    size_of_image = align_up(last.VirtualAddress + new_virtual_size, section_alignment)
    struct.pack_into("<I", mutable, optional_offset + 56, size_of_image)

    # SizeOfInitializedData is informational but keep it coherent.
    old_initialized = pe.OPTIONAL_HEADER.SizeOfInitializedData
    raw_delta = new_raw_size - old_raw_size
    struct.pack_into("<I", mutable, optional_offset + 8, old_initialized + raw_delta)

    output.write_bytes(mutable)
    with report_csv.open("w", newline="", encoding="utf-8-sig") as handle:
        writer = csv.DictWriter(handle, fieldnames=rows[0].keys())
        writer.writeheader()
        writer.writerows(rows)

    return {
        "records": len(rows),
        "total_method_body_bytes": total_body_bytes,
        "eh_methods": eh_methods,
        "eh_clauses": eh_clauses,
        "output_size": len(mutable),
        "output_sha256": hashlib.sha256(mutable).hexdigest(),
        "appended_rva_start": f"0x{rows[0]['new_method_rva'][2:]}",
        "size_of_image": f"0x{size_of_image:X}",
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--csv", type=Path, default=None)
    args = parser.parse_args()

    if args.input.resolve() == args.output.resolve():
        parser.error("input and output must differ")
    report_csv = args.csv or args.output.with_suffix(".records.csv")

    image = Image(args.input)
    table_rva, records = discover_table(image)
    print(f"FOAP table: RVA 0x{table_rva:X}, records={len(records)}")
    summary = patch_image(args.input, args.output, records, report_csv)
    for key_name, value in summary.items():
        print(f"{key_name}: {value}")
    print(f"record report: {report_csv}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
