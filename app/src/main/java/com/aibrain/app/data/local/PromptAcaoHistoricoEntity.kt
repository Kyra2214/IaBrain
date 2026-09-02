package com.aibrain.app.data.local

import androidx.room.Entity
import androidx.room.Index

@Entity(tableName = "prompt_acoes_historico", indices = [Index("promptId"), Index("acao"), Index("criadoEm")])
data class PromptAcaoHistoricoEntity(
    @androidx.room.PrimaryKey val id: String,
    val promptId: String?,
    val acao: String,
    val iaId: String? = null,
    val detalhe: String = "",
    val criadoEm: Long
)
