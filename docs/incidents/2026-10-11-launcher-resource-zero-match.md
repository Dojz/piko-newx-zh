# Launcher resource transformer: zero direct Activity matches

The activity-only resolver raises `NewX launcher activity: expected one, got 0` when
MAIN/LAUNCHER is carried by an activity-alias, rather than the target Activity.
The same unsupported shape exists after this patch moves the launch filter to its
owned aliases; repeating the original resource step also raises the zero-match error.
A lexical `android:name` lookup additionally cannot resolve an XML
document using a different prefix for the Android namespace.

The replacement resolves a single real target Activity from MAIN/LAUNCHER Activity
and activity-alias entries. It reads Android attributes by their namespace or inherited xmlns declaration, preserves
launcher icons and shortcuts from the active entry, and disables the replaced aliases.
A complete owned layout is validated and reused without adding duplicate components
or splash styles. Multiple distinct real targets and incomplete owned layouts remain
errors. No version-specific native Activity class is hardcoded.

`LauncherManifestTest` exercises a real-target manifest topology with disabled premium
aliases, repeated configuration, an alias-only launcher with a relative target, an
alternate namespace prefix in aware and unaware DOMs and a serialized round trip, plus ambiguous and partial
negative layouts. These cases use the production transformer. Native constructor
emission and caption downloading are unchanged. The exact intermediate manifest from
the reported failure is unavailable, so the route producing the unsupported shape
has not been established. No phone runtime result is claimed.
