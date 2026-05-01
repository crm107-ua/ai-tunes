package com.aitunes.app.data.models

import android.app.DownloadManager

/**
 * Unifica el cursor del [DownloadManager], el tamaño del archivo en disco y el tamaño esperado
 * del catálogo para que la UI no se quede en "0 %" mientras el sistema está en cola o conectando.
 */
internal object DownloadProgressCalculator {

    fun compute(
        model: RegisteredModel,
        dmSoFarRaw: Long,
        dmTotalRaw: Long,
        fileLenRaw: Long,
        status: Int?,
    ): ModelDownloadProgress {
        val dmSoFar = dmSoFarRaw.coerceAtLeast(0L)
        val fileLen = fileLenRaw.coerceAtLeast(0L)
        val mergedSoFar = maxOf(dmSoFar, fileLen)

        val approx = model.approximateSizeBytes.coerceAtLeast(1L)
        val dmTotal = dmTotalRaw.coerceAtLeast(-1L)
        val totalForBar = when {
            dmTotal > 0L -> dmTotal
            else -> approx
        }

        val st = status
        if (st == null) {
            return ModelDownloadProgress(
                fraction = INDETERMINATE_PROGRESS,
                bytesDownloaded = mergedSoFar,
                bytesTotal = totalForBar,
                bytesOnDisk = fileLen,
                statusHint = null
            )
        }

        val queued = st == DownloadManager.STATUS_PENDING ||
            st == DownloadManager.STATUS_PAUSED
        val connecting = st == DownloadManager.STATUS_RUNNING &&
            mergedSoFar == 0L && dmSoFar == 0L && fileLen == 0L

        val fraction = when {
            queued -> INDETERMINATE_PROGRESS
            connecting -> INDETERMINATE_PROGRESS
            else -> (mergedSoFar.toFloat() / totalForBar.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
        }

        val hint = when {
            st == DownloadManager.STATUS_PENDING -> "En cola del sistema…"
            st == DownloadManager.STATUS_PAUSED -> "Descarga en pausa…"
            connecting -> "Conectando con el servidor…"
            else -> null
        }

        return ModelDownloadProgress(
            fraction = fraction,
            bytesDownloaded = mergedSoFar,
            bytesTotal = if (dmTotal > 0L) dmTotal else totalForBar,
            bytesOnDisk = fileLen,
            statusHint = hint
        )
    }
}
