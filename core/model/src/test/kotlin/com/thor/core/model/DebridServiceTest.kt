package com.thor.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Which account the Movies section is holding, and which credential goes with it.
 *
 * Two services, one selected, and both credentials kept — so the failure this
 * guards against is the quiet one: the wrong key sent to the right service,
 * which is indistinguishable from an expired key at every point after this and
 * reads to the user as "TorBox rejected my token".
 */
class DebridServiceTest {

    @Test
    fun `the credential follows the service that was chosen`() {
        val settings = MediaSettings(
            realDebridToken = "rd-token",
            torBoxApiKey = "tb-key",
        )

        assertThat(settings.copy(debridService = DebridService.REAL_DEBRID).debridToken)
            .isEqualTo("rd-token")
        assertThat(settings.copy(debridService = DebridService.TORBOX).debridToken)
            .isEqualTo("tb-key")
    }

    /**
     * Switching services does not throw the other credential away.
     *
     * The one thing a chooser must not do: a user trying TorBox for an evening
     * should not have to go and find their Real-Debrid token again to switch
     * back, and a settings screen that silently emptied a field would be doing
     * exactly that.
     */
    @Test
    fun `both credentials survive the switch`() {
        val settings = MediaSettings(realDebridToken = "rd-token", torBoxApiKey = "tb-key")
            .copy(debridService = DebridService.TORBOX)

        assertThat(settings.realDebridToken).isEqualTo("rd-token")
        assertThat(settings.torBoxApiKey).isEqualTo("tb-key")
    }

    /**
     * Configured means the selected service is configured.
     *
     * Read as "a token exists somewhere", a user who had filled in Real-Debrid
     * and then chosen TorBox would be told the section was ready, and would find
     * out otherwise only when a film refused to open.
     */
    @Test
    fun `a service with no credential is not configured, whatever the other holds`() {
        val realDebridOnly = MediaSettings(realDebridToken = "rd-token")

        assertThat(realDebridOnly.isDebridConfigured).isTrue()
        assertThat(realDebridOnly.copy(debridService = DebridService.TORBOX).isDebridConfigured)
            .isFalse()
    }

    /** Existing settings files carry no service, and must keep the one they had. */
    @Test
    fun `the default is the service that was there before there was a choice`() {
        assertThat(MediaSettings().debridService).isEqualTo(DebridService.REAL_DEBRID)
    }

    @Test
    fun `every service says what it is called and what it asks for`() {
        DebridService.entries.forEach { service ->
            assertThat(service.label).isNotEmpty()
            assertThat(service.credentialLabel).isNotEmpty()
        }
    }
}
