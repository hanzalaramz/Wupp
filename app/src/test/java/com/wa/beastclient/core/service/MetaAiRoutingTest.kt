package com.wa.beastclient.core.service

import org.junit.Assert.assertEquals
import org.junit.Test

class MetaAiRoutingTest {

    @Test
    fun canonicalMetaAiJidNormalizesPnAndLidToBotDomain() {
        assertEquals("13135550002@bot", WaRealtimeService.canonicalMetaAiJid("13135550002@s.whatsapp.net"))
        assertEquals("13135550002@bot", WaRealtimeService.canonicalMetaAiJid("13135550002@lid"))
        assertEquals("13135550002@bot", WaRealtimeService.canonicalMetaAiJid("13135550002:0@s.whatsapp.net"))
        assertEquals("13135550002@bot", WaRealtimeService.canonicalMetaAiJid("13135550002@bot"))
        assertEquals("923001234567@s.whatsapp.net", WaRealtimeService.canonicalMetaAiJid("923001234567@s.whatsapp.net"))
    }
}
