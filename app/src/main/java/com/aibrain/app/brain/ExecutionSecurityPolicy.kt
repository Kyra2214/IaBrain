package com.aibrain.app.brain

/** Valida dados antes de entregar uma unidade de trabalho a uma IA externa. */
object ExecutionSecurityPolicy {
    enum class Status { SAFE, BLOCKED }
    data class Finding(val rule: String, val message: String)
    data class Result(val status: Status, val findings: List<Finding>) {
        val allowed: Boolean get() = status == Status.SAFE
    }

    fun validateWorkItem(item: ProjectWorkPlanner.WorkItem): Result {
        val findings = mutableListOf<Finding>()
        if (item.functionId.isBlank()) findings += Finding("function-id", "Função não identificada")
        if (!item.branchName.matches(Regex("^ai/[a-z0-9][a-z0-9-]{0,39}/[a-z0-9][a-z0-9-]{0,39}$"))) {
            findings += Finding("branch", "Branch de trabalho fora do formato permitido")
        }
        if (item.requiredCapabilities.isEmpty()) findings += Finding("capabilities", "Capacidades exigidas ausentes")
        if (!item.command.matches(Regex("^/[a-z0-9-]{1,32}$"))) findings += Finding("command", "Comando inválido")
        return Result(if (findings.isEmpty()) Status.SAFE else Status.BLOCKED, findings)
    }

    fun scanPrompt(prompt: String): Result {
        val findings = mutableListOf<Finding>()
        if (prompt.length > MAX_PROMPT_LENGTH) findings += Finding("prompt-size", "Prompt excede o limite de segurança")
        if (prompt.any { it.code < 0x20 && it != '\n' && it != '\t' }) findings += Finding("control-chars", "Prompt contém caracteres de controle")
        SECRET_PATTERNS.forEach { (name, pattern) ->
            if (pattern.containsMatchIn(prompt)) findings += Finding(name, "Possível segredo detectado; revisão humana obrigatória")
        }
        return Result(if (findings.isEmpty()) Status.SAFE else Status.BLOCKED, findings)
    }

    private const val MAX_PROMPT_LENGTH = 50_000
    private val SECRET_PATTERNS = listOf(
        "private-key" to Regex("-----BEGIN [A-Z ]*PRIVATE KEY-----"),
        "github-token" to Regex("gh[pousr]_[A-Za-z0-9_]{20,}"),
        "bearer-token" to Regex("(?i)bearer\\s+[A-Za-z0-9._~-]{20,}"),
        "password-assignment" to Regex("(?i)(password|senha|secret)\\s*[=:]\\s*[^\\s]{8,}")
    )
}
