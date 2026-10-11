"""Exercise ARSCLib's real resource encoder with a synthetic launcher fixture.

Usage: python tests/check_launcher_resource_encoding.py morphe-cli.jar [patches.mpp]
Requires JPype1 and Java 21. No user APK or device is used.
"""
import re
import shutil
import sys
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path
from zipfile import ZipFile

import jpype

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'piko-overrides/patches/src/main/resources/newx/launcher/drawable'
NAMES = ('piko_launcher_classic_background', 'piko_launcher_classic_foreground', 'piko_classic_splash')
ANDROID = '{http://schemas.android.com/apk/res/android}'


def check(cli, bundle=None):
    source = (ROOT / 'piko-overrides/patches/src/main/kotlin/app/crimera/patches/newx/misc/appicon/LauncherIconPatch.kt').read_text()
    template = re.search(r'folder.resolve\("piko_launcher_blue_bird.xml"\).writeText\(\s*"""(.*?)"""', source, re.S).group(1)
    assert '@drawable/piko_launcher_classic_background' in template
    assert 'piko_launcher_blue_bird_colors.xml' not in source
    shape = ET.parse(ASSETS / (NAMES[0] + '.xml')).getroot()
    assert shape.tag == 'shape' and shape.get(ANDROID + 'shape') == 'rectangle'
    assert len(shape) == 1 and shape[0].tag == 'solid'
    assert shape[0].get(ANDROID + 'color') == '#ff1da1f2'
    if bundle:
        with ZipFile(bundle) as archive:
            for name in NAMES:
                paths = [p for p in archive.namelist() if p.endswith('newx/launcher/drawable/' + name + '.xml')]
                assert len(paths) == 2, paths
                for path in paths:
                    assert archive.read(path) == (ASSETS / (name + '.xml')).read_bytes(), path
        print('PASS: both unified implementations contain the fixed launcher layers')

    jpype.startJVM(classpath=[str(Path(cli).resolve())])
    J = jpype.JClass
    File = J('java.io.File')
    Encoder = J('com.reandroid.apk.ApkModuleXmlEncoder')
    with tempfile.TemporaryDirectory(prefix='launcher-resource-encoding-') as temporary:
        work = Path(temporary)
        res = work / 'resources/package_1/res'
        values = res / 'values'
        drawable = res / 'drawable'
        values.mkdir(parents=True)
        drawable.mkdir()
        (work / 'AndroidManifest.xml').write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.example.launcherfixture"><uses-sdk android:minSdkVersion="26" android:targetSdkVersion="33"/><application android:icon="@mipmap/piko_launcher_blue_bird"/></manifest>')
        public = [f'<public type="drawable" name="{name}" id="0x7f08000{i}"/>' for i, name in enumerate(NAMES)]
        public += ['<public type="mipmap" name="piko_launcher_blue_bird" id="0x7f100000"/>', '<public type="color" name="piko_launcher_blue_bird_background" id="0x7f060000"/>']
        (values / 'public.xml').write_text('<resources package="com.example.launcherfixture" id="0x7f">' + ''.join(public) + '</resources>')
        for name in NAMES:
            shutil.copyfile(ASSETS / (name + '.xml'), drawable / (name + '.xml'))
        for api in (26, 33):
            folder = res / f'mipmap-anydpi-v{api}'
            folder.mkdir()
            monochrome = '<monochrome android:drawable="@drawable/piko_launcher_classic_foreground"/>' if api == 33 else ''
            (folder / 'piko_launcher_blue_bird.xml').write_text(template.replace('$monochrome', monochrome))

        # The old name fails despite having a valid public color ID: ARSCLib derives
        # the values resource type from the basename, not from the <color> tag.
        bad = values / 'piko_launcher_blue_bird_colors.xml'
        bad.write_text('<resources><color name="piko_launcher_blue_bird_background">#ff1da1f2</color></resources>')
        encoder = Encoder()
        encoder.getApkModule().setPreferredFramework(J('java.lang.Integer')(33))
        try:
            try:
                encoder.scanDirectory(File(str(work)))
            except Exception as error:
                assert 'Undefined entry name' in str(error), str(error)
                print('PASS: reproduced reported Undefined entry name with the old values filename')
            else:
                raise AssertionError('Old values filename unexpectedly compiled')
        finally:
            encoder.getApkModule().close()
        bad.unlink()

        encoder = Encoder()
        module = encoder.getApkModule()
        module.setPreferredFramework(J('java.lang.Integer')(33))
        try:
            encoder.scanDirectory(File(str(work)))
            output = work / 'encoded-resources.zip'
            writer = module.createApkFileWriter(File(str(output)))
            try:
                writer.write()  # Force compilation of deferred binary XML inputs.
            finally:
                writer.close()
            with ZipFile(output) as archive:
                assert 'resources.arsc' in archive.namelist()
                for name in NAMES:
                    path = 'res/drawable/' + name + '.xml'
                    assert path in archive.namelist(), archive.namelist()
                    assert not archive.read(path).lstrip().startswith(b'<'), 'Drawable XML was not compiled'
                for api in (26, 33):
                    path = f'res/mipmap-anydpi-v{api}/piko_launcher_blue_bird.xml'
                    assert path in archive.namelist(), archive.namelist()
                    assert not archive.read(path).lstrip().startswith(b'<'), 'Adaptive XML was not compiled'
            print('PASS: ARSCLib full encoder compiled opaque drawable background, official vectors and API 26/33 adaptive icons')
        finally:
            module.close()


if __name__ == '__main__':
    check(*sys.argv[1:])
