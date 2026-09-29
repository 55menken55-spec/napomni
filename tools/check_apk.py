#!/usr/bin/env python3
# Проверка собранного APK: версия, подпись, разрешения (без aapt — парсинг бинарного AXML).
import zipfile, struct, sys

def parse_strings(chunk, soff, offsets, utf8):
    strings = []
    for off in offsets:
        p = soff + off
        if utf8:
            l1 = chunk[p]; p2 = p + 2
            if l1 & 0x80: l1 = ((l1 & 0x7f) << 8) | chunk[p + 1]; p2 = p + 3
            l2 = chunk[p2]
            if l2 & 0x80: l2 = ((l2 & 0x7f) << 8) | chunk[p2 + 1]; start = p2 + 2
            else: start = p2 + 1
            strings.append(chunk[start:start + l2].decode("utf-8", "replace"))
        else:
            l = struct.unpack_from("<H", chunk, p)[0]; p2 = p + 2
            if l & 0x8000:
                l2 = struct.unpack_from("<H", chunk, p2)[0]
                l = ((l & 0x7fff) << 16) | l2; p2 += 2
            strings.append(chunk[p2:p2 + l * 2].decode("utf-16-le", "replace"))
    return strings

def elements(data):
    pos = 8
    pool = None
    while pos + 8 <= len(data):
        ctype, hsize, csize = struct.unpack_from("<HHI", data, pos)
        if csize <= 0: break
        chunk = data[pos:pos + csize]
        if ctype == 0x0001:
            scount, styleoff, flags, soff = struct.unpack_from("<IIII", chunk, 8)
            utf8 = bool(flags & 0x100)
            offsets = struct.unpack_from("<%dI" % scount, chunk, 28)  # после stringsStart
            pool = parse_strings(chunk, soff, offsets, utf8)
        elif ctype == 0x0102:  # start element
            ns, name = struct.unpack_from("<ii", chunk, 16)
            raw = struct.unpack_from("<i", chunk, 20)[0]
            attr_start, attr_size, attr_count = struct.unpack_from("<HHH", chunk, 24)
            base = 16 + attr_start
            attrs = []
            for i in range(attr_count):
                o = base + i * attr_size
                aname = struct.unpack_from("<i", chunk, o + 4)[0]
                araw = struct.unpack_from("<i", chunk, o + 8)[0]
                adata = struct.unpack_from("<I", chunk, o + 16)[0]
                attrs.append((aname, araw, adata))
            yield name, raw, attrs, pool
        pos += csize

def check(apk):
    z = zipfile.ZipFile(apk)
    names = z.namelist()
    ok_zip = z.testzip() is None
    v1 = "META-INF/MANIFEST.MF" in names
    blob = open(apk, "rb").read()
    v2 = b"APK Sig Block 42" in blob
    print(f"\n=== {apk}")
    print(f"  zip_ok={ok_zip}, файлов={len(names)}, размер={len(blob)/1024/1024:.1f} МБ")
    print(f"  подпись: v1(JAR)={'есть' if v1 else 'нет'}, v2(APK Signing Block)={'есть' if v2 else 'НЕТ!'}")

    data = z.read("AndroidManifest.xml")
    manifest, permissions = {}, []
    for name, raw, attrs, pool in elements(data):
        if pool is None: continue
        tag = pool[raw] if raw != -1 else None
        if tag == "manifest":
            for aname, araw, adata in attrs:
                # имена атрибутов манифеста: pool[aattr_idx]? name-индекс указывает в пул строк
                # у манифеста атрибуты не имеют raw-значений для versionCode; имя ищем по пулу
                pass
            # res-map недоступен напрямую — берём имена по индексу из пула ниже
        if tag == "uses-permission":
            for aname, araw, adata in attrs:
                if araw != -1: permissions.append(pool[araw])
        # имена атрибутов у манифеста указывают на строки пула (name = индекс строки)
        if tag == "manifest":
            for aname, araw, adata in attrs:
                an = pool[aname] if 0 <= aname < len(pool) else "?"
                if an == "versionCode": manifest["versionCode"] = adata
                if an == "versionName": manifest["versionName"] = pool[araw] if araw != -1 else adata
    print(f"  versionCode={manifest.get('versionCode')}  versionName={manifest.get('versionName')!r}")
    print("  разрешения:")
    for p in sorted(set(permissions)): print(f"    - {p}")
    bad = [p for p in permissions if "INTERNET" in p or "ACCESS_NETWORK" in p]
    print("  сетевые разрешения:", ("ЕСТЬ: " + ", ".join(bad)) if bad else "отсутствуют ✓")

for apk in sys.argv[1:]:
    check(apk)
