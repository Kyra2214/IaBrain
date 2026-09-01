package com.aibrain.app.data.local

import android.content.Context

class ProjetoContextoRepository(context: Context) {
    private val dao = AppDatabase.getInstance(context).projetoContextoDao()
    suspend fun buscar(projetoId: String): ProjetoContextoEntity? = dao.buscar(projetoId)
    suspend fun salvar(contexto: ProjetoContextoEntity) = dao.salvar(contexto)
}
