# TC-P3-MEDIA-01 — 여행 사진 원본 ZIP 내보내기

- Based on P3 LIVE/MEM/REPORT main.
- Separate from the JSON metadata backup. No DB schema or backup format changes.

The TravelMemories screen offers an explicit CreateDocument ZIP action for the current trip's *user-selected* stored photos. It streams original photo bytes into photos/000.jpg etc with a manifest of moment ID, optional visit ID, caption, timestamp and file MIME type. Original file paths/URI strings are not exported. The original media and Room records are not modified.

Limits: maximum 100 photos per archive, maximum 25MiB per photo and 200MiB of copied originals total; require image MIME and readable content URI. Any failed/missing or expired permission fails export instead of falsely reporting a complete backup. The destination ZIP is **not encrypted**, contains private photos/notes and must be protected by the user.

This PR is **export-only**: ZIP file can be opened outside the app; importing it as linked photos into a different device is not implemented. A complete P3 portable restore requires a separate archive preview, safe entry validation/size caps, selected-trip mapping, app-private media storage, URI replacement and transactional backup import with failure rollback. Re-import of ZIP must NOT be offered as completed until tested.

Test on real SAF image provider, 100 item/200MiB limits, permission expiry, stream failures, export cancellation, phone/tablet and memory pressure. Play upload is frozen.
