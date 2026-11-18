package it.pingpro.app

import it.pingpro.app.data.UpdateInfo
import it.pingpro.app.data.UpdateState
import it.pingpro.app.ui.screens.updateDialogVisible
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UpdaterTest {
    private val json = """{"versionCode":9,"versionName":"2.1.1","apkUrl":"https://raw.githubusercontent.com/x/y/HEAD/release/PingPro-2.1.1.apk",
        "sha256":"ABCDEF","sizeBytes":1400000,"notes":"Correzioni"}"""

    @Test fun parsesLatestJson() {
        val i = UpdateInfo.parse(json)
        assertEquals(9, i.versionCode); assertEquals("2.1.1", i.versionName); assertEquals("abcdef", i.sha256)
        assertEquals("Correzioni", i.notes)
    }

    @Test fun dialogVisibility() {
        val i = UpdateInfo.parse(json)
        assertTrue(updateDialogVisible(UpdateState.Available(i), 0))
        assertFalse(updateDialogVisible(UpdateState.Available(i), 9)) // "Più tardi" per questa versione
        assertTrue(updateDialogVisible(UpdateState.Downloading(i, 0.3f), 9))
        assertTrue(updateDialogVisible(UpdateState.Ready(i, File("x")), 9))
        assertFalse(updateDialogVisible(UpdateState.UpToDate(0), 0))
        assertFalse(updateDialogVisible(UpdateState.Failed("no internet"), 0)) // controllo automatico fallito: nessun popup
    }

    /** Il file release/latest.json del repository deve corrispondere a questa build. */
    @Test fun publishedLatestJsonMatchesBuild() {
        val f = listOf(File("../release/latest.json"), File("release/latest.json")).firstOrNull { it.exists() } ?: return
        val i = UpdateInfo.parse(f.readText())
        assertEquals(BuildConfig.VERSION_CODE, i.versionCode)
        assertEquals(BuildConfig.VERSION_NAME, i.versionName)
        assertTrue(i.apkUrl.endsWith("/release/PingPro-${i.versionName}.apk"))
    }
}
