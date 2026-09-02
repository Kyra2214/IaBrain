package com.aibrain.app.data.local

import androidx.room.Entity
import androidx.room.Index

@Entity(tableName = "projeto_tarefas", indices = [Index("projetoId"), Index("status"), Index("prioridade"), Index("atualizadoEm")])
data class ProjetoTarefaEntity(
    @androidx.room.PrimaryKey val id: String,
    val projetoId: String?,
    val titulo: String,
    val detalhe: String,
    val status: String,
    val prioridade: String,
    val criadoEm: Long,
    val atualizadoEm: Long
)
