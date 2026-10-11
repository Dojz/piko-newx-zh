"""Detect the mixed extension ABI that caused duplicate settings at startup.

Optional final-APK integration check (requires androguard):
python tests/check_settings_apk.py patched.apk
"""
import sys
from collections import Counter
from zipfile import ZipFile

from loguru import logger
logger.remove()
from androguard.core.dex import DEX

APPLICATION = "Lcom/x/android/XApplication;"
LEGACY = "Lapp/morphe/extension/newx/settings/SettingsRegistry;"
SHARED = "Lapp/morphe/extension/crimera/settings/SettingsRegistry;"
HOST = "Lapp/morphe/extension/newx/settings/NewXSettingsHost;"


def check(path):
    extension_classes = Counter()
    startup = []
    with ZipFile(path) as apk:
        for name in apk.namelist():
            if not name.endswith(".dex"):
                continue
            data = apk.read(name)
            if b"Lapp/morphe/extension/" not in data and APPLICATION.encode() not in data:
                continue
            for definition in DEX(data).get_classes():
                owner = definition.get_name()
                if owner.startswith("Lapp/morphe/extension/"):
                    extension_classes[owner] += 1
                if owner != APPLICATION:
                    continue
                for method in definition.get_methods():
                    if method.get_name() == "onCreate" and method.get_code():
                        startup.extend(i.get_output() for i in method.get_instructions()
                                       if i.get_name().startswith("invoke-static"))
    duplicates = [owner for owner, count in extension_classes.items() if count > 1]
    assert not duplicates, f"Duplicate extension classes: {duplicates}"
    assert not (LEGACY in extension_classes and HOST in extension_classes), (
        "Mixed NewX extension ABIs: legacy SettingsRegistry and shared NewXSettingsHost"
    )
    loads = [call for call in startup if LEGACY + "->load()V" in call or SHARED + "->load()V" in call]
    assert len(loads) == 1, f"Expected one NewX settings startup path, found {loads}"
    print("PASS: one extension/settings implementation and one settings startup path")


if __name__ == "__main__":
    check(sys.argv[1])
