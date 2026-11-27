"""Exercise release APKs on a disposable Android emulator using public UI only."""
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path


APP = "com.hebi.tripcanvas"
TRIP = "DEX02 Offline Trip"
EXPENSE = "12873"
HIERARCHY = "/sdcard/dex02-tripcanvas.xml"


def adb(*args, binary=False):
    return subprocess.run(["adb", *args], check=True, capture_output=True,
                          text=not binary, timeout=75).stdout


def hierarchy():
    adb("shell", "uiautomator", "dump", HIERARCHY)
    return adb("shell", "cat", HIERARCHY)


def match_label(expected, contains=False):
    def matches(node):
        labels = [node.get("text", ""), node.get("content-desc", "")]
        return any(expected in label if contains else expected == label for label in labels)
    return matches


def start():
    adb("shell", "am", "force-stop", APP)
    out = adb("shell", "am", "start", "-W", "-n", f"{APP}/.MainActivity")
    if "Status: ok" not in out:
        raise AssertionError(out)


class Ui:
    def __init__(self):
        size = adb("shell", "wm", "size")
        self.width, self.height = map(int, re.findall(r"(\d+)x(\d+)", size)[-1])

    def find(self, matches, description, *, scroll=False):
        scrolls = [None] * 4 + (["down"] * 8 + ["up"] * 12 if scroll else [None] * 7)
        for direction in scrolls:
            if direction:
                top, bottom = self.height // 3, self.height * 3 // 4
                y1, y2 = (bottom, top) if direction == "down" else (top, bottom)
                adb("shell", "input", "swipe", str(self.width // 2), str(y1),
                    str(self.width // 2), str(y2), "350")
            time.sleep(0.55)
            try:
                root = ET.fromstring(hierarchy())
            except (subprocess.CalledProcessError, ET.ParseError):
                continue
            for node in root.iter("node"):
                if matches(node):
                    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds", "")))
                    if x2 > x1 and y2 > y1:
                        return ((x1 + x2) // 2, (y1 + y2) // 2)
        raise AssertionError(f"Missing UI element: {description}")

    def tap(self, matches, description, *, scroll=False):
        x, y = self.find(matches, description, scroll=scroll)
        adb("shell", "input", "tap", str(x), str(y))

    def label(self, value, *, scroll=False):
        self.tap(match_label(value), value, scroll=scroll)

    def require(self, value, *, contains=False, scroll=False):
        self.find(match_label(value, contains), value, scroll=scroll)


def evidence(folder, label):
    (folder / f"{label}.png").write_bytes(adb("exec-out", "screencap", "-p", binary=True))
    (folder / f"{label}.xml").write_text(hierarchy(), encoding="utf-8")


def enter_first_field(ui, text):
    ui.tap(lambda node: node.get("class") == "android.widget.EditText", "editable field")
    adb("shell", "input", "text", text.replace(" ", "%s"))
    adb("shell", "input", "keyevent", "KEYCODE_BACK")


def run(folder):
    if not any(adb("shell", "getprop", name).strip() == "1"
               for name in ("ro.kernel.qemu", "ro.boot.qemu")):
        raise AssertionError("A disposable Android emulator is required")
    if adb("shell", "pm", "list", "packages", APP).strip():
        raise AssertionError("Baseline must be installed on a clean emulator")
    adb("shell", "svc", "wifi", "disable")
    adb("shell", "svc", "data", "disable")
    adb("install", str(folder / "baseline.apk"))
    start()
    ui = Ui()
    ui.label("여행 시작", scroll=True)
    ui.label("새 여행 만들기", scroll=True)
    ui.require("여행 기본 정보")
    enter_first_field(ui, TRIP)
    ui.label("여행 만들기", scroll=True)
    ui.require("여행 한눈에")
    ui.label("경비 열기", scroll=True)
    ui.label("지출 추가", scroll=True)
    ui.require("지출 정보")
    enter_first_field(ui, EXPENSE)
    ui.label("지출 저장", scroll=True)
    ui.require("12,873", contains=True, scroll=True)
    evidence(folder, "baseline-expense")

    adb("install", "-r", str(folder / "candidate.apk"))
    start()
    ui.require(TRIP, scroll=True)
    ui.label(TRIP, scroll=True)
    ui.label("경비 열기", scroll=True)
    ui.require("12,873", contains=True, scroll=True)
    evidence(folder, "optimized-persisted-expense")
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    ui.label("일정 열기", scroll=True)
    ui.require("일정 추가", scroll=True)
    evidence(folder, "optimized-offline-schedule")

    start()
    ui.label("설정", scroll=True)
    ui.require("구매 내역 복원·확인", scroll=True)
    evidence(folder, "optimized-billing-restore-entry")
    print("Verified offline startup, preserved trip/expense, schedule screen, and purchase restore entry")


if __name__ == "__main__":
    output = Path(sys.argv[1])
    try:
        run(output)
    except Exception:
        for capture in (lambda: evidence(output, "failure"),
                        lambda: (output / "logcat.txt").write_text(adb("logcat", "-d"), encoding="utf-8")):
            try:
                capture()
            except Exception as error:
                print(f"Failed to collect diagnostics: {error}", file=sys.stderr)
        raise
