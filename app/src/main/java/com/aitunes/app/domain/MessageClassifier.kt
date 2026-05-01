package com.aitunes.app.domain

import com.aitunes.app.domain.model.MessageCategory
import java.util.Locale

/**
 * Clasificador ligero en tiempo real basado en palabras clave (sin red).
 */
class MessageClassifier {

    private val salud = setOf(
        "salud", "médico", "medico", "doctor", "hospital", "alergia", "alergias",
        "medicina", "síntoma", "sintoma", "dolor", "vacuna", "cita médica", "cita medica",
        "enfermedad", "tratamiento", "pastilla", "medicamento", "psicólogo", "psicologo"
    )
    private val finanzas = setOf(
        "dinero", "euro", "dólar", "dolar", "banco", "hipoteca", "préstamo", "prestamo",
        "inversión", "inversion", "acciones", "bolsa", "impuesto", "nómina", "nomina",
        "ahorro", "deuda", "tarjeta", "presupuesto", "factura", "pago"
    )
    private val trabajo = setOf(
        "trabajo", "oficina", "reunión", "reunion", "jefe", "cliente", "proyecto",
        "deadline", "entrega", "empresa", "contrato", "currículum", "curriculum",
        "entrevista", "equipo", "slack", "correo laboral"
    )

    fun classify(text: String): MessageCategory {
        val normalized = normalize(text)
        if (normalized.isBlank()) return MessageCategory.GENERAL

        val tokens = tokenize(normalized)
        var scoreSalud = 0
        var scoreFinanzas = 0
        var scoreTrabajo = 0

        for (t in tokens) {
            if (salud.any { k -> t.contains(k) || k.contains(t) }) scoreSalud++
            if (finanzas.any { k -> t.contains(k) || k.contains(t) }) scoreFinanzas++
            if (trabajo.any { k -> t.contains(k) || k.contains(t) }) scoreTrabajo++
        }

        // Frases multi-palabra
        if (salud.any { normalized.contains(it) }) scoreSalud += 2
        if (finanzas.any { normalized.contains(it) }) scoreFinanzas += 2
        if (trabajo.any { normalized.contains(it) }) scoreTrabajo += 2

        val max = maxOf(scoreSalud, scoreFinanzas, scoreTrabajo)
        if (max == 0) return MessageCategory.GENERAL

        return when (max) {
            scoreSalud -> MessageCategory.SALUD
            scoreFinanzas -> MessageCategory.FINANZAS
            scoreTrabajo -> MessageCategory.TRABAJO
            else -> MessageCategory.GENERAL
        }
    }

    private fun normalize(s: String): String =
        s.lowercase(Locale.getDefault()).trim()

    private fun tokenize(normalized: String): List<String> =
        normalized.split(Regex("\\s+")).filter { it.isNotBlank() }
}
