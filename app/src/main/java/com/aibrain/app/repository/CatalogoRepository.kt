package com.aibrain.app.repository

import android.content.Context
import com.aibrain.app.model.IA
import com.aibrain.app.model.NivelAcesso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException

/**
 * Repositório responsável por ler o catálogo de IAs a partir do JSON local
 * (app/src/main/assets/ia_catalogo.json).
 *
 * Fase 2.3 — usa org.json (já embutido no Android, sem dependências extras)
 * para manter o app leve, sem bibliotecas pesadas de parsing.
 *
 * Fase 8.3 — quando existe um catálogo atualizado em cache local
 * (baixado por AtualizacaoRepository), ele é usado no lugar do asset
 * embutido; o asset original continua servindo de base de comparação
 * de versão (Fase 8.1) e de fallback.
 *
 * Fase 10.1 — cache de dados em memória (nível de processo): evita
 * reparsear o JSON a cada chamada/instância (cada Activity cria a sua),
 * já que o catálogo raramente muda durante uma sessão. A chave de
 * invalidação é o próprio texto do JSON ativo — se mudar (ex.: Fase 8.3
 * aplicou uma atualização), o cache é automaticamente reparseado.
 *
 * Fase 10.3 — modo offline / resiliência: se o cache local (Fase 8.3)
 * existir mas estiver corrompido (gravação interrompida, JSON inválido),
 * a leitura não falha — descarta o cache ruim e volta para o asset
 * embutido no APK, que é sempre válido.
 */
class CatalogoRepository(private val context: Context) {

    companion object {
        private const val ARQUIVO_CATALOGO = "ia_catalogo.json"

        @Volatile private var jsonEmCache: String? = null
        @Volatile private var catalogoEmCache: List<IA>? = null
    }

    /**
     * Lê o catálogo ativo (cache atualizado, se houver; senão o asset embutido)
     * e retorna a lista de IAs. Executa em thread de I/O para não travar a UI.
     */
    suspend fun carregarCatalogo(): List<IA> = withContext(Dispatchers.IO) {
        val json = lerCatalogoAtivoOuAsset()

        val cache = catalogoEmCache
        if (cache != null && jsonEmCache == json) return@withContext cache

        val lista = parsearCatalogo(json)
        jsonEmCache = json
        catalogoEmCache = lista
        lista
    }

    /** Fase 10.3 — cache local (se válido) ou asset embutido como fallback. */
    private fun lerCatalogoAtivoOuAsset(): String {
        val atualizacaoRepositorio = AtualizacaoRepository(context)
        val jsonCache = atualizacaoRepositorio.catalogoCacheado() ?: return lerArquivoAssets(ARQUIVO_CATALOGO)

        return try {
            JSONObject(jsonCache) // valida antes de aceitar como catálogo ativo
            jsonCache
        } catch (e: Exception) {
            atualizacaoRepositorio.descartarCacheCorrompido()
            lerArquivoAssets(ARQUIVO_CATALOGO)
        }
    }

    /** Fase 8.1 — versão do ia_catalogo.json embutido no APK, usada como base de comparação. */
    suspend fun versaoDoAssetEmbutido(): Int = withContext(Dispatchers.IO) {
        JSONObject(lerArquivoAssets(ARQUIVO_CATALOGO)).optInt("versao", 0)
    }

    /**
     * Fase 12.5 — Ponto único de entrada para quem só quer "o catálogo certo,
     * já sincronizado", sem precisar orquestrar CatalogoRepository +
     * AtualizacaoRepository na Activity/ViewModel (antes cada chamador
     * repetia esses três passos). Retorna o catálogo atualizado se uma
     * versão remota mais nova foi encontrada e aplicada; senão, o catálogo
     * já carregado (local/cache).
     */
    suspend fun carregarCatalogoSincronizado(): List<IA> {
        val catalogoInicial = carregarCatalogo()
        val atualizacaoRepositorio = AtualizacaoRepository(context)
        val versaoBase = versaoDoAssetEmbutido()
        val atualizou = atualizacaoRepositorio.verificarEAtualizar(versaoBase)
        return if (atualizou) carregarCatalogo() else catalogoInicial
    }

    private fun lerArquivoAssets(nomeArquivo: String): String {
        return try {
            context.assets.open(nomeArquivo).bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            throw IllegalStateException("Não foi possível ler $nomeArquivo", e)
        }
    }

    private fun parsearCatalogo(json: String): List<IA> {
        val raiz = JSONObject(json)
        val arrayIas = raiz.getJSONArray("ias")
        val lista = mutableListOf<IA>()

        for (i in 0 until arrayIas.length()) {
            val obj = arrayIas.getJSONObject(i)

            val categorias = obj.getJSONArray("categorias").let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            }

            val idiomas = obj.getJSONArray("idiomas").let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            }

            val notasObj = obj.getJSONObject("notas")
            val notas = notasObj.keys().asSequence().associateWith { chave ->
                notasObj.getInt(chave)
            }

            val gratuita = obj.getBoolean("gratuita")
            // Fase 15.1 — "acesso" é opcional: catálogos antigos em cache (Fase 8/10.3)
            // continuam válidos, caindo no fallback derivado de `gratuita`.
            val acesso = NivelAcesso.porChave(obj.optString("acesso", ""))
                ?: if (gratuita) NivelAcesso.GRATUITA else NivelAcesso.PAGA
            // Fase 19.1 — "categoriaPrincipal" é opcional: catálogo ainda não curado
            // (Fase 19.2) e catálogos antigos em cache continuam válidos, caindo em null.
            val categoriaPrincipal = if (obj.has("categoriaPrincipal") && !obj.isNull("categoriaPrincipal")) {
                obj.getString("categoriaPrincipal")
            } else {
                null
            }

            lista.add(
                IA(
                    id = obj.getString("id"),
                    nome = obj.getString("nome"),
                    logo = obj.getString("logo"),
                    site = obj.getString("site"),
                    descricao = obj.getString("descricao"),
                    categorias = categorias,
                    idiomas = idiomas,
                    gratuita = gratuita,
                    acesso = acesso,
                    notas = notas,
                    categoriaPrincipal = categoriaPrincipal
                )
            )
        }

        return lista
    }
}
