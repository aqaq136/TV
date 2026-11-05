#!/usr/bin/env python3
"""Every launcher-owned translatable resource has Russian text and a Russian fallback."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1] / 'app/src/main/res'
def resources(folder):
    found = {}
    for file in folder.glob('*.xml'):
        for node in ET.parse(file).getroot():
            if node.tag in {'string', 'string-array', 'plurals'} and node.get('translatable') != 'false':
                key = (node.tag, node.get('name'))
                if key in found:
                    raise ValueError(f'Повторный ресурс: {node.get("name")} в {file}')
                found[key] = ''.join(node.itertext()).strip()
    return found

base, russian = resources(root / 'values'), resources(root / 'values-ru')
missing = sorted(set(base) - set(russian))
different = sorted(key for key in base.keys() & russian.keys() if base[key] != russian[key])
if missing or different:
    print('Нет русского перевода:', ', '.join('/'.join(key) for key in missing))
    print('Не совпадает русский текст по умолчанию:', ', '.join('/'.join(key) for key in different))
    sys.exit(1)
print(f'Русские строки и резервный текст проверены: {len(base)} ресурсов.')
