# Blue-bird launcher mask regression

The Android 11 realme launcher placed the blue-bird icon inside a white plate,
while the chooser displayed a nearly square, inset bitmap beside the native X
icon. The launcher alias referenced only a legacy xxhdpi bitmap with transparent
outer margins. The native X icon uses adaptive foreground/background layers;
legacy icon normalization therefore gave the two choices different geometry.

The icon patch now supplies adaptive resources for API 26+ with an opaque Twitter
blue background and a white-bird VectorDrawable converted from the official SVG. Android applies the same device icon mask used by the native icon.
The chooser already loads each alias through PackageManager, so it previews the
same adaptive drawable without another manually rounded image. The API 33 variant
supplies the bird foreground as its monochrome silhouette. Legacy bitmap fallback
and the existing launcher alias / splash switching remain available.

Both retained and current implementations must be rebuilt with this overlay.
The newly retained content-addressed legacy bundle is used by subsequent regular
unified releases, while current builds apply the same maintained overlay.

`tests/check_launcher_icon_apk.py` inspects the final compiled APK: alias icon and
roundIcon agree, adaptive API variants exist, the background fills the mask in
blue, and the vector keeps the exact official SVG path and uniform proportions without
an embedded background. Chinese labels are 经典 and 经典图标. It rejects
the previous APK with missing adaptive launcher variants. Final device rendering
still requires user verification; build/resource checks cannot emulate realme's
launcher cache or custom icon settings.

The user confirmed that `{text}` names downloads using the currently displayed
language. This fix changes launcher resources only.

Official vector source: https://github.com/twitter/opensource-website/blob/2887311216d99cd0e38b16e564bcdbea3797eb63/static/assets/twitter-logo.svg

The original SVG is retained in `docs/assets/twitter-logo.svg`; Android vector
resources keep its path unchanged and apply only a uniform scale/translation.
The foreground uses a 108dp adaptive canvas with a 44dp-wide logo, centered in the
safe area. The startup drawable reuses the same path on its existing 288-unit
canvas. Copyright Twitter; Apache-2.0 source license retained beside the SVG.
