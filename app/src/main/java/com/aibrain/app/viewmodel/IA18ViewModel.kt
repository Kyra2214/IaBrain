package com.aibrain.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableCornerSize
import androidx.lifecycle.MutableLiveData
import com.aibrain.app.repository.IA18Repository

class IA18ViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = IA18Repository(application)

    private val _categorias = MutableLiveData<List<IA18Repository.Categoria18>>()
    val categorias: LiveData<List<IA18Repository.Categoria18>> = _categorias

    private val _ias = MutableLiveData<List<IA18Repository.IA18>>()
    val ias: LiveData<List<IA18Repository.IA18>> = _ias

    private val _categoriaSelecionada = MutableLiveData<IA18Repository.Categoria18?>()
    val categoriaSelecionada: LiveData<IA18Repository.Categoria18?> = _categoriaSelecionada

    fun carregarDados() {
        val cats = repository.carregarCategorias()
        _categorias.value = cats
        if (cats.isNotEmpty() && _categoriaSelecionada.value == null) {
            selecionarCategoria(cats[0])
        }
    }

    fun selecionarCategoria(categoria: IA18Repository.Categoria18) {
        _categoriaSelecionada.value = categoria
        _ias.value = repository.carregarIAs(categoria.id)
    }

    fun isIdadeConfirmada(): Boolean = repository.isIdadeConfirmada()

    fun confirmarIdade() {
        repository.confirmarIdade()
    }
}
