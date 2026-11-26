"""Check the manifest AGP will package for the selected TripCanvas ad mode."""

import argparse
import re
import xml.etree.ElementTree as ET


ANDROID_NAME = "{http://schemas.android.com/apk/res/android}name"
ANDROID_VALUE = "{http://schemas.android.com/apk/res/android}value"
ADS_PERMISSIONS = {
    "com.google.android.gms.permission.AD_ID",
    "android.permission.ACCESS_ADSERVICES_AD_ID",
    "android.permission.ACCESS_ADSERVICES_ATTRIBUTION",
    "android.permission.ACCESS_ADSERVICES_TOPICS",
}
ADS_COMPONENTS = (
    ("provider", "com.google.android.gms.ads.MobileAdsInitProvider"),
    ("service", "com.google.android.gms.ads.AdService"),
    ("meta-data", "com.google.android.gms.ads.APPLICATION_ID"),
)


def verify(manifest, enabled):
    permissions = {node.get(ANDROID_NAME) for node in manifest.findall("uses-permission")}
    if "com.android.vending.BILLING" not in permissions:
        raise ValueError("Play Billing permission is missing from the merged manifest")
    if permissions & ADS_PERMISSIONS != (ADS_PERMISSIONS if enabled else set()):
        raise ValueError("Advertising permissions do not match the configured ad mode")

    for tag, name in ADS_COMPONENTS:
        component = manifest.find(f"./application/{tag}[@{ANDROID_NAME}='{name}']")
        if (component is not None) != enabled:
            raise ValueError(f"Unexpected presence or absence of {name} in the merged manifest")
        if enabled and tag == "meta-data" and not re.fullmatch(
            r"ca-app-pub-[0-9]{16}~[0-9]{10}", component.get(ANDROID_VALUE, "")
        ):
            raise ValueError("Configured AdMob app ID is missing or malformed")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest")
    parser.add_argument("ads_mode", choices=("enabled", "disabled"))
    args = parser.parse_args()
    try:
        verify(ET.parse(args.manifest).getroot(), args.ads_mode == "enabled")
    except ValueError as error:
        parser.exit(1, f"Manifest verification failed: {error}\n")
    print(f"Merged manifest verified with ads {args.ads_mode}.")
