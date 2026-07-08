/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.config

data class ParsedOAuthConfig(val endpoint: String? = null, val clientId: String? = null)

private val endpointLabel = Regex(
    "(?im)^\\s*[\"']?(?:server|endpoint|url)[\"']?\\s*[:=]\\s*[\"']?(\\S+)",
)
private val url = Regex("https?://[^\\s,;]+", RegexOption.IGNORE_CASE)
private val uuid = Regex(
    "(?i)(?<![0-9a-f])[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(?![0-9a-f])",
)

/** Extracts whatever useful OAuth configuration is present in copied text. */
fun parseOAuthConfig(text: String): ParsedOAuthConfig {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return ParsedOAuthConfig()

    val uuidMatch = uuid.find(trimmed)
    val clientId = uuidMatch?.value
    var endpoint: String? = null

    // Accept the terse form copied by some deployments: <server>:<uuid>.
    if (uuidMatch != null && !trimmed.contains('\n') && uuidMatch.range.last == trimmed.lastIndex) {
        val separator = uuidMatch.range.first - 1
        if (separator > 0 && trimmed[separator] == ':') {
            endpoint = trimmed.substring(0, separator).trim()
        }
    }

    if (endpoint == null) endpoint = endpointLabel.find(trimmed)?.groupValues?.get(1)
    if (endpoint == null) endpoint = url.find(trimmed)?.value

    return ParsedOAuthConfig(
        endpoint = endpoint?.trimEnd('/', ',', ';', '\'', '"', ')', ']'),
        clientId = clientId,
    )
}
