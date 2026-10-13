"""Local data protection: encrypted business backup and in-memory isolated clone.

Account credentials and face features are excluded from backup files. A clone receives
the complete database only through an in-memory pipe. No plaintext dump is saved.
Restore is deliberately limited to an independently named test container.
"""
import argparse
import ctypes
from ctypes import wintypes
import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import zlib

spec = importlib.util.spec_from_file_location('completeness', Path(__file__).with_name('data-completeness.py'))
dc = importlib.util.module_from_spec(spec)
spec.loader.exec_module(dc)


class Blob(ctypes.Structure):
    _fields_ = [('size', wintypes.DWORD), ('data', ctypes.POINTER(ctypes.c_byte))]


def protect(data, decrypt=False):
    buffer = ctypes.create_string_buffer(data)
    source = Blob(len(data), ctypes.cast(buffer, ctypes.POINTER(ctypes.c_byte)))
    destination = Blob()
    crypt = ctypes.WinDLL('crypt32', use_last_error=True)
    method = crypt.CryptUnprotectData if decrypt else crypt.CryptProtectData
    if not method(ctypes.byref(source), None, None, None, None, 1, ctypes.byref(destination)):
        raise ctypes.WinError(ctypes.get_last_error())
    try:
        return ctypes.string_at(destination.data, destination.size)
    finally:
        ctypes.WinDLL('kernel32').LocalFree(destination.data)


def dump(container, include_private=False, schema_only=False):
    options = '--single-transaction --skip-lock-tables --hex-blob --no-tablespaces --set-gtid-purged=OFF'
    if not include_private:
        options += ' --ignore-table=classroom_ai.t_user_account --ignore-table=classroom_ai.face_feature'
    if schema_only:
        options += ' --no-data'
    result = subprocess.run(['docker', 'exec', container, 'sh', '-c', 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot ' + options + ' classroom_ai'], capture_output=True)
    if result.returncode:
        raise RuntimeError(result.stderr.decode())
    return result.stdout


def restore_bytes(data, container):
    if not container.startswith('classroom-data-test-'):
        raise ValueError('Restore allowed only for classroom-data-test-* isolated containers')
    result = subprocess.run(['docker', 'exec', '-i', container, 'mysql', '-uroot', 'classroom_ai'], input=data, capture_output=True)
    if result.returncode:
        raise RuntimeError(result.stderr.decode())


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('command', choices=['backup', 'clone', 'restore-test'])
    p.add_argument('--source', default='classroom-mysql')
    p.add_argument('--target')
    p.add_argument('--file')
    p.add_argument('--evidence', required=True)
    a = p.parse_args()
    if not __import__('re').fullmatch(r'[A-Za-z0-9_-]+', a.source):
        raise ValueError('Invalid source container')
    if a.command == 'backup':
        file = Path(a.file)
        if file.exists():
            raise FileExistsError('Never overwrite a previous backup')
        data = dump(a.source)
        encrypted = protect(zlib.compress(data))
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_bytes(encrypted)
        # Prove same-account decryptability and exact round-trip before any write.
        assert zlib.decompress(protect(encrypted, True)) == data
        dc.save(a.evidence, {'encrypted_file': str(file.resolve()), 'encrypted_sha256': hashlib.sha256(encrypted).hexdigest(), 'business_dump_sha256': hashlib.sha256(data).hexdigest(), 'excluded': ['t_user_account', 'face_feature'], 'decrypt_roundtrip': True, 'encryption': 'Windows DPAPI current user, compressed', 'source': a.source})
    elif a.command == 'clone':
        data = dump(a.source, include_private=True)
        restore_bytes(data, a.target)
        dc.save(a.evidence, {'source': a.source, 'target': a.target, 'in_memory_restore': True, 'plaintext_saved': False})
    else:
        encrypted = Path(a.file).read_bytes()
        data = zlib.decompress(protect(encrypted, True))
        restore_bytes(data, a.target)
        dc.save(a.evidence, {'target': a.target, 'business_restore_success': True, 'private_tables_excluded': True})
    print(json.dumps({'command': a.command, 'evidence': a.evidence, 'success': True}))


if __name__ == '__main__':
    main()
