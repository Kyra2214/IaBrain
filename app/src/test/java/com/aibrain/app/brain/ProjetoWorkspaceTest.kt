package com.aibrain.app.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ProjetoWorkspaceTest {
    @Test
    fun `contribuicoes diferentes no mesmo arquivo sao conflito com proveniencia`() {
        val a = ContribuicaoWorkspace("a", "p", FonteContribuicao.ZIP, "IA A", arquivos = listOf(ArquivoWorkspace("src/App.kt", "hash-a", 10, "IA A")))
        val b = ContribuicaoWorkspace("b", "p", FonteContribuicao.GITHUB, "GitHub", arquivos = listOf(ArquivoWorkspace("src/App.kt", "hash-b", 10, "GitHub")))
        val resultado = AnalisadorWorkspace.comparar(emptyList(), listOf(a, b))

        assertEquals(listOf("src/App.kt"), resultado.conflitos)
        assertEquals(TipoMudanca.CONFLITO, resultado.mudancas.single().tipo)
        assertEquals(listOf("IA A", "GitHub"), resultado.mudancas.single().fontes)
    }

    @Test
    fun `arquivo novo igual e alterado sao classificados contra base`() {
        val base = listOf(ArquivoWorkspace("README.md", "r", 1), ArquivoWorkspace("src/Main.kt", "old", 1), ArquivoWorkspace("old.txt", "gone", 1))
        val fonte = ContribuicaoWorkspace("a", "p", FonteContribuicao.ZIP, "ZIP", arquivos = listOf(
            ArquivoWorkspace("README.md", "r", 1),
            ArquivoWorkspace("src/Main.kt", "new", 2),
            ArquivoWorkspace("src/Test.kt", "test", 3)
        ))
        val mudancas = AnalisadorWorkspace.comparar(base, listOf(fonte)).mudancas.associateBy { it.caminho }
        assertEquals(TipoMudanca.IGUAL, mudancas["README.md"]?.tipo)
        assertEquals(TipoMudanca.MODIFICADO, mudancas["src/Main.kt"]?.tipo)
        assertEquals(TipoMudanca.NOVO, mudancas["src/Test.kt"]?.tipo)
        assertEquals(TipoMudanca.REMOVIDO, mudancas["old.txt"]?.tipo)
    }

    @Test
    fun `relatorio nunca declara build remoto como aprovado sem execucao`() {
        val relatorio = ValidadorProjeto.validar(listOf(ArquivoWorkspace("README.md", "r", 1)))
        val remoto = relatorio.itens.first { it.nivel == NivelValidacao.REMOTO }
        assertEquals(StatusValidacao.DEPENDE_AMBIENTE_EXTERNO, remoto.status)
        assertTrue(!relatorio.aprovadoLocalmente)
    }

    @Test
    fun `ci standard seleciona perfil por stack e nao fixa pipeline universal`() {
        assertEquals("Android/Kotlin", IaBrainCiStandard.perfilPara("ANDROID_KOTLIN")?.stack)
        assertEquals("React/TypeScript", IaBrainCiStandard.perfilPara("React/TypeScript")?.stack)
        assertEquals(null, IaBrainCiStandard.perfilPara("Rust"))
    }

    @Test
    fun `importador calcula hash e rejeita path traversal`() {
        val zip = File.createTempFile("workspace", ".zip")
        ZipOutputStream(zip.outputStream()).use { output ->
            output.putNextEntry(ZipEntry("src/Main.kt")); output.write("class Main".toByteArray()); output.closeEntry()
            output.putNextEntry(ZipEntry("../segredo.txt")); output.write("não importar".toByteArray()); output.closeEntry()
            output.putNextEntry(ZipEntry("C:\\arquivo.txt")); output.write("não importar".toByteArray()); output.closeEntry()
            output.putNextEntry(ZipEntry("a/../../arquivo.txt")); output.write("não importar".toByteArray()); output.closeEntry()
        }
        val resultado = ZipWorkspaceImporter.importar(zip, "teste")
        assertEquals(listOf("src/Main.kt"), resultado.arquivos.map { it.caminho })
        assertEquals(listOf("../segredo.txt", "C:/arquivo.txt", "a/../../arquivo.txt"), resultado.rejeitados)
        assertTrue(resultado.arquivos.single().hash.length == 64)
        zip.delete()
    }
}
