#!/usr/bin/env python3
"""Render ko-KR Play screenshots using only verified real Android QA PNGs.

Android screenshot pixels are never synthesized, cropped, retouched or annotated.
All editorial text sits outside the screenshot frame. No Play API calls.
"""
from __future__ import annotations

import argparse
import hashlib
import io
import json
import re
import shutil
import sys
from datetime import date
from pathlib import Path
from zipfile import ZipFile

try:
    from PIL import Image, ImageDraw, ImageFont
except ImportError as error:
    raise SystemExit("Pillow is required for this offline artwork step.") from error

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "assets" / "play-store" / "ko-KR"
TARGET_WIDTH = 1350
TARGET_HEIGHT = 2400
TARGET_FILES = (
    ("01-home.png", "2c", "01-home-light.png", "여행을 한눈에", "내 여행의 계획과 상태를 확인"),
    ("02-workspace.png", "2c", "02a-workspace-overview-light.png", "여행 작업 공간", "일정·경비·준비물을 한곳에서"),
    ("03-schedule.png", "2c", "02d-workspace-schedule-route.png", "날짜별 일정 관리", "시간과 장소를 한 화면에서"),
    ("04-map.png", "3a", "03-map-all-light.png", "저장 장소와 지도", "장소와 여행 일정을 연결"),
    ("05-checklist.png", "2c", "03-checklist-light.png", "준비물 체크리스트", "완료 항목과 남은 준비물 확인"),
    ("06-expenses.png", "2c", "07-expenses-light.png", "예산과 지출 관리", "남은 예산과 미환산 지출 구분"),
    ("07-settlement.png", "3b", "05-settlement-light.png", "동행자 비용 정산", "참가자별 지불·부담과 정산 제안"),
    ("08-bookings.png", "3b", "08-bookings-light.png", "예약 자료 한곳에", "항공·숙소·교통 예약 정보 보관"),
)
ASSET_NAMES = ("store-icon-512.png", "feature-graphic-1024x500.png")
DEFAULT_FONTS = {
    "bold": [
        "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc",
        "/usr/share/fonts/truetype/nanum/NanumGothicBold.ttf",
    ],
    "regular": [
        "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
        "/usr/share/fonts/truetype/nanum/NanumGothic.ttf",
    ],
}


def sha256(blob: bytes) -> str:
    return hashlib.sha256(blob).hexdigest()


def font_path(name: str, override: str | None) -> str:
    for candidate in ([override] if override else []) + DEFAULT_FONTS[name]:
        if candidate and Path(candidate).is_file():
            return candidate
    raise ValueError(f"Korean {name} font is missing; provide --font-{name}")


def validate_provenance(evidence: dict, zips: dict[str, Path]) -> tuple[str, dict]:
    source = evidence.get("main_sha")
    if not isinstance(source, str) or re.fullmatch(r"[a-f0-9]{40}", source) is None:
        raise ValueError("Expected verified 40-digit main_sha")
    runs = evidence.get("runs")
    if not isinstance(runs, dict) or set(runs) != set(zips):
        raise ValueError("Exactly 2c, 3a and 3b successful QA runs are required")
    checked = {}
    for kind, path in zips.items():
        row = runs[kind]
        if (row.get("head_sha") != source or row.get("conclusion") != "success" or
                not isinstance(row.get("run_id"), int) or row["run_id"] <= 0):
            raise ValueError(f"{kind} evidence was not verified for exact main")
        actual = sha256(path.read_bytes())
        if row.get("artifact_sha256") != actual:
            raise ValueError(f"{kind} screenshot ZIP hash differs from verified evidence")
        checked[kind] = {"run_id": row["run_id"], "sha256": actual}
    return source, checked


def get_qa_png(path: Path, filename: str) -> bytes:
    with ZipFile(path, "r") as zipped:
        matches = [
            name for name in zipped.namelist()
            if name.startswith("screenshots/") and name.endswith("/" + filename)
        ]
        if len(matches) != 1:
            raise ValueError(f"Missing or duplicate real Android PNG {filename} in {path.name}")
        data = zipped.read(matches[0])
    with Image.open(io.BytesIO(data)) as image:
        image.verify()
    with Image.open(io.BytesIO(data)) as image:
        width, height = image.size
    if width < 700 or height < 1200 or not (0.43 <= width / height <= 0.72):
        raise ValueError(f"Unexpected phone screenshot aspect for {filename}: {width}x{height}")
    return data


def compose(source_png: bytes, title: str, subtitle: str, fonts: tuple[str, str]) -> bytes:
    with Image.open(io.BytesIO(source_png)) as original:
        screen = original.convert("RGB")
        scale = min(1100 / screen.width, 1900 / screen.height)
        target = (round(screen.width * scale), round(screen.height * scale))
        screen = screen.resize(target, Image.Resampling.LANCZOS)

    result = Image.new("RGB", (TARGET_WIDTH, TARGET_HEIGHT), "#FFFFFF")
    d = ImageDraw.Draw(result)
    main = ImageFont.truetype(fonts[0], 70)
    sub = ImageFont.truetype(fonts[1], 36)
    footer = ImageFont.truetype(fonts[1], 25)
    d.text((110, 72), title, fill="#111111", font=main)
    d.text((110, 186), subtitle, fill="#555555", font=sub)
    d.line([(110, 298), (1240, 298)], fill="#D8D8D8", width=2)
    x = (TARGET_WIDTH - screen.width) // 2
    y = 365 + (1900 - screen.height) // 2
    result.paste(screen, (x, y))
    d.rectangle((x-2, y-2, x+screen.width+1, y+screen.height+1),
                outline="#BBBBBB", width=2)
    d.text((110, 2330), "TripCanvas  |  실제 Android 화면", fill="#666666", font=footer)
    output = io.BytesIO()
    result.save(output, format="PNG", optimize=True)
    return output.getvalue()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    for kind in ("2c", "3a", "3b"):
        parser.add_argument(f"--qa-{kind}", required=True, type=Path)
    parser.add_argument("--evidence", required=True, type=Path,
                        help="Reviewed GitHub Actions run IDs, exact main SHA and ZIP SHA-256")
    parser.add_argument("--output", required=True, type=Path,
                        help="Separate staging directory; not Play and not an automatic upload")
    parser.add_argument("--font-bold")
    parser.add_argument("--font-regular")
    parser.add_argument("--allow-replace-assets", action="store_true",
                        help="Requires completed human QA; not used for draft generation")
    args = parser.parse_args()
    output = args.output.resolve()
    if output == ASSETS.resolve() and not args.allow_replace_assets:
        raise ValueError("Refusing to replace checked-in store assets without explicit flag")
    zips = {kind: getattr(args, "qa_" + kind) for kind in ("2c", "3a", "3b")}
    evidence = json.loads(args.evidence.read_text(encoding="utf-8"))
    main_sha, verified = validate_provenance(evidence, zips)
    fonts = (font_path("bold", args.font_bold), font_path("regular", args.font_regular))

    outputs = []
    for icon in ASSET_NAMES:
        raw = (ASSETS / icon).read_bytes()
        outputs.append((icon, raw, "Existing verified TripCanvas icon/feature graphic"))
    for filename, group, qa_name, title, subtitle in TARGET_FILES:
        original = get_qa_png(zips[group], qa_name)
        image = compose(original, title, subtitle, fonts)
        provenance = (f"Real Android {group} run {verified[group]['run_id']}; " +
                      f"{qa_name}; original_png_sha256={sha256(original)}")
        outputs.append((filename, image, provenance))
    output.mkdir(parents=True, exist_ok=True)
    manifest_entries = []
    for idx, (name, data, note) in enumerate(outputs):
        target = output / name
        target.write_bytes(data)
        with Image.open(io.BytesIO(data)) as image:
            dimensions, mode = list(image.size), image.mode
        manifest_entries.append({
            "order": idx, "name": name, "size_bytes": len(data),
            "sha256": sha256(data), "pixels": dimensions,
            "mode": mode, "source": note
        })
    manifest = {
        "app": "TripCanvas", "package": "com.hebi.tripcanvas",
        "version_basis": f"Exact integrated main commit {main_sha}",
        "created_date": date.today().isoformat(),
        "store_locale": "ko-KR",
        "design": "neutral monochrome editorial; no gradient, shadow or rounded frame",
        "source_note": "Only framing and captions outside real Android screenshots; no screenshot edits",
        "source_main_sha": main_sha,
        "source_workflow_runs": verified,
        "files": manifest_entries
    }
    (output / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Prepared 8 real-UI screenshots and 2 unchanged brand assets in {output}")
    print(f"Exact verified main: {main_sha}. Manual visual review and Play API upload are separate.")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (ValueError, OSError, KeyError) as error:
        print(f"Error: {error}", file=sys.stderr)
        sys.exit(1)
