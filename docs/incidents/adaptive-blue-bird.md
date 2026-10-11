# Blue-bird launcher mask regression

The Android 11 realme launcher placed the blue-bird icon inside a white plate,
while the chooser displayed a nearly square, inset bitmap beside the native X
icon. The launcher alias referenced only a legacy xxhdpi bitmap with transparent
outer margins. The native X icon uses adaptive foreground/background layers;
legacy icon normalization therefore gave the two choices different geometry.

The icon patch now supplies adaptive resources for API 26+ with an opaque Twitter
blue background and the existing upstream white-bird foreground in all five
densities. Android applies the same device icon mask used by the native icon.
The chooser already loads each alias through PackageManager, so it previews the
same adaptive drawable without another manually rounded image. The API 33 variant
supplies the bird foreground as its monochrome silhouette. Legacy bitmap fallback
and the existing launcher alias / splash switching remain available.

Both retained and current implementations must be rebuilt with this overlay.
The newly retained content-addressed legacy bundle is used by subsequent regular
unified releases, while current builds apply the same maintained overlay.

`tests/check_launcher_icon_apk.py` inspects the final compiled APK: alias icon and
roundIcon agree, adaptive API variants exist, the background fills the mask in
blue, and the foreground has no embedded background or edge artwork. It rejects
the previous APK with missing adaptive launcher variants. Final device rendering
still requires user verification; build/resource checks cannot emulate realme's
launcher cache or custom icon settings.

The user confirmed that `{text}` names downloads using the currently displayed
language. This fix changes launcher resources only.
