"""Catch the realme white fallback plate / small square blue-bird regression.

Final APK integration check (optional androguard and Pillow dependencies):
python tests/check_launcher_icon_apk.py patched.apk
"""
import io
import sys
from zipfile import ZipFile

from loguru import logger
logger.remove()
from androguard.core.apk import APK
from androguard.core.axml import AXMLPrinter
from PIL import Image

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
    with ZipFile(path) as files:
        for api, file in adaptive.items():
            root = AXMLPrinter(files.read(file)).get_xml_obj()
            assert root.tag == "adaptive-icon", "OEM launcher would normalize a legacy bitmap into a plate"
            layers = {node.tag: node.get(ANDROID + "drawable") for node in root}
            color_id = int(layers["background"].removeprefix("@"), 16)
            colors = resources.get_resolved_res_configs(color_id)
            assert colors and all(value.lower() == "#ff1da1f2" for _, value in colors), colors
            if api >= 33:
                assert layers.get("monochrome") == layers["foreground"], "Themed icons need the bird silhouette"
            foreground_id = int(layers["foreground"].removeprefix("@"), 16)
            foregrounds = resources.get_resolved_res_configs(foreground_id)
            assert len(foregrounds) >= 5, "Missing foreground density variants"
            for _, foreground in foregrounds:
                image = Image.open(io.BytesIO(files.read(foreground))).convert("RGBA")
                width, height = image.size
                assert width == height and image.getpixel((0, 0))[3] == 0
                bounds = image.getchannel("A").getbbox()
                assert bounds and all(width * .2 < v < width * .8 for v in bounds), bounds
                assert all(r == g == b == 255 for r, g, b, alpha in image.getdata() if alpha), (
                    "Foreground embeds a background; expected only the white bird on transparency"
                )
    print("PASS: alias icon/roundIcon use full-bleed blue adaptive layers, five safe-zone foreground densities and themed silhouette")


if __name__ == "__main__":
    check(sys.argv[1])
