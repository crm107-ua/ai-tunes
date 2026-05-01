package com.aitunes.app.data.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadProgressCalculatorTest {

    /** Valores alineados con [android.app.DownloadManager] (sin depender de Robolectric en test JVM). */
    private companion object {
        const val STATUS_PENDING = 1
        const val STATUS_RUNNING = 2
    }

    private val tiny = RegisteredModel(
        id = "x",
        displayName = "Tiny",
        description = "",
        huggingFaceDownloadUrl = "https://example.com/f.gguf",
        fileName = "f.gguf",
        quantLabel = "Q4",
        approximateSizeBytes = 1000L,
        recommendedTiers = emptySet()
    )

    @Test
    fun pending_is_indeterminate_not_zero_percent() {
        val p = DownloadProgressCalculator.compute(
            model = tiny,
            dmSoFarRaw = 0L,
            dmTotalRaw = 1000L,
            fileLenRaw = 0L,
            status = STATUS_PENDING
        )
        assertTrue(p.isIndeterminate)
        assertEquals("En cola del sistema…", p.statusHint)
    }

    @Test
    fun running_no_bytes_yet_is_indeterminate_not_zero_percent() {
        val p = DownloadProgressCalculator.compute(
            model = tiny,
            dmSoFarRaw = 0L,
            dmTotalRaw = 1000L,
            fileLenRaw = 0L,
            status = STATUS_RUNNING
        )
        assertTrue(p.isIndeterminate)
        assertEquals("Conectando con el servidor…", p.statusHint)
    }

    @Test
    fun running_with_bytes_shows_determinate_fraction() {
        val p = DownloadProgressCalculator.compute(
            model = tiny,
            dmSoFarRaw = 250L,
            dmTotalRaw = 1000L,
            fileLenRaw = 0L,
            status = STATUS_RUNNING
        )
        assertEquals(0.25f, p.fraction, 0.001f)
        assertEquals(250L, p.bytesDownloaded)
    }

    @Test
    fun disk_ahead_of_dm_counts_for_merged_progress() {
        val p = DownloadProgressCalculator.compute(
            model = tiny,
            dmSoFarRaw = 0L,
            dmTotalRaw = 1000L,
            fileLenRaw = 300L,
            status = STATUS_RUNNING
        )
        assertEquals(0.3f, p.fraction, 0.001f)
        assertEquals(300L, p.bytesDownloaded)
        assertEquals(300L, p.bytesOnDisk)
    }

    @Test
    fun negative_dm_total_uses_approx_catalog_size() {
        val p = DownloadProgressCalculator.compute(
            model = tiny,
            dmSoFarRaw = 500L,
            dmTotalRaw = -1L,
            fileLenRaw = 0L,
            status = STATUS_RUNNING
        )
        assertEquals(0.5f, p.fraction, 0.001f)
        assertEquals(1000L, p.bytesTotal)
    }
}
