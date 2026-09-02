package com.aibrain.app.data.local

import androidx.room.Entity
import androidx.room.Index

@Entity(tableName = "browser_contextos", indices = [Index("criadoEm"), Index("origem")])
data class BrowserContextoEntity(
    @androidx.room.PrimaryKey val id: String,
    val origem: String,
    val abaSelecionadaId: String?,
    val abas: List<String>,
    val prompt: String?,
    val criadoEm: Long
)
