package com.example.recetario

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.jsoup.Jsoup

data class Recipe(
    val id: String,
    val name: String,
    val imageUrl: String?,
    val ingredients: List<String>,
    val steps: List<String>,
    val sourceUrl: String
)

object RecipeParser {

    fun fromUrl(url: String): Recipe {
        val doc = Jsoup.connect(url).userAgent("Mozilla/5.0 (Android)").timeout(15000).get()
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
            var name = doc.title()
            val n = node.get("name")
            if (n != null && n.isJsonPrimitive) name = n.asString.trim()
            var img = imageOf(node.get("image"))
            if (img == null) {
                val og = doc.select("meta[property=og:image]").attr("content")
                if (og.isNotBlank()) img = og
            }
            return Recipe(System.currentTimeMillis().toString(), name, img, ing, steps, url)
        }
        throw Exception("No encontré una receta estructurada en ese link")
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
