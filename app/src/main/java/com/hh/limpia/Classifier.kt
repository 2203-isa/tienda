package com.hh.limpia

/**
 * Reglas para saber de dónde viene cada archivo y qué tipo de imagen es
 * (comprobante, captura de IA, captura de internet).
 */
object Classifier {

    private val SCREEN_REC_WORDS = listOf(
        "screenrecord", "screen record", "screen_record", "screenrecorder", "screen-record",
        "grabacion de pantalla", "grabaciones de pantalla", "gravacao de tela", "gravacoes de tela",
        "gravador de tela", "grabadora de pantalla", "screencast", "record_screen", "screen_rec",
    )

    private val ARCHIVE_WORDS = listOf(
        "archiv", "arquiv", "oculto", "oculta", "hidden", "backup", "respaldo",
        "privad", "private", "secure folder", "cofre", "vault", "papelera vieja",
    )

    private val SOCIAL_WORDS = listOf(
        "instagram", "facebook", "messenger", "tiktok", "kwai", "snapchat", "pinterest",
        "twitter", "threads", "reddit", "snaptube", "vidmate", "likee", "signal",
        "discord", "wechat", "viber", "line/", "youtube", "shein", "shopee", "mercado",
    )

    private val AI_NAME_WORDS = listOf(
        "chatgpt", "openai", "gemini", "bard", "claude", "anthropic", "copilot", "deepseek",
        "perplexity", "meta ai", "metaai", "grok", "character.ai", "characterai", "qwen",
        "mistral", "lechat", "manus", "poe.", "com.openai", "com.google.android.apps.bard",
    )

    private val AI_IMAGE_WORDS = listOf(
        "chatgpt image", "chatgpt_image", "dall-e", "dall·e", "dalle", "gemini_generated",
        "generated_image", "generated image", "midjourney", "leonardo", "ideogram", "firefly",
        "bing_image", "image_fx", "imagefx", "meta_ai", "grok_image", "sora",
    )

    private val BROWSER_WORDS = listOf(
        "chrome", "sbrowser", "samsung internet", "firefox", "opera", "brave", "duckduckgo",
        "browser", "navegador", "edge", "kiwi", "vivaldi", "googlequicksearchbox", "safari",
        "mi browser", "ucmobile", "uc browser",
    )

    private val SCREENSHOT_PREFIX = Regex("^(screenshot|captura|screen shot|screen_shot|scr)[ _\\-]*[0-9_\\-. ]*")

    /** Nombre de la app que algunos celulares (Samsung, Xiaomi, Motorola) ponen al final de la captura. */
    private fun appPart(nameLower: String): String =
        nameLower.substringBeforeLast('.').replace(SCREENSHOT_PREFIX, "").trim()

    fun detectOrigin(name: String, folder: String, isVideo: Boolean): Origin {
        val n = normalizeText(name)
        val f = normalizeText(folder)
        val all = "$f/$n"
        if (isVideo && SCREEN_REC_WORDS.any { all.contains(it) }) return Origin.SCREEN_RECORDING
        if (!isVideo && (f.contains("screenshot") || f.contains("captura") || f.contains("screen shot") ||
                n.startsWith("screenshot") || n.startsWith("captura") || n.startsWith("screen shot") ||
                n.startsWith("screen_shot"))
        ) return Origin.SCREENSHOT
        if (f.contains("whatsapp")) return Origin.WHATSAPP
        if (f.contains("telegram")) return Origin.TELEGRAM
        if (ARCHIVE_WORDS.any { f.contains(it) }) return Origin.ARCHIVED
        if (f.startsWith("download") || f.contains("/download") || f.contains("descarga") || f.contains("bluetooth")) return Origin.DOWNLOAD
        if (SOCIAL_WORDS.any { f.contains(it) }) return Origin.SOCIAL
        if (f == "dcim" || f.contains("camera") || f.contains("camara") || f.contains("100andro") ||
            f.contains("100media") || f.contains("opencamera")
        ) return Origin.CAMERA
        return Origin.OTHER
    }

    fun isAiByName(name: String, origin: Origin): Boolean {
        val n = normalizeText(name)
        if (AI_IMAGE_WORDS.any { n.contains(it) }) return true
        if (origin == Origin.SCREENSHOT) {
            val app = appPart(n)
            if (app.isNotEmpty() && AI_NAME_WORDS.any { app.contains(it) }) return true
        }
        return false
    }

    fun isBrowserByName(name: String, origin: Origin): Boolean {
        if (origin != Origin.SCREENSHOT) return false
        val app = appPart(normalizeText(name))
        if (app.isEmpty()) return false
        if (app == "google" || app == "internet" || app.endsWith("_google") || app.endsWith(" google")) return true
        return BROWSER_WORDS.any { app.contains(it) }
    }

    // ---------- Texto leído dentro de la imagen ----------

    private val RECEIPT_STRONG = Regex(
        "\\b(comprovante|comprobante|pix|transferencia|transferencias|pagamento|pago exitoso|pago realizado|" +
            "pago aprobado|transaccion|transacao|recibo|boleto|autenticacao|chave|favorecido|aprobacion|" +
            "valor pago|autorizacion|id da transacao|envio exitoso|referencia de pago|fatura paga|" +
            "voucher|ticket de pago|pagaste|enviaste|recebeu|transferiste|deposito|consignacion)\\b"
    )

    private val RECEIPT_WEAK = listOf(
        "nequi", "daviplata", "bancolombia", "davivienda", "nubank", "picpay", "mercado pago", "itau",
        "bradesco", "santander", "caixa", "banco", "inter", "sicoob", "sicredi", "pagbank", "efecty",
        "western union", "remitly", "agencia", "conta", "cpf", "cnpj", "cedula", "destino", "origem",
        "origen", "pagador", "beneficiario", "destinatario", "valor", "total", "aprobado", "exitoso",
        "concluido", "concluida", "realizado", "realizada", "enviado", "recebido", "recibido", "monto",
        "comision", "saldo", "tarifa",
    )

    private val MONEY = Regex("(r\\$|\\$|cop|usd|brl)\\s?\\d")

    private val AI_TEXT = listOf(
        "chatgpt", "gemini", "copilot", "deepseek", "perplexity", "meta ai", "claude", "grok",
        "pode cometer erros", "can make mistakes", "puede cometer errores", "puede equivocarse",
        "informacoes imprecisas", "informacion inexacta", "pergunte ao", "pregunta a gemini",
        "message chatgpt", "mensagem para o chatgpt", "pregunta lo que quieras", "pergunte alguma coisa",
        "ask anything", "escribe a chatgpt",
    )

    private val URL = Regex(
        "(https?://|www\\.|\\b[a-z0-9-]{2,}\\.(com|net|org|gov|edu|io|co|br|me|info|app|tv|ai|es)(\\.[a-z]{2})?(/|\\b))"
    )

    fun classifyText(raw: String): Int {
        val t = normalizeText(raw)
        if (t.isBlank()) return 0
        var flags = 0

        val strong = RECEIPT_STRONG.findAll(t).map { it.value }.toSet().size
        val weak = RECEIPT_WEAK.count { t.contains(it) }
        val money = MONEY.containsMatchIn(t)
        val score = strong * 2 + weak + (if (money) 1 else 0)
        if (strong >= 1 && (score >= 4 || (score >= 3 && money))) flags = flags or Flags.RECEIPT

        if (AI_TEXT.any { t.contains(it) }) flags = flags or Flags.AI
        if (URL.containsMatchIn(t)) flags = flags or Flags.WEB
        return flags
    }
}
