/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.config

import org.junit.Assert.assertEquals
import org.junit.Test

class OAuthConfigParserTest {
    private val clientId = "123e4567-e89b-12d3-a456-426614174000"

    @Test
    fun parsesLabelledValues() {
        assertEquals(
            ParsedOAuthConfig("https://example.com", clientId),
            parseOAuthConfig("server:https://example.com/\nclient:$clientId"),
        )
    }

    @Test
    fun findsUrlAndUuidInSurroundingText() {
        assertEquals(
            ParsedOAuthConfig("https://example.com", clientId),
            parseOAuthConfig("Use https://example.com with $clientId to log in"),
        )
    }

    @Test
    fun parsesCompactPair() {
        assertEquals(
            ParsedOAuthConfig("my-server", clientId),
            parseOAuthConfig("my-server:$clientId"),
        )
    }

    @Test
    fun parsesCompactUrlPairWithoutIncludingUuidInUrl() {
        assertEquals(
            ParsedOAuthConfig("https://example.com", clientId),
            parseOAuthConfig("https://example.com:$clientId"),
        )
    }

    @Test
    fun findsClientUuidWithoutALabel() {
        assertEquals(ParsedOAuthConfig(clientId = clientId), parseOAuthConfig(clientId))
    }

    @Test
    fun findsServerWithoutAClientId() {
        assertEquals(
            ParsedOAuthConfig(endpoint = "https://example.com"),
            parseOAuthConfig("server = https://example.com/"),
        )
    }

    @Test
    fun acceptsQuotedJsonStyleServerLabel() {
        assertEquals(
            ParsedOAuthConfig("https://example.com", clientId),
            parseOAuthConfig("\"server\": \"https://example.com\",\n\"client\": \"$clientId\""),
        )
    }

    @Test
    fun acceptsUppercaseUuidAndNewerUuidVersions() {
        val versionSevenId = "01890F3E-7B5A-7CC2-98C4-DC0C0C07398F"
        assertEquals(
            ParsedOAuthConfig(clientId = versionSevenId),
            parseOAuthConfig("client_id: $versionSevenId"),
        )
    }

    @Test
    fun ignoresNonUuidClientValue() {
        assertEquals(ParsedOAuthConfig(), parseOAuthConfig("client: not-a-uuid"))
    }

    @Test
    fun doesNotTreatArbitraryColonTextAsConfig() {
        assertEquals(ParsedOAuthConfig(), parseOAuthConfig("meeting at 12:30"))
    }

    @Test
    fun trimsPunctuationAroundDetectedValues() {
        assertEquals(
            ParsedOAuthConfig("https://example.com", clientId),
            parseOAuthConfig("Connect to (https://example.com/), client: $clientId."),
        )
    }

    @Test
    fun emptyClipboardProducesNoValues() {
        assertEquals(ParsedOAuthConfig(), parseOAuthConfig("  \n  "))
    }

    @Test
    fun acceptsExplicitUnusualServerUrlForManualCorrection() {
        assertEquals(
            ParsedOAuthConfig(endpoint = "https:///example"),
            parseOAuthConfig("server:https:///example"),
        )
    }
}
