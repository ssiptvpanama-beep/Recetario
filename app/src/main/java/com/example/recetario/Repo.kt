package com.example.recetario

import android.content.Context
import com.google.gson.Gson
import java.io.File

data class Price(val ingredient: String, val store: String, val price: Double)
data class Data(val recipes: MutableList<Recipe> = mutableListOf(), val prices: MutableList<Price> = mutableListOf())

class Repo(ctx: Context) {
    private val file = File(ctx.filesDir, "data.json")
    private val gson = Gson()
    var data: Data = try { gson.fromJson(file.readText(), Data::class.java) } catch (e: Exception) { Data() }
    fun save() = file.writeText(gson.toJson(data))
}
