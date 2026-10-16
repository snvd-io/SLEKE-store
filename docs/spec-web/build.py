#!/usr/bin/env python3
"""Build the dependency-free specification reader from repository Markdown."""
from pathlib import Path
import argparse
import hashlib
import html
import json
import re
import shutil
import zipfile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
DIST = HERE / "dist"
DESIGN = ROOT / "docs/superpowers/specs/2026-10-16-sleke-module-refactor-design.md"
CHANGE = ROOT / "openspec/changes/isolate-sleke-features"
PLAN_ROOT = ROOT / "docs/superpowers/plans"
PLAN_PAGES = [
    (PLAN_ROOT / "2026-10-16-sleke-module-refactor-plan.md", "implementation.html", "Full implementation plan"),
    (PLAN_ROOT / "2026-10-16-sleke-module-refactor/01-catalog.md", "plan-catalog.html", "Catalog and presentation"),
    (PLAN_ROOT / "2026-10-16-sleke-module-refactor/02-sessions.md", "plan-sessions.html", "Enterprise sessions"),
    (PLAN_ROOT / "2026-10-16-sleke-module-refactor/03-downloads.md", "plan-downloads.html", "Custom downloads"),
    (PLAN_ROOT / "2026-10-16-sleke-module-refactor/04-support-integration.md", "plan-support.html", "Support and integration"),
]
DOCUMENT_LINKS = {DESIGN.resolve(): "index.html", **{path.resolve(): name for path, name, _ in PLAN_PAGES}}
CURRENT_DOCUMENT = DESIGN


def inline(value):
    value = html.escape(value)
    protected = []

    def code(match):
        protected.append("<code>" + match[1] + "</code>")
        return "CODETOKEN" + str(len(protected) - 1) + "ENDTOKEN"

    value = re.sub(r"`([^`]+)`", code, value)
    def link(match):
        label, href = match[1], html.unescape(match[2])
        if href.startswith("https://"):
            return f'<a href="{html.escape(href, quote=True)}" target="_blank" rel="noreferrer">{label}</a>'
        target = DOCUMENT_LINKS.get((CURRENT_DOCUMENT.parent / href).resolve())
        return f'<a href="{target}">{label}</a>' if target else label

    value = re.sub(r"\[([^\]]+)\]\(([^\s)]+)\)", link, value)
    value = re.sub(r"\*\*([^*]+)\*\*", r"<strong>\1</strong>", value)
    for i, fragment in enumerate(protected):
        value = value.replace("CODETOKEN" + str(i) + "ENDTOKEN", fragment)
    return value


def markdown(value):
    """Render this reader's controlled prose, lists, headings and tables."""
    lines = value.strip().splitlines()
    out = []
    i = 0
    while i < len(lines):
        line = lines[i].strip()
        if not line:
            i += 1
            continue
        if line.startswith("|"):
            rows = []
            while i < len(lines) and lines[i].strip().startswith("|"):
                cells = [x.strip() for x in lines[i].strip().strip("|").split("|")]
                if not all(re.fullmatch(r":?-+:?", c) for c in cells):
                    rows.append(cells)
                i += 1
            header, *body = rows
            out.append('<div class="table-scroll" role="region" aria-label="Comparison table" tabindex="0"><table><thead><tr>')
            out.extend("<th scope=\"col\">" + inline(c) + "</th>" for c in header)
            out.append("</tr></thead><tbody>")
            for row in body:
                out.append("<tr>" + "".join("<td>" + inline(c) + "</td>" for c in row) + "</tr>")
            out.append("</tbody></table></div>")
            continue
        heading = re.match(r"^(#{3,6}) (.+)", line)
        if heading:
            level = len(heading[1])
            out.append(f"<h{level}>" + inline(heading[2]) + f"</h{level}>")
            i += 1
            continue
        if re.match(r"^(?:- |\d+\. )", line):
            ordered = bool(re.match(r"^\d+\. ", line))
            tag = "ol" if ordered else "ul"
            steps = line.startswith("- [ ] ")
            out.append("<" + tag + (' class="plan-steps"' if steps else "") + ">")
            while i < len(lines) and re.match(r"^(?:- |\d+\. )", lines[i].strip()):
                item = re.sub(r"^(?:- |\d+\. )", "", lines[i].strip())
                out.append("<li>" + inline(item.removeprefix("[ ] ")) + "</li>")
                i += 1
            out.append("</" + tag + ">")
            continue
        paragraph = [line]
        i += 1
        while i < len(lines) and lines[i].strip() and not re.match(r"^(?:#{3,6} |\||- |\d+\. )", lines[i].strip()):
            paragraph.append(lines[i].strip())
            i += 1
        out.append("<p>" + inline(" ".join(paragraph)) + "</p>")
    return "\n".join(out)


ARCHITECTURE = '''<figure class="diagram"><div class="diagram-scroll" tabindex="0" aria-label="Architecture diagram"><svg viewBox="0 0 840 340" role="img" aria-labelledby="arch-title arch-description" xmlns="http://www.w3.org/2000/svg">
<title id="arch-title">Application depends on the SLEKE feature library</title><desc id="arch-description">The app retains navigation, Play adapters, installer adapters and packaging. The SLEKE library owns catalog, enterprise sessions, downloads, eligibility, recovery and runtime support. Domain contracts prevent reverse dependencies.</desc>
<defs><marker id="arrow" markerWidth="8" markerHeight="8" refX="6" refY="3" orient="auto"><path d="M0 0L6 3L0 6" fill="none" stroke="#17675d" stroke-width="1.5"/></marker></defs>
<rect x="12" y="16" width="264" height="288" rx="12" fill="#fff" stroke="#cbd0cb"/>
<text x="36" y="51" class="svg-eyebrow">APPLICATION</text><text x="36" y="84" class="svg-title">:app</text>
<text x="36" y="123" class="svg-body">Navigation and deep links</text><text x="36" y="156" class="svg-body">Play metadata and auth adapters</text><text x="36" y="189" class="svg-body">Preferred-installer adapter</text><text x="36" y="222" class="svg-body">Upstream workers and screens</text><text x="36" y="255" class="svg-body">Packaging, version and signing</text>
<path d="M278 164H365" stroke="#17675d" stroke-width="2" marker-end="url(#arrow)"/><text x="291" y="145" class="svg-small">depends on</text>
<rect x="370" y="16" width="458" height="288" rx="12" fill="#eaf1ec" stroke="#bacdc0"/>
<text x="394" y="51" class="svg-eyebrow">PRODUCT FEATURES</text><text x="394" y="84" class="svg-title">:sleke</text>
<g class="svg-body"><text x="394" y="126">Catalog</text><text x="596" y="126">Enterprise session</text><text x="394" y="167">Downloads</text><text x="596" y="167">Eligibility</text><text x="394" y="208">Recovery</text><text x="596" y="208">Runtime and branding</text></g>
<path d="M394 232H804" stroke="#bacdc0"/><text x="394" y="258" class="svg-small">Pure domain contracts · feature data · reusable UI</text><text x="394" y="281" class="svg-small">No imports from Aurora implementation</text>
</svg></div><figcaption>Compile-time direction stays one way. The app implements library contracts and supplies host navigation callbacks.</figcaption></figure>'''

DOWNLOADS = '''<figure class="diagram"><div class="diagram-scroll" tabindex="0" aria-label="Download operation diagram"><svg viewBox="0 0 840 250" role="img" aria-labelledby="flow-title flow-description" xmlns="http://www.w3.org/2000/svg">
<title id="flow-title">One validated download operation and one controlled installer handoff</title><desc id="flow-description">The original request resolves a retained operation handle, transfers and validates the APK, then stages it for the preferred installer. HTTP, package and cancellation errors cannot reach installation. Screen observation never submits installation.</desc>
<defs><marker id="flow-arrow" markerWidth="8" markerHeight="8" refX="6" refY="3" orient="auto"><path d="M0 0L6 3L0 6" fill="none" stroke="#65766a" stroke-width="1.5"/></marker></defs>
<g fill="#fff" stroke="#cbd0cb"><rect x="10" y="30" width="142" height="90" rx="9"/><rect x="180" y="30" width="142" height="90" rx="9"/><rect x="350" y="30" width="142" height="90" rx="9"/><rect x="520" y="30" width="142" height="90" rx="9"/><rect x="690" y="30" width="140" height="90" rx="9"/></g>
<g class="svg-small"><text x="26" y="55">01 / REQUEST</text><text x="196" y="55">02 / TRANSFER</text><text x="366" y="55">03 / VALIDATE</text><text x="536" y="55">04 / STAGE</text><text x="706" y="55">05 / INSTALL</text></g>
<g class="svg-body"><text x="26" y="83">Actual retained</text><text x="26" y="103">operation handle</text><text x="196" y="83">HTTP and</text><text x="196" y="103">cancellation</text><text x="366" y="83">APK package</text><text x="366" y="103">and metadata</text><text x="536" y="83">Owned files</text><text x="536" y="103">on IO</text><text x="706" y="83">Preferred</text><text x="706" y="103">installer</text></g>
<g stroke="#65766a" stroke-width="1.5" marker-end="url(#flow-arrow)"><path d="M154 75H177"/><path d="M324 75H347"/><path d="M494 75H517"/><path d="M664 75H687"/></g>
<rect x="180" y="153" width="312" height="53" rx="8" fill="#f9ede4"/><text x="196" y="176" class="svg-small">HTTP ERROR · MISMATCH · CANCELLATION</text><text x="196" y="196" class="svg-body">Failure or cancelled state. No install.</text>
<rect x="520" y="153" width="310" height="53" rx="8" fill="#eaf1ec"/><text x="536" y="176" class="svg-small">PLATFORM CONFIRMATION / REJECTION</text><text x="536" y="196" class="svg-body">Reconcile the handoff. Never replay blindly.</text>
</svg></div><figcaption>State observation is read-only. Lifecycle, preferred-installer and access-loss rules are confirmed and specified below.</figcaption></figure>'''


def requirement_cards():
    groups = []
    total = 0
    for file in sorted((CHANGE / "specs").glob("*/spec.md")):
        source = file.read_text()
        title = source.splitlines()[0].removeprefix("# ")
        blocks = re.split(r"^### Requirement: ", source, flags=re.M)[1:]
        cards = []
        for block in blocks:
            name, body = block.split("\n", 1)
            description, *scenarios = re.split(r"^#### Scenario: ", body, flags=re.M)
            cases = []
            for scenario in scenarios:
                label, text = scenario.split("\n", 1)
                cases.append("<h5>" + inline(label) + "</h5>" + markdown(text))
            searchable = html.escape((title + " " + name + " " + body).lower(), quote=True)
            total += 1
            cards.append(f'<article class="requirement" data-search="{searchable}"><span class="requirement-number">{total:02d}</span><h4>{inline(name)}</h4>{markdown(description)}<details class="scenarios"><summary>{len(scenarios)} acceptance scenarios</summary>{"".join(cases)}</details></article>')
        opened = " open" if not groups else ""
        groups.append(f'<details class="capability"{opened}><summary><span>{inline(title)}</span><span class="count">{len(blocks)} requirements</span></summary><div class="capability-body">{"".join(cards)}</div></details>')
    return "\n".join(groups), total


def plan_page(file, filename, label, revision):
    global CURRENT_DOCUMENT
    CURRENT_DOCUMENT = file
    source = file.read_text()
    title = source.splitlines()[0].removeprefix("# ")
    chunks = re.split(r"^## (.+)\n", source, flags=re.M)
    prelude = "\n".join(line for line in chunks[0].splitlines()[1:]
                        if not line.startswith(">") and line.strip() != "---")
    sections, nav = [], []
    for index in range(1, len(chunks), 2):
        heading = chunks[index]
        slug = re.sub(r"[^a-z0-9]+", "-", heading.lower()).strip("-")
        body = re.sub(r"^---\s*$", "", chunks[index + 1], flags=re.M)
        sections.append(f'<section id="{slug}" class="spec-section"><div class="section-heading"><h2>{inline(heading)}</h2></div>{markdown(body)}</section>')
        nav.append(f'<a href="#{slug}">{inline(heading)}</a>')
    other_plans = "".join(f'<a href="{name}"' + (' aria-current="page"' if name == filename else "") + f'>{text}</a>' for _, name, text in PLAN_PAGES)
    task_count = len(re.findall(r"^## Task ", source, flags=re.M))
    summary = (f"{task_count} reviewable tasks in the full 15-task plan. Each task defines file ownership, shared interfaces and acceptance checks."
               if task_count else "15 reviewable tasks across eight delivery stages. File ownership, shared interfaces and evidence gates travel with each task.")
    template = (HERE / "plan-template.html").read_text()
    values = {"TITLE": html.escape(title), "LABEL": html.escape(label), "INTRO": markdown(prelude),
              "CONTENT": "\n".join(sections), "NAV": "\n".join(nav), "PLANS": other_plans, "SUMMARY": summary, "REVISION": revision}
    for key, value in values.items():
        template = template.replace("{{" + key + "}}", value)
    assert not re.search(r"\{\{[A-Z]+\}\}", template)
    (DIST / filename).write_text(template)


def build():
    global CURRENT_DOCUMENT
    CURRENT_DOCUMENT = DESIGN
    DIST.mkdir(parents=True, exist_ok=True)
    files = [DESIGN, CHANGE / "proposal.md", CHANGE / "design.md", CHANGE / "tasks.md", CHANGE / ".openspec.yaml", ROOT / "openspec/config.yaml"]
    files += sorted((CHANGE / "specs").glob("*/spec.md"))
    files += [file for file, _, _ in PLAN_PAGES]
    manifest = {str(f.relative_to(ROOT)): hashlib.sha256(f.read_bytes()).hexdigest() for f in files}
    revision = hashlib.sha256(json.dumps(manifest, sort_keys=True).encode()).hexdigest()[:12]
    source = DESIGN.read_text()
    chunks = re.split(r"^## (.+)\n", source, flags=re.M)
    sections, nav = [], []
    for index in range(1, len(chunks), 2):
        heading = re.sub(r"^\d+\.\s+", "", chunks[index])
        number = (index + 1) // 2
        slug = re.sub(r"[^a-z0-9]+", "-", heading.lower()).strip("-")
        diagram = ARCHITECTURE if number == 3 else DOWNLOADS if number == 7 else ""
        sections.append(f'<section id="{slug}" class="spec-section"><div class="section-heading"><span>{number:02d}</span><h2>{inline(heading)}</h2></div>{diagram}{markdown(chunks[index + 1])}</section>')
        nav.append(f'<a href="#{slug}"><span>{number:02d}</span>{inline(heading)}</a>')
    cards, count = requirement_cards()
    template = (HERE / "template.html").read_text()
    for key, value in {"CONTENT": "\n".join(sections), "NAV": "\n".join(nav), "REQUIREMENTS": cards, "COUNT": str(count), "REVISION": revision}.items():
        template = template.replace("{{" + key + "}}", value)
    assert not re.search(r"\{\{[A-Z]+\}\}", template)
    (DIST / "index.html").write_text(template)
    for file, filename, label in PLAN_PAGES:
        plan_page(file, filename, label, revision)
    for name in ("style.css", "reader.js"):
        shutil.copy2(HERE / name, DIST / name)
    for file in files:
        target = DIST / "sources" / file.relative_to(ROOT)
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(file, target)
    (DIST / "sources-manifest.json").write_text(json.dumps({"baseline": "c58f58eb0f8925a03fc579604c7e802a58866e71", "revision": revision, "files": manifest}, indent=2) + "\n")
    with zipfile.ZipFile(DIST / "sleke-module-specification.zip", "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for file in files:
            entry = zipfile.ZipInfo(str(file.relative_to(ROOT)))
            entry.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(entry, file.read_bytes())
    print(f"Built reader: {len(sections)} design sections, {count} requirements, {len(PLAN_PAGES)} plan pages, revision {revision}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, help="Optional output directory for a standalone Site export")
    options = parser.parse_args()
    if options.output:
        DIST = options.output.resolve()
    build()
