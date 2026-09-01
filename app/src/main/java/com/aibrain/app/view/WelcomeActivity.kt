package com.aibrain.app.view

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.aibrain.app.data.AssistenteIARepository
import com.aibrain.app.databinding.ActivityWelcomeBinding

/**
 * WelcomeActivity — Primeira tela do app.
 * Mostra o nome do app e uma mensagem de boas-vindas; o botão "Entrar"
 * leva para a listagem principal (MainActivity).
 *
 * Fase 12.1 — só é exibida na primeira abertura do app; depois disso,
 * pula direto para a MainActivity (guardado em SharedPreferences).
 *
 * Fase 18.3 — na PRIMEIRA abertura (ainda sem `ja_viu_welcome`), se não
 * houver API key da Groq configurada ([AssistenteIARepository], Fase 18.1),
 * o botão "Entrar" leva para [AssistenteIAActivity] em vez de direto para
 * [MainActivity], convidando o usuário a configurar o Assistente de IA
 * (curadoria) antes de usar o app — sem bloquear o uso: a tela continua
 * acessível a qualquer momento pela navegação principal (Fase 18.2), e
 * aberturas seguintes (ou já com chave salva) nunca forçam esse desvio.
 */
class WelcomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWelcomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(PREFS_NOME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(CHAVE_JA_VIU_WELCOME, false)) {
            abrirListagem(marcarComoVista = false)
            return
        }

        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnEntrar.setOnClickListener {
            abrirListagem(marcarComoVista = true)
        }
    }

    private fun abrirListagem(marcarComoVista: Boolean) {
        if (marcarComoVista) {
            getSharedPreferences(PREFS_NOME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(CHAVE_JA_VIU_WELCOME, true)
                .apply()

            // Fase 18.3 — só desvia para o Assistente de IA na PRIMEIRA abertura
            // (marcarComoVista=true só acontece aqui) e só se ainda não há chave salva.
            if (!AssistenteIARepository(applicationContext).temApiKey()) {
                startActivity(
                    Intent(this, AssistenteIAActivity::class.java)
                        .putExtra(AssistenteIAActivity.EXTRA_VEIO_DO_ONBOARDING, true)
                )
                finish()
                return
            }
        }
        startActivity(Intent(this, AIBrainActivity::class.java))
        finish()
    }

    companion object {
        private const val PREFS_NOME = "ai_brain_prefs"
        private const val CHAVE_JA_VIU_WELCOME = "ja_viu_welcome"
    }
}
