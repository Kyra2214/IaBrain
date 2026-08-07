package com.aibrain.app.view

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.aibrain.app.databinding.ItemMensagemAssistenteBinding
import com.aibrain.app.databinding.ItemMensagemUsuarioBinding
import com.aibrain.app.model.MensagemChat

/**
 * Adapter do histórico da sessão de conversa do Criador de Prompts (Fase 17.2).
 * Dois tipos de item: mensagem do usuário (alinhada à direita) e resposta
 * do assistente (alinhada à esquerda, pronta para a Fase 17.6+ preencher
 * com as respostas reais do fluxo do Prompt Builder).
 */
class ChatAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val mensagens = mutableListOf<MensagemChat>()

    /** Adiciona uma mensagem ao fim do histórico da sessão e notifica a lista. */
    fun adicionarMensagem(mensagem: MensagemChat) {
        mensagens.add(mensagem)
        notifyItemInserted(mensagens.lastIndex)
    }

    inner class UsuarioViewHolder(val binding: ItemMensagemUsuarioBinding) :
        RecyclerView.ViewHolder(binding.root)

    inner class AssistenteViewHolder(val binding: ItemMensagemAssistenteBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun getItemViewType(position: Int): Int =
        if (mensagens[position].deUsuario) TIPO_USUARIO else TIPO_ASSISTENTE

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TIPO_USUARIO) {
            UsuarioViewHolder(ItemMensagemUsuarioBinding.inflate(inflater, parent, false))
        } else {
            AssistenteViewHolder(ItemMensagemAssistenteBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val mensagem = mensagens[position]
        when (holder) {
            is UsuarioViewHolder -> holder.binding.txtMensagemUsuario.text = mensagem.texto
            is AssistenteViewHolder -> holder.binding.txtMensagemAssistente.text = mensagem.texto
        }
    }

    override fun getItemCount(): Int = mensagens.size

    companion object {
        private const val TIPO_USUARIO = 0
        private const val TIPO_ASSISTENTE = 1
    }
}
