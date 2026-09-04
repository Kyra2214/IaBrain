package com.aibrain.app.view

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.aibrain.app.R
import com.aibrain.app.data.AiApiCatalogSync
import com.aibrain.app.data.AssistenteIARepository
import com.aibrain.app.databinding.ActivityWelcomeBinding

/**
 * WelcomeActivity — entrada e primeira experiência de marca do IaBrain.
 *
 * A tela começa com um loading curto, usando o cérebro tecnológico como ponto
 * focal da identidade. Usuários que já concluíram o onboarding seguem para a
 * listagem após a mesma transição; novos usuários veem a tela de boas-vindas.
 */
class WelcomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWelcomeBinding
    private val mainHandler = Handler(Looper.getMainLooper())
    private var logoPulse: ObjectAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        configurarBarrasDoSistema()
        iniciarPulsoDaMarca()
        AiApiCatalogSync(applicationContext).syncIfStale()

        val prefs = getSharedPreferences(PREFS_NOME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(CHAVE_JA_VIU_WELCOME, false)) {
            // Loading curto também nas aberturas seguintes evita um salto visual
            // e mantém a assinatura de marca como porta de entrada do produto.
            mainHandler.postDelayed({ abrirListagem(marcarComoVista = false) }, DURACAO_LOADING_RETORNO)
            return
        }

        mainHandler.postDelayed({ mostrarBoasVindas() }, DURACAO_LOADING_PRIMEIRA_ABERTURA)
        binding.btnEntrar.setOnClickListener {
            abrirListagem(marcarComoVista = true)
        }
    }

    private fun configurarBarrasDoSistema() {
        window.statusBarColor = getColor(R.color.brand_background)
        window.navigationBarColor = getColor(R.color.brand_background)
        var systemUiFlags = window.decorView.systemUiVisibility
        systemUiFlags = systemUiFlags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            systemUiFlags = systemUiFlags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
        }
        window.decorView.systemUiVisibility = systemUiFlags
    }

    private fun iniciarPulsoDaMarca() {
        logoPulse = ObjectAnimator.ofPropertyValuesHolder(
            binding.imgLoadingLogo,
            PropertyValuesHolder.ofFloat(View.ALPHA, 0.78f, 1f),
            PropertyValuesHolder.ofFloat(View.SCALE_X, 0.97f, 1.03f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.97f, 1.03f)
        ).apply {
            duration = 1300L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun mostrarBoasVindas() {
        logoPulse?.cancel()
        binding.loadingContainer.animate()
            .alpha(0f)
            .setDuration(220L)
            .withEndAction {
                binding.loadingContainer.visibility = View.GONE
                binding.welcomeContent.visibility = View.VISIBLE
                binding.welcomeContent.alpha = 0f
                binding.welcomeContent.animate()
                    .alpha(1f)
                    .setDuration(280L)
                    .start()
            }
            .start()
    }

    private fun abrirListagem(marcarComoVista: Boolean) {
        mainHandler.removeCallbacksAndMessages(null)
        logoPulse?.cancel()

        if (marcarComoVista) {
            getSharedPreferences(PREFS_NOME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(CHAVE_JA_VIU_WELCOME, true)
                .apply()

            // Na primeira abertura, se não houver chave salva, o usuário é
            // convidado a configurar o Assistente de IA antes da listagem.
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

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        logoPulse?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val PREFS_NOME = "ai_brain_prefs"
        private const val CHAVE_JA_VIU_WELCOME = "ja_viu_welcome"
        private const val DURACAO_LOADING_RETORNO = 900L
        private const val DURACAO_LOADING_PRIMEIRA_ABERTURA = 1250L
    }
}
