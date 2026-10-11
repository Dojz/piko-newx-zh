package app.crimera.patches.newx.misc.appicon

import app.morphe.patcher.patch.PatchException
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/** Guards the reported Android 11 `NewX launcher activity: expected one, got 0` failure. */
class LauncherManifestTest {
    private val namespace = "http://schemas.android.com/apk/res/android"
    private val mainFilter = """<intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent-filter>"""
    private fun manifest(body: String, aware: Boolean = false): Document =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = aware }.newDocumentBuilder().parse(
            ByteArrayInputStream("""<manifest xmlns:android="$namespace" package="com.twitter.android"><application android:icon="@mipmap/ic_launcher">$body</application></manifest>""".toByteArray()))
    private fun component(doc: Document, name: String): Element =
        (0 until doc.getElementsByTagName("application").item(0).childNodes.length)
            .mapNotNull { doc.getElementsByTagName("application").item(0).childNodes.item(it) as? Element }
            .single { it.getAttributeNS(namespace, "name").ifEmpty { it.getAttribute("android:name") } == name }
    private fun serialized(doc: Document): String = StringWriter().also { out ->
        TransformerFactory.newInstance().newTransformer().transform(DOMSource(doc), StreamResult(out))
    }.toString()

    @Test
    fun `native 12_28 activity and disabled premium aliases are converted exactly once`() {
        for (aware in listOf(false, true)) {
            val doc = manifest("""
                <activity android:name="com.x.android.main.MainActivity" android:theme="@style/Theme.X.SplashScreen" android:exported="true">
                    $mainFilter
                    <intent-filter><action android:name="android.intent.action.VIEW"/></intent-filter>
                    <meta-data android:name="android.app.shortcuts" android:resource="@xml/shortcuts"/>
                </activity>
                <activity-alias android:name="com.x.appicon.icon2" android:enabled="false" android:targetActivity="com.x.android.main.MainActivity">$mainFilter</activity-alias>
            """, aware)
            val first = configureLauncherManifest(doc)
            assertEquals("Lcom/x/android/main/MainActivity;", first.descriptor)
            assertEquals("@style/Theme.X.SplashScreen", first.theme)
            val before = serialized(doc)
            assertFalse(configureLauncherManifest(doc).added)
            assertEquals(before, serialized(doc))
            val native = component(doc, "com.x.android.main.MainActivity")
            assertEquals(1, native.getElementsByTagName("intent-filter").length)
            assertEquals(1, native.getElementsByTagName("meta-data").length)
            assertEquals(2, doc.getElementsByTagName("activity").length)
            assertEquals(3, doc.getElementsByTagName("activity-alias").length)
            // Check that mixed namespace-aware writes also serialize to valid XML.
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(before.toByteArray()))
        }
    }

    @Test
    fun `alias-only launcher resolves relative target and preserves its icon and shortcuts`() {
        val doc = manifest("""
            <activity android:name=".Native" android:theme="@style/NativeSplash"/>
            <activity-alias android:name=".OriginalIcon" android:targetActivity=".Native" android:icon="@mipmap/custom">
                $mainFilter<meta-data android:name="android.app.shortcuts" android:resource="@xml/shortcuts"/>
            </activity-alias>
        """)
        assertEquals("Lcom/twitter/android/Native;", configureLauncherManifest(doc).descriptor)
        val original = component(doc, "app.morphe.extension.newx.launcher.Default")
        assertEquals("@mipmap/custom", original.getAttributeNS(namespace, "icon"))
        assertEquals(1, original.getElementsByTagName("meta-data").length)
        assertEquals("false", component(doc, ".OriginalIcon").getAttributeNS(namespace, "enabled"))
        assertFalse(configureLauncherManifest(doc).added)
    }

    @Test
    fun `namespace prefix changes resolve without an android lexical prefix`() {
        val xml = """<manifest xmlns:x="$namespace" package="com.twitter.android"><application><activity x:name="Native" x:theme="@style/Splash">${mainFilter.replace("android:", "x:")}</activity></application></manifest>"""
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray()))
        assertEquals("Lcom/twitter/android/Native;", configureLauncherManifest(doc).descriptor)
        val roundTrip = factory.newDocumentBuilder().parse(ByteArrayInputStream(serialized(doc).toByteArray()))
        assertFalse(configureLauncherManifest(roundTrip).added)
    }

    @Test
    fun `distinct launcher targets and partially converted layouts fail closed`() {
        assertFailsWith<PatchException> { configureLauncherManifest(manifest("""
            <activity android:name=".One" android:theme="@style/Splash">$mainFilter</activity>
            <activity android:name=".Two" android:theme="@style/Splash">$mainFilter</activity>
        """)) }
        assertFailsWith<PatchException> { configureLauncherManifest(manifest("""
            <activity android:name=".Native" android:theme="@style/Splash"/>
            <activity-alias android:name="app.morphe.extension.newx.launcher.Default" android:targetActivity=".Native">$mainFilter</activity-alias>
        """)) }
    }
}
