package my.github.MrxSiN.pixellauncherevolved.labels

import my.github.MrxSiN.pixellauncherevolved.feature.backup.PortableAppLabel
import my.github.MrxSiN.pixellauncherevolved.feature.backup.SettingsBackupFormat
import my.github.MrxSiN.pixellauncherevolved.feature.backup.SettingsSnapshot
import my.github.MrxSiN.pixellauncherevolved.icons.FakePreferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLabelFormatTest {

    private val video = AppLabel("com.google.android.youtube", "com.google.android.youtube.HomeActivity", 0, "Video")
    private val workVideo = video.copy(userId = 10, name = "Work video", hideHome = true)

    @Test
    fun `labels survive being stored and read back, any character included`() {
        val odd = AppLabel("com.example", "com.example.Main", 0, "a;b=c|d/\"e\" 🎬 مرحبا", hideDrawer = true)
        val labels = listOf(workVideo, video, odd)

        val read = AppLabelFormat.read(AppLabelFormat.write(labels))

        assertEquals(labels.sortedWith(compareBy({ it.userId }, { it.packageName })), read)
    }

    @Test
    fun `the same app in two profiles keeps two labels`() {
        val compiled = AppLabelFormat.compile(listOf(video, workVideo))

        assertEquals("Video", compiled.name(compiled.find(video.packageName, video.className, 0)))
        val work = compiled.find(video.packageName, video.className, 10)
        assertEquals("Work video", compiled.name(work))
        assertEquals(AppLabels.HIDE_HOME, compiled.flags(work))
        assertEquals(-1, compiled.find(video.packageName, video.className, 11))
        assertEquals(-1, compiled.find(video.packageName, "com.google.android.youtube.Other", 0))
    }

    @Test
    fun `an entry that changes nothing is not kept`() {
        val plain = video.copy(name = null)

        assertFalse(plain.isCustom)
        assertTrue(AppLabelFormat.compile(listOf(plain)).isEmpty)
        assertEquals(emptyList<AppLabel>(), AppLabelFormat.read(AppLabelFormat.write(listOf(plain))))
    }

    @Test
    fun `visibility alone needs no name, and no name means no sorting work`() {
        val compiled = AppLabelFormat.compile(listOf(video.copy(name = null, hideDrawer = true)))

        assertFalse(compiled.isEmpty)
        assertFalse(compiled.hasNames())
        assertNull(compiled.name(0))
    }

    @Test
    fun `a typed name keeps its characters, loses only what would break the icon, and is bounded`() {
        assertNull(AppLabelFormat.normalize("   "))
        assertNull(AppLabelFormat.normalize(null))
        assertEquals("Photos 📷", AppLabelFormat.normalize("  Photos 📷 "))
        assertEquals("two lines", AppLabelFormat.normalize("two\nlines"))

        val long = "👍".repeat(AppLabelFormat.MAX_CODE_POINTS + 10)
        val cut = AppLabelFormat.normalize(long)!!
        assertEquals(AppLabelFormat.MAX_CODE_POINTS, cut.codePointCount(0, cut.length))
    }

    @Test
    fun `text that is not this format, or a newer one, reads as nothing`() {
        assertEquals(emptyList<AppLabel>(), AppLabelFormat.read("not json"))
        assertEquals(emptyList<AppLabel>(), AppLabelFormat.read("""{"version":99,"apps":[]}"""))
        assertEquals(emptyList<AppLabel>(), AppLabelFormat.read(""))
    }

    @Test
    fun `the store publishes what it stores and recompiles only on change`() {
        val store = AppLabelStore(FakePreferences())
        store.put(video)

        val first = AppLabels.current()
        assertEquals(1, first.size())
        assertSame(first, store.publish())

        store.put(video.copy(name = null))
        assertTrue(AppLabels.current().isEmpty)
        assertTrue(store.labels().isEmpty())
    }

    @Test
    fun `a backup names the profile by kind, and an older backup has no labels`() {
        val snapshot = SettingsSnapshot.DEFAULTS.copy(
            appLabels = listOf(PortableAppLabel("personal", video), PortableAppLabel("work", workVideo.copy(userId = 0))),
        )

        val read = SettingsBackupFormat.read(SettingsBackupFormat.write(snapshot))

        assertEquals(snapshot.appLabels.sortedBy { it.profile }, read.appLabels)
        assertFalse(SettingsBackupFormat.write(snapshot).contains("\"user\": 10"))
        assertEquals(emptyList<PortableAppLabel>(), SettingsBackupFormat.read("""{"version":1}""").appLabels)
    }
}
