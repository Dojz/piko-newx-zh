import os
import re
import shutil
import subprocess
import tempfile
from dataclasses import dataclass
from pathlib import Path

PIKO_REPO = "crimera/piko"
PIKO_REPOSITORY = f"https://github.com/{PIKO_REPO}.git"
PIKO_BRANCH = "x-lite"
REPO_ROOT = Path(__file__).resolve().parent
ZH_CN_NEWX_STRINGS = REPO_ROOT / "translations" / "newx-zh-rCN.xml"
ZH_CN_INSTAGRAM_STRINGS = REPO_ROOT / "translations" / "instagram-zh-rCN.xml"
PIKO_OVERLAY_DIR = REPO_ROOT / "piko-overrides"
INSTAGRAM_ACTIONBAR = (
    "extensions/instagram/src/main/java/app/morphe/extension/instagram/patches/actionbar/ActionBarPatch.java"
)
XLITE_CONSTANTS = (
    "patches/src/main/kotlin/app/crimera/patches/newx/utils/Constants.kt"
)


@dataclass(frozen=True)
class PikoBuild:
    commit: str
    supported_versions: frozenset[str]


def get_supported_versions(constants: str) -> frozenset[str]:
    """Return the X-Lite app versions supported by the checked-out Piko source."""
    versions = frozenset(
        re.findall(r'AppTarget\(version\s*=\s*"([^"]+)"\)', constants)
    )
    if not versions:
        raise ValueError("Could not find X-Lite compatible app versions in Piko")
    return versions



def apply_zh_cn(piko_directory: Path) -> None:
    """Overlay the maintained Simplified Chinese NewX and Instagram resources."""
    overlays = {
        ZH_CN_NEWX_STRINGS:
            "patches/src/main/resources/addresources/values-zh-rCN/newx/strings.xml",
        ZH_CN_INSTAGRAM_STRINGS:
            "patches/src/main/resources/addresources/values-zh-rCN/instagram/strings.xml",
    }
    for source, relative_target in overlays.items():
        if not source.is_file():
            raise FileNotFoundError(f"Missing zh-CN translation overlay: {source}")
        target = piko_directory / relative_target
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)


def apply_source_overlays(piko_directory: Path) -> None:
    """Copy fork-owned source additions into the temporary Piko checkout."""
    if not PIKO_OVERLAY_DIR.exists():
        return
    for source in PIKO_OVERLAY_DIR.rglob("*"):
        if not source.is_file():
            continue
        relative = source.relative_to(PIKO_OVERLAY_DIR)
        target = piko_directory / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)


def install_instagram_screen_translate_button(piko_directory: Path) -> None:
    """Add one native-styled full-screen translate icon to Instagram action bars."""
    path = piko_directory / INSTAGRAM_ACTIONBAR
    text = path.read_text(encoding="utf-8")

    import_anchor = "import app.morphe.extension.instagram.constants.Constants;\n"
    import_line = "import app.morphe.extension.instagram.patches.translate.ScreenTranslator;\n"
    if import_line not in text:
        if import_anchor not in text:
            raise ValueError("Instagram ActionBarPatch import anchor changed upstream")
        text = text.replace(import_anchor, import_anchor + import_line, 1)

    helper = """
    private static void addScreenTranslateButton(ViewGroup viewGroup) {
        if (viewGroup == null) {
            return;
        }
        ImageView imageView = UI.addImageViewToViewGroup(
            viewGroup,
            "instagram_translate_pano_outline_24",
            ScreenTranslator::translateVisibleScreen
        );
        if (imageView != null) {
            imageView.setContentDescription("全屏翻译");
        }
    }

"""
    class_anchor = "public class ActionBarPatch {\n"
    if "private static void addScreenTranslateButton" not in text:
        if class_anchor not in text:
            raise ValueError("Instagram ActionBarPatch class anchor changed upstream")
        text = text.replace(class_anchor, class_anchor + helper, 1)

    for method in (
        "mainFeedActionBarButton",
        "userProfileActionBarButton",
        "chatActionBarButton",
        "inboxActionBarButton",
    ):
        pattern = rf"(public static void {method}\([^)]*\)\s*\{{\s*try\s*\{{)"
        replacement = r"\1\n            addScreenTranslateButton(viewGroup);"
        text, count = re.subn(pattern, replacement, text, count=1, flags=re.DOTALL)
        if count != 1:
            raise ValueError(f"Instagram ActionBarPatch method changed upstream: {method}")

    path.write_text(text, encoding="utf-8")


def checkout_requested_piko_commit(piko_directory: Path) -> None:
    """Pin the build to the exact Piko commit from the official piko-newx release."""
    requested = os.environ.get("PIKO_COMMIT", "").strip()
    if not requested:
        return
    subprocess.run(
        ["git", "fetch", "--depth", "1", "origin", requested],
        cwd=piko_directory,
        check=True,
    )
    subprocess.run(
        ["git", "checkout", "--detach", requested],
        cwd=piko_directory,
        check=True,
    )


def pre_build_cleanup(piko_directory: Path) -> None:
    """Remove legacy Twitter patches/extensions while keeping NewX and Instagram."""
    # Remove the legacy Twitter patch source package; keep Instagram and NewX.
    for path in [
        piko_directory / "patches/src/main/kotlin/app/crimera/patches/twitter",
        piko_directory / "patches/src/main/kotlin/app/revanced",
    ]:
        if path.exists():
            shutil.rmtree(path)

    # Remove the legacy Twitter extension module; keep Instagram.
    for path in [
        piko_directory / "extensions/twitter",
    ]:
        if path.exists():
            shutil.rmtree(path)

    # Keep twitter/bringbacktwitter assets for Bring back twitter patch
    twitter_res = piko_directory / "patches/src/main/resources/twitter"
    if twitter_res.exists():
        for item in twitter_res.iterdir():
            if item.name != "bringbacktwitter":
                if item.is_dir():
                    shutil.rmtree(item)
                else:
                    item.unlink()

    addresources_dir = piko_directory / "patches/src/main/resources/addresources"
    if addresources_dir.exists():
        for res_name in ("twitter",):
            for matched in addresources_dir.glob(f"*/{res_name}"):
                if matched.is_dir():
                    shutil.rmtree(matched)

    # The current x-lite Instagram userdata fingerprint has one unused import
    # from the legacy Twitter patch tree. Remove it when building Instagram
    # without the legacy Twitter sources.
    userdata_fingerprint = (
        piko_directory
        / "patches/src/main/kotlin/app/crimera/patches/instagram/entity/userdata/Fingerprint.kt"
    )
    if userdata_fingerprint.exists():
        contents = userdata_fingerprint.read_text(encoding="utf-8")
        contents = contents.replace(
            "import app.crimera.patches.twitter.logging.responseLogging.JACKSON_CLASS\n",
            "",
        )
        userdata_fingerprint.write_text(contents, encoding="utf-8")


def set_project_version(piko_directory: Path, version: str) -> None:
    """Set the version only in the temporary Piko checkout used for a build."""
    properties_path = piko_directory / "gradle.properties"
    lines = properties_path.read_text(encoding="utf-8").splitlines(keepends=True)
    version_indexes = [
        index
        for index, line in enumerate(lines)
        if re.match(r"^\s*version\s*=", line)
    ]
    if len(version_indexes) != 1:
        raise ValueError("Piko gradle.properties must contain exactly one version")

    lines[version_indexes[0]] = f"version = {version}\n"
    properties_path.write_text("".join(lines), encoding="utf-8")


def build_piko_patches(
    output: str = "bins/patches.mpp", patch_version: str | None = None
) -> PikoBuild:
    output_path = Path(output)
    output_path.parent.mkdir(parents=True, exist_ok=True)

    if patch_version is not None and patch_version.startswith("v"):
        raise ValueError("patch_version must not include the leading v")

    with tempfile.TemporaryDirectory(prefix="piko-") as temporary_directory:
        piko_directory = Path(temporary_directory) / "piko"

        subprocess.run(
            [
                "git",
                "clone",
                "--depth",
                "1",
                "--branch",
                PIKO_BRANCH,
                PIKO_REPOSITORY,
                str(piko_directory),
            ],
            check=True,
        )
        checkout_requested_piko_commit(piko_directory)

        supported_versions = get_supported_versions(
            (piko_directory / XLITE_CONSTANTS).read_text()
        )

        pre_build_cleanup(piko_directory)
        apply_source_overlays(piko_directory)
        install_instagram_screen_translate_button(piko_directory)
        apply_zh_cn(piko_directory)

        if patch_version is not None:
            set_project_version(piko_directory, patch_version)

        subprocess.run(
            ["./gradlew", "clean", "buildAndroid"],
            cwd=piko_directory,
            env=os.environ.copy(),
            check=True,
        )

        artifacts_directory = piko_directory / "patches" / "build" / "libs"
        if patch_version is not None:
            artifact = artifacts_directory / f"patches-{patch_version}.mpp"
            if not artifact.is_file():
                raise FileNotFoundError(
                    f"Piko did not produce the expected artifact {artifact.name}"
                )
        else:
            artifacts = sorted(artifacts_directory.glob("patches-*.mpp"))
            if not artifacts:
                raise FileNotFoundError("Piko did not produce a patches .mpp artifact")
            artifact = artifacts[-1]

        shutil.copy2(artifact, output_path)

        commit = subprocess.run(
            ["git", "rev-parse", "HEAD"],
            cwd=piko_directory,
            check=True,
            capture_output=True,
            text=True,
        )

    return PikoBuild(commit=commit.stdout.strip(), supported_versions=supported_versions)
