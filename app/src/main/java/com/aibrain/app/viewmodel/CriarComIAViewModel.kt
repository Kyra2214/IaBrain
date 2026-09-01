package com.aibrain.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aibrain.app.brain.ProjetoRecommendation
import com.aibrain.app.data.local.AppDatabase
import com.aibrain.app.data.local.SalvarProjetoCompletoUseCase
import kotlinx.coroutines.launch

/** Mantém a Activity desacoplada do DAO e prepara o fluxo para uma fonte remota futura. */
class CriarComIAViewModel(application: Application) : AndroidViewModel(application) {
    private val salvarProjeto = SalvarProjetoCompletoUseCase(AppDatabase.getInstance(application))
    fun salvarAnalise(recomendacao: ProjetoRecommendation) {
        viewModelScope.launch { salvarProjeto(recomendacao) }
    }
}
