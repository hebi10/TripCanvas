#!/usr/bin/env python3
"""Read or create a draft-only Korea ad-free Play subscription. Never activates sales."""
import argparse
import json
import os
import sys
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

PACKAGE = "com.hebi.tripcanvas"
PRODUCT = "tripcanvas_ad_free"
PLANS = {"monthly": ("P1M", 1000), "yearly": ("P1Y", 9900)}
BASE = f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{PACKAGE}"


class CatalogError(RuntimeError):
    pass


def api(token: str, method: str, path: str, payload=None):
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    request = Request(
        BASE + path, data=data, method=method,
        headers={
            "Authorization": "Bearer " + token,
            "Accept": "application/json",
            "Content-Type": "application/json",
        },
    )
    try:
        with urlopen(request, timeout=30) as response:
            body = response.read(1_000_001)
            if len(body) > 1_000_000:
                raise CatalogError("Unexpected catalog response size")
            return json.loads(body) if body else {}
    except HTTPError as error:
        if error.code == 404 and method == "GET":
            return None
        raise CatalogError(f"Android Publisher API returned HTTP {error.code}") from None
    except (OSError, URLError, ValueError) as error:
        raise CatalogError("Android Publisher catalog request failed") from None


def money(won):
    return {"currencyCode": "KRW", "units": str(won)}


def catalog_payload():
    return {
        "packageName": PACKAGE,
        "productId": PRODUCT,
        "listings": [
            {"languageCode": "ko-KR", "title": "TripCanvas 광고 제거",
             "benefits": ["TripCanvas 홈 광고 제거"],
             "description": "구독이 활성화된 기간 동안 TripCanvas 홈 배너 광고가 표시되지 않습니다."},
            {"languageCode": "en-US", "title": "TripCanvas Ad-Free",
             "benefits": ["Remove TripCanvas home banner ads"],
             "description": "No home banner ads while your subscription remains active."},
        ],
        "basePlans": [
            {
                "basePlanId": base,
                "autoRenewingBasePlanType": {"billingPeriodDuration": period},
                "regionalConfigs": [
                    {"regionCode": "KR", "newSubscriberAvailability": True, "price": money(won)}
                ],
            }
            for base, (period, won) in PLANS.items()
        ],
    }


def verify(existing):
    if existing is None:
        return {"exists": False, "productId": PRODUCT, "saleActivated": False}
    if existing.get("packageName") != PACKAGE or existing.get("productId") != PRODUCT:
        raise CatalogError("Unexpected subscription identity")
    plans = {p["basePlanId"]: p for p in existing.get("basePlans", [])}
    if set(plans) != set(PLANS):
        raise CatalogError("Existing subscription has a different set of base plans")
    summary = {}
    for base, (period, expected_won) in PLANS.items():
        item = plans[base]
        if item.get("autoRenewingBasePlanType", {}).get("billingPeriodDuration") != period:
            raise CatalogError(f"Unexpected billing period for {base}")
        korean = next((x for x in item.get("regionalConfigs", []) if x.get("regionCode") == "KR"), None)
        if korean is None or korean.get("price", {}).get("currencyCode") != "KRW" or int(
            korean.get("price", {}).get("units", 0)
        ) != expected_won or int(korean.get("price", {}).get("nanos", 0)) != 0:
            raise CatalogError(f"Unexpected Korean price for {base}")
        summary[base] = {"state": item.get("state", "STATE_UNSPECIFIED"), "priceKRW": expected_won}
    return {
        "exists": True, "productId": PRODUCT, "plans": summary,
        "saleActivated": any(p["state"] == "ACTIVE" for p in summary.values()),
    }


def run(token, mode, approved):
    existing = api(token, "GET", f"/subscriptions/{PRODUCT}")
    if existing is not None:
        return verify(existing)
    if mode == "check":
        return verify(None)
    if not approved:
        raise CatalogError("Draft creation requires explicit CATALOG_DRAFT_APPROVED=true")
    region = api(token, "POST", "/pricing:convertRegionPrices", {"price": money(1000)})
    version = region.get("regionVersion", {}).get("version")
    if not version or not isinstance(version, str):
        raise CatalogError("Cannot obtain current supported Play regions version")
    query = urlencode({"productId": PRODUCT, "regionsVersion.version": version})
    created = api(token, "POST", "/subscriptions?" + query, catalog_payload())
    state = verify(created)
    if state["saleActivated"]:
        raise CatalogError("Draft creation returned unexpectedly active plans")
    reread = api(token, "GET", f"/subscriptions/{PRODUCT}")
    readback = verify(reread)
    if readback["saleActivated"]:
        raise CatalogError("Draft unexpectedly activated on readback")
    return readback


def main(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument("--mode", choices=("check", "create-draft"), default="check")
    args = parser.parse_args(argv)
    token = os.environ.get("PLAY_ACCESS_TOKEN", "").strip()
    if not token:
        parser.error("PLAY_ACCESS_TOKEN must be supplied by existing GitHub Workload Identity")
    try:
        report = run(
            token, args.mode,
            os.environ.get("CATALOG_DRAFT_APPROVED") == "true",
        )
        print(json.dumps(report, ensure_ascii=False, sort_keys=True))
    except (CatalogError, ValueError) as error:
        print("Catalog operation blocked: " + str(error), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
