# TC-P4-OCR-01 / AI-00 — 선택 영수증 OCR 및 승인 게이트

## Implemented
- User explicitly selects one image in the expense editor.
- Bundled ML Kit Korean Text Recognition v2 16.0.1 runs on device. No external AI request, account, receipt upload or automatic file scan.
- Parser proposes amount/currency/date/merchant only where text explicitly supports it; ambiguous/missing values are left empty. Proposal is kept in Compose memory until dismissed or accepted.
- An explicit "입력란에 반영" applies proposed fields to the editable draft, **not** to the database. The existing normal "지출 저장" validation and confirmation remains required.
- AI proposal gate model rejects missing consent, stale source revision and unconfirmed target. No actual AI server integration or automatic schedule changes.

## Not implemented
- Reliable multi-currency OCR for every language, full receipts OCR accuracy benchmark, local receipt storage/reference attachment, actual AI itineraries/optimization/diary provider.
- External AI: requires provider, server-side credentials, privacy transfer agreement, usage/billing caps, source citations and opt-in settings. Work must first complete service setup.
- Android real images, rotated/cropped receipts, large file handling, UI focus, phone/tablet and OCR failure QA required.
- Evaluate ML Kit bundled SDK and privacy disclosures before store release.

Official Android ML Kit Text Recognition v2 docs: https://developers.google.com/ml-kit/vision/text-recognition/v2/android
Play upload forbidden before entire plan and QA; this is a Draft PR.
