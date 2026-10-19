package com.example.recetario

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = Repo(applicationContext)
        val shared = intent?.getStringExtra(Intent.EXTRA_TEXT)?.trim() ?: ""
        setContent { MaterialTheme { App(repo, shared) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(repo: Repo, initialUrl: String) {
    val scope = rememberCoroutineScope()
    var recipes by remember { mutableStateOf(repo.data.recipes.toList()) }
    var prices by remember { mutableStateOf(repo.data.prices.toList()) }
    var selected by remember { mutableStateOf<Recipe?>(null) }
    var url by remember { mutableStateOf(initialUrl) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var priceFor by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = selected != null) { selected = null }

    Scaffold(topBar = { TopAppBar(title = { Text(selected?.name ?: "Mi recetario") }) }) { pad ->
        Box(Modifier.padding(pad).padding(12.dp)) {
            val r = selected
            if (r == null) {
                Column {
                    OutlinedTextField(url, { url = it }, label = { Text("Pega el link de una receta") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    Button(enabled = !loading && url.isNotBlank(), onClick = {
                        loading = true; error = null
                        scope.launch {
                            try {
                                val rec = withContext(Dispatchers.IO) { RecipeParser.fromUrl(url.trim()) }
                                repo.data.recipes.add(0, rec); repo.save()
                                recipes = repo.data.recipes.toList(); url = ""
                            } catch (e: Exception) { error = e.message ?: "Error al leer el link" }
                            loading = false
                        }
                    }) { Text(if (loading) "Extrayendo..." else "Guardar receta") }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(recipes) { rec ->
                            Card(onClick = { selected = rec }, modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(8.dp)) {
                                    AsyncImage(rec.imageUrl, null, Modifier.size(80.dp).clip(MaterialTheme.shapes.small),
                                        contentScale = ContentScale.Crop)
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(rec.name, style = MaterialTheme.typography.titleMedium)
                                        Text("${rec.ingredients.size} ingredientes", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        AsyncImage(r.imageUrl, null, Modifier.fillMaxWidth().height(200.dp).clip(MaterialTheme.shapes.medium),
                            contentScale = ContentScale.Crop)
                        Text("Ingredientes", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
                    }
                    items(r.ingredients) { ing ->
                        val best = prices.filter { it.ingredient == ing }.minByOrNull { it.price }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("• $ing")
                                best?.let { Text("Más barato: ${it.store} $${it.price}", style = MaterialTheme.typography.bodySmall) }
                            }
                            TextButton(onClick = { priceFor = ing }) { Text("Precios") }
                        }
                    }
                    item { Text("Preparación", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp)) }
                    items(r.steps.withIndex().toList()) { (i, s) -> Text("${i + 1}. $s") }
                    item {
                        TextButton(onClick = { repo.data.recipes.removeAll { it.id == r.id }; repo.save(); recipes = repo.data.recipes.toList(); selected = null }) {
                            Text("Eliminar receta")
                        }
                    }
                }
            }
        }
    }

    priceFor?.let { ing ->
        val ctx = LocalContext.current
        var store by remember { mutableStateOf("") }
        var price by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { priceFor = null },
            title = { Text(ing, style = MaterialTheme.typography.titleSmall) },
            text = {
                Column {
                    prices.filter { it.ingredient == ing }.sortedBy { it.price }.forEachIndexed { i, p ->
                        Text("${if (i == 0) "★ " else ""}${p.store}: $${p.price}")
                    }
                    OutlinedTextField(store, { store = it }, label = { Text("Tienda") }, singleLine = true)
                    OutlinedTextField(price, { price = it }, label = { Text("Precio") }, singleLine = true)
                    TextButton(onClick = {
                        val v = price.replace(",", ".").toDoubleOrNull()
                        if (v != null && store.isNotBlank()) {
                            repo.data.prices.add(Price(ing, store.trim(), v)); repo.save()
                            prices = repo.data.prices.toList(); store = ""; price = ""
                        }
                    }) { Text("Guardar precio") }
                    TextButton(onClick = {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode("supermercado"))))
                    }) { Text("Ver tiendas cerca de mí") }
                }
            },
            confirmButton = { TextButton(onClick = { priceFor = null }) { Text("Cerrar") } })
    }
}
