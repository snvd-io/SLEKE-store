# SLEKE specification web reader

This dependency-free reader is generated from the approved Superpowers design, the full implementation plan and OpenSpec artifacts. Edit those source documents, then run:

```sh
python3 docs/spec-web/build.py
python3 -m http.server 8765 --directory docs/spec-web/dist --bind 127.0.0.1
```

The generated `dist` includes the specification, an implementation overview and four subsystem plan pages, searchable behavior requirements, a source archive and a SHA-256 source manifest. Local document links resolve through an explicit page allowlist. `build.py` supports the controlled headings, tables, lists, links and inline code used by these documents. Review visual changes in desktop and narrow viewports before publishing.

The hosting identity is retained in `.openai/hosting.json`. Publication uses a sanitized standalone Site checkout rather than pushing the Android repository. Keep repository credentials out of files and output.
