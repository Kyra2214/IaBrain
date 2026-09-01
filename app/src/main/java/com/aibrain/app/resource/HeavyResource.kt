package com.aibrain.app.resource

data class HeavyResource(
    val id: String,
    val version: String,
    val url: String,
    val filename: String,
    val sizeBytes: Long,
    val sha256: String,
    val type: String,
    val required: Boolean
)

/** Metadados reais do modelo escolhido; o arquivo não é empacotado no APK. */
object BuiltinResourceManifest {
    val qwen3_06b_q4_0 = HeavyResource(
        id = "qwen3-0.6b-q4-0", version = "1.0.0",
        url = "https://huggingface.co/ggml-org/Qwen3-0.6B-GGUF/resolve/main/Qwen3-0.6B-Q4_0.gguf",
        filename = "qwen3-0.6b-q4-0.gguf", sizeBytes = 428970080L,
        sha256 = "da2572f16c06133561ce56accaa822216f2391ef4d37fba427801cd6736417d4",
        type = "llm", required = false
    )
}
