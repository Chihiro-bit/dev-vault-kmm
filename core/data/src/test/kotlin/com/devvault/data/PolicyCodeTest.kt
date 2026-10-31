package com.devvault.data

import com.devvault.crypto.KdfParams
import kotlin.test.Test
import kotlin.test.assertEquals

class PolicyCodeTest {
    @Test fun policyCodeMatchesDesktop() {
        assertEquals(
            "2Cw8Wgm0Bu+JkCn4h4iNLK5QEFDAw8GrPjxPMKyuhHI=",
            PolicyCode.derive("安全码ＡＢ12", KdfParams("scrypt", 1024, 8, 1, "Dw4NDAsKCQgHBgUEAwIBAA==")),
        )
    }
}
