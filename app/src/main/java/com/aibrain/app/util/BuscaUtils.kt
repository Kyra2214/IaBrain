package com.aibrain.app.util

import java.text.Normalizer

/**
 * Fase 12.9 — normaliza um texto para busca: remove acentos e converte para
 * minúsculas, permitindo que "artistica" encontre "artísticas" e assim por
 * diante. Usado pela pesquisa do MainViewModel (Fase 12.4).
 */
fun String.normalizarBusca(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "")
        .lowercase()
