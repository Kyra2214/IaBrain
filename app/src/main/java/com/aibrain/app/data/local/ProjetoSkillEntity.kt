package com.aibrain.app.data.local

import androidx.room.Entity
import androidx.room.Index

@Entity(tableName = "projeto_skills", indices = [Index("projetoId"), Index("ativo")])
data class ProjetoSkillEntity(
    @androidx.room.PrimaryKey val id: String,
    val projetoId: String?,
    val nome: String,
    val descricao: String,
    val passos: List<String>,
    val ativo: Boolean,
    val criadoEm: Long,
    val atualizadoEm: Long
)
