package com.aibrain.app.cache

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Fase 10.1 — Cache de logos das IAs (memória + disco).
 * Fase 10.2 — Compressão/otimização: decodifica com amostragem (inSampleSize)
 * no tamanho de exibição real ([tamanhoDp]), evitando bitmaps maiores que o
 * necessário na memória, e grava o cache em disco já compactado (WebP com
 * perdas) em vez de PNG bruto.
 *
 * Sem bibliotecas externas (Glide/Coil), seguindo o mesmo princípio "leve"
 * já usado em CatalogoRepository/AtualizacaoRepository (Fases 2.3 e 8).
 *
 * O campo `logo` de [com.aibrain.app.model.IA] aceita URL (http/https) ou
 * nome de recurso local (drawable/asset). Falha ao resolver não é erro:
 * retorna null e a UI mantém o ícone placeholder já definido nos layouts.
 */
class ImagemCache(context: Context) {

    private val contexto = context.applicationContext
    private val diretorioDisco = File(contexto.cacheDir, PASTA_DISCO).apply { mkdirs() }
    private val densidade = contexto.resources.displayMetrics.density

    private val cacheMemoria = object : LruCache<String, Bitmap>(TAMANHO_MEMORIA_KB) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }

    companion object {
        private const val PASTA_DISCO = "logos_ia"
        private const val TAMANHO_MEMORIA_KB = 8 * 1024 // 8MB
        private const val TIMEOUT_MS = 8000
        private const val QUALIDADE_DISCO = 85

        /** Fase 19.9 — nº de downloads simultâneos durante o prefetch em lote. */
        private const val MAX_PARALELO_PREFETCH = 4

        /**
         * Fase 14.4 — por quanto tempo um "miss" (404, domínio sem favicon, timeout)
         * fica marcado em disco antes de a URL ser tentada de novo. Evita repetir
         * a mesma requisição de rede fadada a falhar a cada tela aberta, mas ainda
         * permite que o logo apareça depois, caso o domínio passe a ter um.
         *
         * Fase 19.8 — a Clearbit Logo API (antiga fonte dos logos remotos, Fase 13.5)
         * foi desativada definitivamente em 08/12/2025 (https://logo.clearbit.com
         * deixou de resolver, derrubando os 39 logos do catálogo). O campo `logo`
         * do `ia_catalogo.json` passou a apontar para o Google Favicon Service
         * (`https://www.google.com/s2/favicons?domain=...&sz=128`), gratuito,
         * sem chave de API — mesmo formato de URL http(s) já suportado aqui.
         */
        private const val TTL_FALHA_MS = 7L * 24 * 60 * 60 * 1000L // 7 dias

        /** Tamanhos padrão de exibição do logo (dp), usados pelas telas do app. */
        const val TAMANHO_ITEM_DP = 48
        const val TAMANHO_DETALHE_DP = 72
    }

    /**
     * Memória → disco (só URLs) → rede (só URLs) → recurso local.
     * [tamanhoDp] é o tamanho de exibição do logo — define a resolução máxima
     * decodificada (Fase 10.2). Null se indisponível.
     */
    suspend fun carregar(logo: String, tamanhoDp: Int = TAMANHO_ITEM_DP): Bitmap? = withContext(Dispatchers.IO) {
        if (logo.isBlank()) return@withContext null
        val tamanhoPx = (tamanhoDp * densidade).toInt().coerceAtLeast(1)
        val chaveMemoria = "$logo@$tamanhoPx"
        cacheMemoria.get(chaveMemoria)?.let { return@withContext it }

        val bitmap = if (logo.startsWith("http://") || logo.startsWith("https://")) {
            carregarPorUrl(logo, tamanhoPx)
        } else {
            carregarLocal(logo, tamanhoPx)
        } ?: return@withContext null

        cacheMemoria.put(chaveMemoria, bitmap)
        bitmap
    }

    /**
     * Fase 19.9 — download em lote dos logos remotos (usada no primeiro uso do app,
     * já que os ícones não são embutidos no APK, Fase 19.8). Grava direto no cache
     * de disco (Fase 10.1) reaproveitando [carregarPorUrl] — chamadas futuras a
     * [carregar] para as mesmas URLs batem no disco, sem nova requisição de rede.
     * Concorrência limitada ([MAX_PARALELO_PREFETCH]) para não sobrecarregar a
     * conexão do usuário; falha em uma URL não interrompe as demais (cada uma já
     * fica marcada como "miss" pelo mecanismo da Fase 14.4).
     */
    suspend fun prefetchTodos(urls: List<String>, tamanhoDp: Int = TAMANHO_ITEM_DP) = withContext(Dispatchers.IO) {
        val tamanhoPx = (tamanhoDp * densidade).toInt().coerceAtLeast(1)
        val semaforo = Semaphore(MAX_PARALELO_PREFETCH)
        coroutineScope {
            urls.filter { it.startsWith("http://") || it.startsWith("https://") }
                .distinct()
                .map { url ->
                    async {
                        semaforo.withPermit {
                            try {
                                carregarPorUrl(url, tamanhoPx)
                            } catch (e: Exception) {
                                null
                            }
                        }
                    }
                }
                .forEach { it.await() }
        }
    }

    // ---- Remoto (URL) — memória + disco ----

    private fun carregarPorUrl(url: String, tamanhoPx: Int): Bitmap? {
        val arquivo = arquivoDisco(url, tamanhoPx)
        if (arquivo.exists()) {
            BitmapFactory.decodeFile(arquivo.absolutePath)?.let { return it }
        }

        // Fase 14.4 — já sabemos (marcador em disco, ainda válido) que essa URL
        // não resolve; não repete a requisição de rede a cada tela aberta.
        val arquivoFalha = arquivoFalha(url, tamanhoPx)
        if (arquivoFalha.exists() && !falhaExpirada(arquivoFalha)) {
            return null
        }

        val bytes = try {
            baixarBytes(url)
        } catch (e: Exception) {
            null
        }
        if (bytes == null) {
            registrarFalha(arquivoFalha)
            return null
        }

        val bitmap = decodificarBytesComAmostra(bytes, tamanhoPx)
        if (bitmap == null) {
            registrarFalha(arquivoFalha)
            return null
        }

        try {
            arquivo.outputStream().use { bitmap.compress(formatoCompressaoDisco(), QUALIDADE_DISCO, it) }
            arquivoFalha.delete() // sucesso após falha anterior — marcador não é mais válido
        } catch (e: Exception) {
            // Falha ao gravar em disco não impede o uso do bitmap já baixado.
        }
        return bitmap
    }

    /** Fase 14.4 — grava o instante da falha; se não conseguir, a próxima tela só tenta de novo. */
    private fun registrarFalha(arquivoFalha: File) {
        try {
            arquivoFalha.writeText(System.currentTimeMillis().toString())
        } catch (e: Exception) {
            // Sem efeito funcional: pior caso é voltar a tentar a URL na próxima tela.
        }
    }

    private fun falhaExpirada(arquivoFalha: File): Boolean {
        val timestamp = arquivoFalha.readText().toLongOrNull() ?: return true
        return System.currentTimeMillis() - timestamp > TTL_FALHA_MS
    }

    private fun baixarBytes(url: String): ByteArray {
        val conexao = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            requestMethod = "GET"
        }
        try {
            if (conexao.responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("HTTP ${conexao.responseCode}")
            }
            return conexao.inputStream.use { it.readBytes() }
        } finally {
            conexao.disconnect()
        }
    }

    @Suppress("DEPRECATION")
    private fun formatoCompressaoDisco(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            Bitmap.CompressFormat.WEBP
        }

    private fun arquivoDisco(url: String, tamanhoPx: Int): File =
        File(diretorioDisco, hashDe(url, tamanhoPx))

    /** Fase 14.4 — marcador de "sem logo" para a mesma URL/tamanho, separado da imagem em si. */
    private fun arquivoFalha(url: String, tamanhoPx: Int): File =
        File(diretorioDisco, "${hashDe(url, tamanhoPx)}.miss")

    private fun hashDe(url: String, tamanhoPx: Int): String =
        MessageDigest.getInstance("MD5")
            .digest("$url@$tamanhoPx".toByteArray())
            .joinToString("") { "%02x".format(it) }

    // ---- Local (drawable/asset) — já embutido no APK, só cache em memória ----

    private fun carregarLocal(nome: String, tamanhoPx: Int): Bitmap? {
        val idDrawable = contexto.resources.getIdentifier(nome, "drawable", contexto.packageName)
        if (idDrawable != 0) {
            decodificarRecursoComAmostra(idDrawable, tamanhoPx)?.let { return it }
        }
        return listOf("$nome.png", "$nome.webp", "$nome.jpg", "logos/$nome.png")
            .firstNotNullOfOrNull { caminho ->
                try {
                    val bytes = contexto.assets.open(caminho).use { it.readBytes() }
                    decodificarBytesComAmostra(bytes, tamanhoPx)
                } catch (e: Exception) {
                    null
                }
            }
    }

    // ---- Fase 10.2 — decodificação com amostragem (evita bitmaps maiores que o necessário) ----

    private fun decodificarBytesComAmostra(bytes: ByteArray, tamanhoPx: Int): Bitmap? {
        val opcoes = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opcoes)
        opcoes.inSampleSize = calcularInSampleSize(opcoes, tamanhoPx)
        opcoes.inJustDecodeBounds = false
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opcoes)
    }

    private fun decodificarRecursoComAmostra(idDrawable: Int, tamanhoPx: Int): Bitmap? {
        val opcoes = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(contexto.resources, idDrawable, opcoes)
        opcoes.inSampleSize = calcularInSampleSize(opcoes, tamanhoPx)
        opcoes.inJustDecodeBounds = false
        return BitmapFactory.decodeResource(contexto.resources, idDrawable, opcoes)
    }

    private fun calcularInSampleSize(opcoes: BitmapFactory.Options, tamanhoPx: Int): Int {
        val altura = opcoes.outHeight
        val largura = opcoes.outWidth
        var inSampleSize = 1
        if (altura > tamanhoPx || largura > tamanhoPx) {
            val alturaMetade = altura / 2
            val larguraMetade = largura / 2
            while (alturaMetade / inSampleSize >= tamanhoPx && larguraMetade / inSampleSize >= tamanhoPx) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
