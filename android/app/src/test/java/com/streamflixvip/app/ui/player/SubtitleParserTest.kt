package com.streamflixvip.app.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleParserTest {
    @Test
    fun vttComHorasCurtasETags() {
        val vtt = "\uFEFFWEBVTT\n\nNOTE x\n\n00:01.500 --> 00:03.000 line:0\n<v Ana><i>Oi</i> &amp; <font color=red>tchau</font>\n\n" +
            "1:00:00.000 --> 1:00:02.000\n{\\an8}Topo\n"
        val cues = parseSubtitleText(vtt)
        assertEquals(2, cues.size)
        assertEquals(1500L, cues[0].startMs)
        assertEquals("<i>Oi</i> & tchau", cues[0].text)
        assertTrue(cues[0].top)
        assertEquals(3_600_000L, cues[1].startMs)
        assertEquals("Topo", cues[1].text)
        assertTrue(cues[1].top)
    }

    @Test
    fun srtComCrlfENumeros() {
        val srt = "1\r\n00:00:01,000 --> 00:00:02,000\r\nLinha 1\r\n42\r\n\r\n2\r\n00:00:03,000 --> 00:00:04,000\r\nB\r\n"
        val cues = parseSubtitleText(srt)
        assertEquals(2, cues.size)
        assertEquals("Linha 1\n42", cues[0].text)
        assertFalse(cues[0].top)
        assertEquals(3000L, cues[1].startMs)
    }
}
