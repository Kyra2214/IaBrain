package com.aibrain.app.view

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.aibrain.app.databinding.ActivityIa18VerificacaoBinding
import com.aibrain.app.repository.IA18Repository

class IA18VerificacaoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIa18VerificacaoBinding
    private lateinit var repository: IA18Repository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIa18VerificacaoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = IA18Repository(this)

        binding.btnConfirmarIdade.setOnClickListener {
            repository.confirmarIdade()
            startActivity(Intent(this, IA18Activity::class.java))
            finish()
        }

        binding.btnSair.setOnClickListener {
            finish()
        }
    }
}
