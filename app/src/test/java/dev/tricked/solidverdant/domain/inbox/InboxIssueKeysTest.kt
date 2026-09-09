/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.domain.inbox

import org.junit.Assert.assertEquals
import org.junit.Test

class InboxIssueKeysTest {
    @Test fun missing_and_long_keys_swap_the_entry_id() {
        assertEquals(
            "missing:v1:abc-1:1000:DESCRIPTION",
            rekeyInboxIssueKey("missing:v1:local-1:1000:DESCRIPTION", "local-1", "abc-1"),
        )
        assertEquals(
            "long:v1:abc-1:10:1000:50000",
            rekeyInboxIssueKey("long:v1:local-1:10:1000:50000", "local-1", "abc-1"),
        )
    }

    @Test fun overlap_key_reorders_the_pair_and_its_time_tokens_when_the_new_id_sorts_first() {
        // "local-1" sorted after the server id "bbb"; the rekeyed "aaa" sorts before it, so the
        // pair and the (start, end) tokens travel together into the order InboxAnalyzer would use.
        assertEquals(
            "overlap:v1:aaa:bbb:300:400:100:200",
            rekeyInboxIssueKey("overlap:v1:bbb:local-1:100:200:300:400", "local-1", "aaa"),
        )
        assertEquals(
            "overlap:v1:bbb:ccc:100:200:300:400",
            rekeyInboxIssueKey("overlap:v1:bbb:local-1:100:200:300:400", "local-1", "ccc"),
        )
    }

    @Test fun keys_for_other_entries_are_untouched() {
        val key = "missing:v1:local-10:1000:DESCRIPTION"
        assertEquals(key, rekeyInboxIssueKey(key, "local-1", "aaa"))
        assertEquals("overlap:v1:a:b:1:2:3:4", rekeyInboxIssueKey("overlap:v1:a:b:1:2:3:4", "local-1", "aaa"))
    }
}
