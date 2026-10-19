package com.example.recetario

import com.google.gson.*
import org.jsoup.Jsoup

data class Recipe(
    val id: String, val name: String, val imageUrl: String?,
    val ingredients: List<String>, val steps: List<String>, val sourceUrl: String
)

object RecipeParser {
    /** Descarga la página y lee la receta desde el JSON-LD (schema.org/Recipe). Ejecutar fuera del hilo principal. */
    fun fromUrl(url: String): Recipe {
        val doc = Jsoup.connect(url).userAgent("Mozilla/5.0 (Android)").timeout(15000).get()
        for (el in doc.select("script[type=application/ld+json]")) {
            val json = try { JsonParser.parseString(el.data()) } catch (e: Exception) { continue }
            val node = findRecipe(json) ?: continue
            val ing = node.getAsJsonArray("recipeIngredient")?.map { it.asString.trim() } ?: emptyList()
            val steps = mutableListOf<String>()
            collectSteps(node.get("recipeInstructions"), steps)
            if (ing.isEmpty() && steps.isEmpty()) continue
            return Recipe(
                id = System.currentTimeMillis().toString(),
                name = node.get("name")?.asString?.trim() ?: doc.title(),
                imageUrl = imageOf(node.get("image")) ?: doc.select("meta[property=og:image]").attr("content").ifBlank { null },
                ingredients = ing, steps = steps, sourceUrl = url
            )
        }
        throw Exception("No encontré una receta estructurada en ese link")
    }

    private fun isRecipe(o: JsonObject): Boolean {
        val t = o.get("@type") ?: return false
