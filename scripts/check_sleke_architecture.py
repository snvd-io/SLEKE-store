#!/usr/bin/env python3
"""Check structural SLEKE module boundaries (not arbitrary runtime behavior)."""
import argparse
import pathlib
import re
import sys


# Keep line numbers while excluding comments and Kotlin literals from type scans.
def strip_non_code(text, strings=True):
    result = list(text)
    index = 0
    while index < len(text):
        start = index
        is_comment = False
        if text.startswith("//", index):
            end = text.find("\n", index)
            index = len(text) if end == -1 else end
            is_comment = True
        elif text.startswith("/*", index):
            depth = 1
            index += 2
            while index < len(text) and depth:
                if text.startswith("/*", index):
                    depth += 1
                    index += 2
                elif text.startswith("*/", index):
                    depth -= 1
                    index += 2
                else:
                    index += 1
            is_comment = True
        elif text.startswith('"""', index):
            end = text.find('"""', index + 3)
            index = len(text) if end == -1 else end + 3
        elif text[index] in ("\"", "'"):
            quote = text[index]
            index += 1
            while index < len(text):
                if text[index] == "\\":
                    index += 2
                elif text[index] == quote:
                    index += 1
                    break
                else:
                    index += 1
        else:
            index += 1
            continue
        if strings or is_comment:
            for position in range(start, min(index, len(text))):
                if text[position] != "\n":
                    result[position] = " "
    return "".join(result)


HOST = re.compile(r'\bcom\s*\.\s*(?:aurora\b|sleke\s*\.\s*integration\b)')
PLATFORM = re.compile(
    r'\b(?:android|androidx|io\s*\.\s*ktor|okhttp3|retrofit2|'
    r'com\s*\.\s*google\s*\.\s*firebase|'
    r'org\s*\.\s*apache\s*\.\s*http|'
    r'java\s*\.\s*(?:io|net|nio)|javax\s*\.\s*(?:net|sql))\s*\.'
)
DOMAIN = re.compile(r'^\s*package\s+[\w.]*\bdomain(?:\.|\s|$)', re.MULTILINE)
DEPENDENCY = re.compile(
    r'project\s*\(\s*(?:path\s*=\s*)?["\']:app["\']|'
    r'projects\s*\.\s*app\b|'
    r'["\']com\.aurora[:.]'
)


def violations(root):
    library = root / 'sleke'
    for source in sorted((library / 'src').rglob('*.kt')):
        code = strip_non_code(source.read_text())
        checks = [(HOST, 'library must not reference Aurora host types')]
        if DOMAIN.search(code):
            checks.append((PLATFORM, 'domain must not reference platform or network types'))
        for pattern, message in checks:
            for match in pattern.finditer(code):
                line = code.count('\n', 0, match.start()) + 1
                yield f'{source.relative_to(root)}:{line}: {message}'
    for source in sorted(library.glob('build.gradle*')):
        # Gradle dependency coordinates are string literals, so retain those here.
        code = strip_non_code(source.read_text(), strings=False)
        for match in DEPENDENCY.finditer(code):
            line = code.count('\n', 0, match.start()) + 1
            yield f'{source.relative_to(root)}:{line}: library must not depend on app/Aurora'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=pathlib.Path, default=pathlib.Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    errors = list(violations(args.root.resolve()))
    for error in errors:
        print(error)
    if not errors:
        print('SLEKE architecture boundaries passed')
    return bool(errors)


if __name__ == '__main__':
    sys.exit(main())
