package com.aibrain.app.brain

import java.net.URI

/** Shared URL validation used by Explorer domain objects before any handoff. */
fun isSafeHttps(value: String): Boolean = runCatching {
    val uri = URI(value.trim())
    uri.scheme.equals("https", true) &&
        !uri.host.isNullOrBlank() &&
        uri.userInfo == null &&
        uri.fragment == null &&
        !uri.host.equals("localhost", true) &&
        !uri.host.startsWith("127.") &&
        !uri.host.startsWith("10.") &&
        !uri.host.startsWith("192.168.")
}.getOrDefault(false)
