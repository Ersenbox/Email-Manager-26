package com.example.common.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlTextTest {

    @Test
    fun `css inside the body does not reach the text`() {
        val html = """
            <html><body><style type="text/css">@media all and (max-width: 480px){*[class].ib_t{min-width:100%!important}}</style>
            <p>gizmbuse und 6 weitere</p></body></html>
        """.trimIndent()

        val text = HtmlText.toPlainText(html)

        assertEquals("gizmbuse und 6 weitere", text)
    }

    @Test
    fun `head script comments and unclosed style are removed`() {
        val html = "<head><title>Gizli</title></head><!-- not --><script>var a=1;</script>Metin<style>.x{color:red}"

        assertEquals("Metin", HtmlText.toPlainText(html))
    }

    @Test
    fun `entities are decoded once`() {
        assertEquals("Größe & Preis: 5 € <ok> ç", HtmlText.toPlainText("Gr&ouml;&szlig;e &amp; Preis:&nbsp;5 &euro; &lt;ok&gt; &#231;"))
        assertEquals("&lt;", HtmlText.toPlainText("&amp;lt;"))
    }

    @Test
    fun `hidden preheader filler is dropped`() {
        assertEquals("Merhaba", HtmlText.toPlainText("Merhaba&zwnj;&#8203;&#847;&shy;"))
    }

    @Test
    fun `block tags keep lines and blank runs collapse`() {
        val text = HtmlText.toPlainText("<div>Bir</div><div></div><div></div><div></div><p>Iki<br>Uc</p>")

        assertTrue(text, text.startsWith("Bir\n"))
        assertTrue(text, text.endsWith("Iki\nUc"))
        assertFalse(text, text.contains("\n\n\n"))
    }

    @Test
    fun `tracking pixels are found and normal images are not`() {
        assertTrue(HtmlText.hasTrackingPixel("<img src='https://a.example/x.gif' width='1' height='1'>"))
        assertTrue(HtmlText.hasTrackingPixel("<IMG style=\"display:none\" src=\"https://a.example/x.png\">"))
        assertTrue(HtmlText.hasTrackingPixel("<img src=\"https://t.example/track/open?id=5\">"))
        assertFalse(HtmlText.hasTrackingPixel("<img src=\"https://a.example/logo.png\" width=\"120\" height=\"40\">"))
        assertFalse(HtmlText.hasTrackingPixel("<img src=\"https://a.example/logo.png\" width=\"1\" height=\"40\">"))
        assertFalse(HtmlText.hasTrackingPixel("<p>metin</p>"))
    }

    @Test
    fun `preview is one short line`() {
        assertEquals("a b c", HtmlText.preview("a\n\n  b\tc"))
        assertEquals(160, HtmlText.preview("x".repeat(500)).length)
    }
}
