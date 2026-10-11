"""Catch the realme white fallback plate / small square blue-bird regression.

Final APK integration check (optional androguard dependency):
python tests/check_launcher_icon_apk.py patched.apk
"""
import sys
from pathlib import Path
import xml.etree.ElementTree as ET
from zipfile import ZipFile

from loguru import logger
logger.remove()
from androguard.core.apk import APK
from androguard.core.axml import AXMLPrinter

ANDROID = "{http://schemas.android.com/apk/res/android}"


def check(path):
    apk = APK(path)
    resources = apk.get_android_resources()
    manifest = apk.get_android_manifest_xml()
    aliases = manifest.findall("application/activity-alias")
    bird = [a for a in aliases if a.get(ANDROID + "name", "").endswith(".launcher.BlueBird")]
    assert len(bird) == 1, "Expected one switchable blue-bird launcher alias"
    icon = bird[0].get(ANDROID + "icon")
    assert icon == bird[0].get(ANDROID + "roundIcon"), "Launcher masks must use the same adaptive layers"
    resource_id = int(icon.removeprefix("@"), 16)
    configs = resources.get_resolved_res_configs(resource_id)
    adaptive = {config.get_sdk_version(): file for config, file in configs if file.endswith(".xml")}
    assert 26 in adaptive and 33 in adaptive, "Missing adaptive and themed launcher variants"
    official = ET.parse(Path(__file__).resolve().parents[1] / "docs/assets/twitter-logo.svg")
    official_path = official.find(".//{http://www.w3.org/2000/svg}path").get("d")
    with ZipFile(path) as files:
        for api, file in adaptive.items():
            root = AXMLPrinter(files.read(file)).get_xml_obj()
            assert root.tag == "adaptive-icon", "OEM launcher would normalize a legacy bitmap into a plate"
            layers = {node.tag: node.get(ANDROID + "drawable") for node in root}
            background_id = int(layers["background"].removeprefix("@"), 16)
            backgrounds = resources.get_resolved_res_configs(background_id)
            assert backgrounds, "Missing full-bleed drawable background"
            for _, background in backgrounds:
                shape = AXMLPrinter(files.read(background)).get_xml_obj()
                assert shape.tag == "shape"
                assert shape.get(ANDROID + "shape", "rectangle") in ("rectangle", "0")
                assert len(shape) == 1 and shape[0].tag == "solid", "Background must have no inset or rounding"
                assert shape[0].get(ANDROID + "color").lower() == "#ff1da1f2"
            if api >= 33:
                assert layers.get("monochrome") == layers["foreground"], "Themed icons need the bird silhouette"
            foreground_id = int(layers["foreground"].removeprefix("@"), 16)
            foregrounds = resources.get_resolved_res_configs(foreground_id)
            assert foregrounds, "Missing vector foreground"
            for _, foreground in foregrounds:
                assert foreground.endswith(".xml"), "Foreground must scale as a vector, not a bitmap"
                vector = AXMLPrinter(files.read(foreground)).get_xml_obj()
                assert vector.tag == "vector"
                group = vector.find("group")
                paths = vector.findall(".//path")
                assert group is not None and len(paths) == 1, "Unexpected vector artwork/background"
                assert abs(float(group.get(ANDROID + "scaleX")) - 44 / 248) < .00001
                assert group.get(ANDROID + "scaleX") == group.get(ANDROID + "scaleY"), "Bird proportions changed"
                assert paths[0].get(ANDROID + "fillColor").lower() == "#ffffffff"
                assert "".join(paths[0].get(ANDROID + "pathData").split()) == "".join(official_path.split()), "Official SVG path changed"
    for name, expected in (("piko_newx_app_icon_blue_bird", "经典"),
                           ("piko_newx_app_icon_classic_title", "经典图标")):
        string_id = resources.get_res_id_by_key(apk.get_package(), "string", name)
        assert string_id and expected in [v for _, v in resources.get_resolved_res_configs(string_id)], name
    print("PASS: adaptive icon/roundIcon, full-bleed blue background, official SVG vector foreground, themed silhouette and Classic labels")


if __name__ == "__main__":
    check(sys.argv[1])
