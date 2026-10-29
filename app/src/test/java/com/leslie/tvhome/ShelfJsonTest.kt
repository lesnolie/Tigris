package com.leslie.tvhome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ShelfJsonTest {
    @Test fun parsesAShelfArrayAndIgnoresExtraFields() {
        val items = ShelfJson.parse(
            """
            [
              {"title":" Arrival ","meta":" 2024 · Movie ","image":" https://media.example/a.jpg ","url":"https://example/play","intent":"view"},
              {"title":"Second","meta":"","image":"http://192.168.1.20:8096/image/2"}
            ]
            """.trimIndent(),
        )
        assertEquals(2, items!!.size)
        assertEquals("Arrival", items[0].title)
        assertEquals("2024 · Movie", items[0].meta)
        assertEquals("https://media.example/a.jpg", items[0].image)
        assertEquals("", items[1].meta)
        assertEquals("http://192.168.1.20:8096/image/2", items[1].image)
    }

    @Test fun rejectsDocumentsThatAreNotAnArray() {
        assertNull(ShelfJson.parse(""))
        assertNull(ShelfJson.parse("   "))
        assertNull(ShelfJson.parse("not json"))
        assertNull(ShelfJson.parse("<html>nope</html>"))
        assertNull(ShelfJson.parse("""{"Items":[]}"""))
        assertNull(ShelfJson.parse("""{"title":"One","meta":"","image":"https://example/a.jpg"}"""))
    }

    @Test fun emptyArrayIsValidButHasNoItems() {
        assertEquals(emptyList<ShelfJson.Item>(), ShelfJson.parse("[]"))
        assertEquals(emptyList<ShelfJson.Item>(), ShelfJson.parse("[null, 1, \"x\", []]"))
    }

    @Test fun skipsBrokenItemsAndKeepsTheRest() {
        val items = ShelfJson.parse(
            """
            [
              {"title":"","image":"https://example/a.jpg"},
              {"title":"No picture","meta":"x","image":""},
              {"title":"File","image":"file:///tmp/a.jpg"},
              {"title":"Script","image":"javascript:alert(1)"},
              {"meta":"missing title","image":"https://example/a.jpg"},
              {"title":null,"image":"https://example/a.jpg"},
              {"title":"Ok","image":"https://example/ok.jpg","meta":null},
              {"title":"Relative","image":"/Items/1/Images/Primary"}
            ]
            """.trimIndent(),
        )
        assertEquals(listOf("Ok"), items!!.map { it.title })
        assertEquals("", items[0].meta)
    }

    @Test fun capsItemCount() {
        val rows = (1..20).joinToString(",") { i ->
            """{"title":"Item $i","meta":"m","image":"https://example/$i.jpg"}"""
        }
        val items = ShelfJson.parse("[$rows]")!!
        assertEquals(ShelfLimits.MAX_ITEMS, items.size)
        assertEquals("Item 1", items.first().title)
        assertEquals("Item 12", items.last().title)
        assertFalse(items.any { it.image.endsWith("/13.jpg") })
    }

    @Test fun truncatesTitleAndMeta() {
        val items = ShelfJson.parse(
            """[{"title":"${"T".repeat(200)}","meta":"${"M".repeat(300)}","image":"https://example/a.jpg"}]""",
        )!!
        assertEquals("T".repeat(ShelfLimits.MAX_TITLE), items[0].title)
        assertEquals("M".repeat(ShelfLimits.MAX_META), items[0].meta)
    }

    @Test fun acceptsAByteOrderMark() {
        val items = ShelfJson.parse("\uFEFF[{\"title\":\"One\",\"image\":\"https://example/a.jpg\"}]")
        assertEquals("One", items!!.single().title)
    }

    @Test fun numericTitleIsCoerced() {
        val items = ShelfJson.parse("""[{"title":12,"image":"https://example/a.jpg"}]""")
        assertEquals("12", items!!.single().title)
    }

    @Test fun httpUrlsAllowLanAndRejectOtherSchemes() {
        assertTrue(ShelfLimits.isHttpUrl("https://example.com/shelf"))
        assertTrue(ShelfLimits.isHttpUrl("http://192.168.1.20:8766/shelf"))
        assertTrue(ShelfLimits.isHttpUrl("http://[::1]:8766/shelf"))
        assertTrue(ShelfLimits.isHttpUrl("https://i0.hdslb.com/bfs/archive/abc.jpg@1280w_720h_1c.jpg"))
        assertFalse(ShelfLimits.isHttpUrl("ftp://example.com/a"))
        assertFalse(ShelfLimits.isHttpUrl("file:///etc/passwd"))
        assertFalse(ShelfLimits.isHttpUrl("javascript:alert(1)"))
        assertFalse(ShelfLimits.isHttpUrl("http://"))
        assertFalse(ShelfLimits.isHttpUrl("https://example.com/a b"))
        assertFalse(ShelfLimits.isHttpUrl("http://" + "a".repeat(ShelfLimits.MAX_URL_CHARS)))
    }

    @Test fun sampleSizeKeepsTheLongEdgeNearTheCap() {
        assertEquals(1, ShelfLimits.sampleSize(1280, 720))
        assertEquals(1, ShelfLimits.sampleSize(1920, 1080))
        assertEquals(2, ShelfLimits.sampleSize(3840, 2160))
        assertEquals(4, ShelfLimits.sampleSize(8000, 4500))
        assertEquals(1, ShelfLimits.sampleSize(0, 100))
    }

    @Test fun readAtMostRejectsABodyPastTheCap() {
        assertEquals("hell", ShelfLimits.readAtMost("hell".byteInputStream(), 4).decodeToString())
        assertEquals("", ShelfLimits.readAtMost(ByteArray(0).inputStream(), 0).decodeToString())
        val error = assertThrows(IOException::class.java) {
            ShelfLimits.readAtMost("hello".byteInputStream(), 4)
        }
        assertTrue(error.message!!.contains("too large"))
    }

    @Test fun thirdPartyBilibiliClientSharesTheOfficialFeed() {
        assertEquals("bilibili", Shelf.builtInPackages["com.xiaodianshi.tv.yst"])
        assertEquals(
            Shelf.builtInPackages["com.xiaodianshi.tv.yst"],
            Shelf.builtInPackages["com.tutu.myblbl"],
        )
        assertEquals("douyu", Shelf.builtInPackages["com.douyu.xl.douyutv"])
    }

    @Test fun customFeedOverridesABuiltInSourceOnlyWhenItParses() {
        val jellyfin = Shelf.Custom("org.jellyfin.androidtv", "http://192.168.1.20:8766/shelf")
        val bilibili = Shelf.Custom("com.xiaodianshi.tv.yst", "https://home.example/shelf")
        assertEquals(Shelf.ShelfPick.CUSTOM, Shelf.pickShelf("org.jellyfin.androidtv", jellyfin, customOk = true))
        assertEquals(Shelf.ShelfPick.NONE, Shelf.pickShelf("org.jellyfin.androidtv", jellyfin, customOk = false))
        assertEquals(Shelf.ShelfPick.CUSTOM, Shelf.pickShelf("com.xiaodianshi.tv.yst", bilibili, customOk = true))
        assertEquals(Shelf.ShelfPick.BUILTIN, Shelf.pickShelf("com.xiaodianshi.tv.yst", bilibili, customOk = false))
        assertEquals(Shelf.ShelfPick.BUILTIN, Shelf.pickShelf("com.tutu.myblbl", jellyfin, customOk = true))
        assertEquals(Shelf.ShelfPick.BUILTIN, Shelf.pickShelf("com.douyu.xl.douyutv", null, customOk = true))
        assertEquals(
            Shelf.ShelfPick.NONE,
            Shelf.pickShelf("org.jellyfin.androidtv", Shelf.Custom("org.jellyfin.androidtv", "file:///tmp/shelf.json"), true),
        )
        assertTrue(Shelf.has("com.tutu.myblbl"))
        assertTrue(Shelf.has("org.jellyfin.androidtv", jellyfin))
        assertFalse(Shelf.has("org.jellyfin.androidtv"))
    }
}
