package com.example.recetario

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

data class Recipe(
    val id: String,
    val name: String,
    val imageUrl: String?,
    val ingredients: List<String>,
    val steps: List<String>,
    val sourceUrl: String
)

object RecipeParser {

    private val ingRe = Regex("^\\s*ingredientes\\b.*", RegexOption.IGNORE_CASE)
    private val stepRe = Regex("^\\s*(preparaci[oó]n|elaboraci[oó]n|paso a paso|pasos|instrucciones|modo de preparaci[oó]n).*", RegexOption.IGNORE_CASE)
    private val stopRe = Regex("comentarios|notas|te puede interesar|compartir|newsletter|suscripci", RegexOption.IGNORE_CASE)

    fun fromUrl(url: String): Recipe {
        val doc = Jsoup.connect(url).userAgent("Mozilla/5.0 (Android)").timeout(15000).get()
        val a = fromJsonLd(doc, url)
        if (a != null) return a
        val b = fromHtml(doc, url)
        if (b != null) return b
        throw Exception("No encontré ingredientes ni preparación en esa página")
    }

    private fun pageImage(doc: Document): String? {
        val og = doc.select("meta[property=og:image]").attr("abs:content")
        return if (og.isNotBlank()) og else null
    }

    private fun pageName(doc: Document): String {
        val og = doc.select("meta[property=og:title]").attr("content")
        return if (og.isNotBlank()) og.trim() else doc.title()
    }

    private fun isHead(e: Element): Boolean {
        return e.text().length < 40 && e.children().size <= 1
    }

    private fun collect(all: List<Element>, from: Int, to: Int): ArrayList<String> {
        val lis = ArrayList<String>()
        val ps = ArrayList<String>()
        for (k in from until to) {
            val e = all[k]
            if (isHead(e) && stopRe.containsMatchIn(e.text())) break
            if (e.tagName() == "li") {
                val t = e.text().trim()
                if (t.isNotEmpty() && !lis.contains(t)) lis.add(t)
            } else if (e.tagName() == "p") {
                val t = e.text().trim()
                if (t.length > 3 && !ps.contains(t)) ps.add(t)
            }
        }
        return if (lis.isNotEmpty()) lis else ps
    }

    private fun fromHtml(doc: Document, url: String): Recipe? {
        var ing = ArrayList<String>()
        var steps = ArrayList<String>()
        for (e in doc.select("[itemprop=recipeIngredient],[itemprop=ingredients]")) {
            ing.add(e.text().trim())
        }
        for (e in doc.select("[itemprop=recipeInstructions]")) {
            val lis = e.select("li")
            if (lis.isEmpty()) steps.add(e.text().trim()) else for (l in lis) steps.add(l.text().trim())
        }
        if (ing.isEmpty() || steps.isEmpty()) {
            val all = doc.body().allElements.toList()
            var iIdx = -1
            for (k in all.indices) {
                if (isHead(all[k]) && ingRe.matches(all[k].text())) { iIdx = k; break }
            }
            if (iIdx >= 0) {
                var sIdx = -1
                for (k in iIdx + 1 until all.size) {
                    if (isHead(all[k]) && stepRe.matches(all[k].text())) { sIdx = k; break }
                }
                if (ing.isEmpty()) {
                    val end = if (sIdx > 0) sIdx else minOf(all.size, iIdx + 80)
                    ing = collect(all, iIdx + 1, end)
                }
                if (steps.isEmpty() && sIdx > 0) {
                    steps = collect(all, sIdx + 1, minOf(all.size, sIdx + 150))
                }
            }
        }
        if (ing.isEmpty() && steps.isEmpty()) return null
        return Recipe(System.currentTimeMillis().toString(), pageName(doc), pageImage(doc), ing, steps, url)
    }

    private fun fromJsonLd(doc: Document, url: String): Recipe? {
        for (el in doc.select("script[type=application/ld+json]")) {
            var json: JsonElement? = null
            try {
                json = JsonParser.parseString(el.data())
            } catch (e: Exception) {
            }
            if (json == null) continue
            val node = findRecipe(json)
            if (node == null) continue
            val ing = ArrayList<String>()
            val arr = node.get("recipeIngredient")
            if (arr != null && arr.isJsonArray) {
                for (x in arr.asJsonArray) ing.add(x.asString.trim())
            }
            val steps = ArrayList<String>()
            collectSteps(node.get("recipeInstructions"), steps)
            if (ing.isEmpty() && steps.isEmpty()) continue
            var name = pageName(doc)
            val n = node.get("name")
            if (n != null && n.isJsonPrimitive) name = n.asString.trim()
            var img = imageOf(node.get("image"))
            if (img == null) img = pageImage(doc)
            return Recipe(System.currentTimeMillis().toString(), name, img, ing, steps, url)
        }
        return null
    }

    private fun isRecipe(o: JsonObject): Boolean {
        val t = o.get("@type")
        if (t == null) return false
        if (t.isJsonArray) {
            for (x in t.asJsonArray) {
                if (x.asString == "Recipe") return true
            }
            return false
        }
        return t.asString == "Recipe"
    }

    private fun findRecipe(e: JsonElement): JsonObject? {
        if (e.isJsonArray) {
            for (x in e.asJsonArray) {
                val r = findRecipe(x)
                if (r != null) return r
            }
            return null
        }
        if (e.isJsonObject) {
            val o = e.asJsonObject
            if (isRecipe(o)) return o
            val g = o.get("@graph")
            if (g != null) return findRecipe(g)
        }
        return null
    }

    private fun imageOf(e: JsonElement?): String? {
        if (e == null || e.isJsonNull) return null
        if (e.isJsonPrimitive) return e.asString
        if (e.isJsonArray) {
            for (x in e.asJsonArray) {
                val r = imageOf(x)
                if (r != null) return r
            }
            return null
        }
        if (e.isJsonObject) {
            val u = e.asJsonObject.get("url")
            if (u != null && u.isJsonPrimitive) return u.asString
        }
        return null
    }

    private fun collectSteps(e: JsonElement?, out: MutableList<String>) {
        if (e == null || e.isJsonNull) return
        if (e.isJsonPrimitive) {
            out.add(Jsoup.parse(e.asString).text())
            return
        }
        if (e.isJsonArray) {
            for (x in e.asJsonArray) collectSteps(x, out)
            return
        }
        if (e.isJsonObject) {
            val o = e.asJsonObject
            val list = o.get("itemListElement")
            if (list != null) {
                collectSteps(list, out)
            } else {
                val t = o.get("text")
                if (t != null && t.isJsonPrimitive) out.add(Jsoup.parse(t.asString).text())
            }
        }
    }
}
