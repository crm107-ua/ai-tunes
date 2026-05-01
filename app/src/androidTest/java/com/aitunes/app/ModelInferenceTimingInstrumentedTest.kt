package com.aitunes.app

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aitunes.app.data.models.ModelRegistry
import com.aitunes.app.domain.model.SectorId
import com.aitunes.app.engine.InferenceEvent
import com.aitunes.app.engine.LlamaNativeBridge
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

/**
 * Ejecutar en dispositivo/emulador con modelos ya descargados en la biblioteca.
 * Salida: Logcat tag **AITUNES_MODEL_BENCH** con tiempos por modelo (carga, 1er token, total).
 *
 * Orden: GGUF más pequeños primero. Tope global (~12 min) para que Gradle no pare "colgado" al 98%.
 */
@RunWith(AndroidJUnit4::class)
class ModelInferenceTimingInstrumentedTest {

    @Test
    fun benchmarkEachDownloadedModel() {
        runBlocking {
            Log.i(
                TAG,
                "Bench iniciado: Gradle suele quedar en EXECUTING ~98% sin mas texto en consola. " +
                    "Progreso solo en logcat (tag $TAG). En emulador, carga/infer por modelo pueden tardar varios minutos."
            )
            val app = InstrumentationRegistry.getInstrumentation()
                .targetContext.applicationContext as AiTunesApplication
            val engine = app.llamaEngine
            val mm = app.modelManager

            val benchStart = SystemClock.elapsedRealtime()
            val ordered = ModelRegistry.allModels.sortedBy { it.approximateSizeBytes }

            for (model in ordered) {
                if (SystemClock.elapsedRealtime() - benchStart > BENCH_BUDGET_MS) {
                    Log.w(TAG, "Presupuesto global ${BENCH_BUDGET_MS}ms agotado; deteniendo bench.")
                    break
                }
                val debugFile = mm.modelFile(model)
                Log.i(
                    TAG,
                    "CHECK ${model.id} path=${debugFile.absolutePath} exists=${debugFile.exists()} len=${debugFile.length()}"
                )
                if (!mm.isModelDownloaded(model.id)) {
                    Log.i(TAG, "OMITIDO ${model.id} (no descargado)")
                    continue
                }

                val path = mm.modelFile(model).absolutePath

                val loadMs = measureTimeMillis {
                    engine.prepareNativeSession(path)
                }
                val ready = LlamaNativeBridge.isModelReady()
                Log.i(TAG, "MODELO=${model.id} loadMs=$loadMs ready=$ready jni=${LlamaNativeBridge.isJniLibraryLoaded()}")

                if (!ready) {
                    Log.w(TAG, "Sin sesión nativa; infer usará fallback Kotlin.")
                }

                var firstTokenMs = -1L
                val genMs = measureTimeMillis {
                    val t0 = SystemClock.elapsedRealtime()
                    engine.infer("Hola, responde en una frase.", SectorId.GENERAL, path).collect { ev ->
                        if (ev is InferenceEvent.TokenChunk && firstTokenMs < 0) {
                            firstTokenMs = SystemClock.elapsedRealtime() - t0
                        }
                    }
                }

                Log.i(
                    TAG,
                    "MODELO=${model.id} inferTotalMs=$genMs firstTokenMs=$firstTokenMs " +
                        "(primer token desde inicio de infer, GGUF grandes pueden tardar varios minutos en emulador)"
                )

                engine.releaseNativeSession()
            }
            Log.i(TAG, "Bench terminado en ${SystemClock.elapsedRealtime() - benchStart}ms")
        }
    }

    companion object {
        private const val TAG = "AITUNES_MODEL_BENCH"
        /** Evita que connectedAndroidTest quede en EXECUTING >10–15 min sin logs nuevos. */
        private const val BENCH_BUDGET_MS = 12L * 60L * 1000L
    }
}
