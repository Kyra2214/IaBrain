package com.aibrain.app.data.local

import androidx.room.Entity
import androidx.room.Index

@Entity(tableName = "projeto_memorias", indices = [Index("projetoId"), Index("criadoEm")])
data class ProjetoMemoriaEntity(
    @androidx.room.PrimaryKey val id: String,
    val projetoId: String,
    val tipo: String,
    val titulo: String,
    val conteudo: String,
    val criadoEm: Long,
    val atualizadoEm: Long
)
