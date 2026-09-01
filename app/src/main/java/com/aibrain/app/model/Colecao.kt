package com.aibrain.app.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Colecao(
    val id: String,
    val titulo: String,
    val descricao: String,
    val tipo: String,
    val itens: List<String>
) : Parcelable

@Parcelize
data class Guia(
    val id: String,
    val titulo: String,
    val descricao: String,
    val passos: List<String>,
    val ferramentas: List<String>
) : Parcelable
