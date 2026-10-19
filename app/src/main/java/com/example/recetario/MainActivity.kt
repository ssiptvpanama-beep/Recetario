package com.example.recetario

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest

val Orange = Color(0xFFF4511E)
val OrangeDark = Color(0xFFBF360C)
val Cream = Color(0xFFFFF8F1)
val Peach = Color(0xFFFFE0D2)

val categories = listOf("Desayuno", "Almuerzo", "Cena", "Postres", "Bebidas", "Cócteles", "Snacks", "Otros")

fun catIcon(c: String?): String = when (c) {
    "Desayuno" -> "🥞"
    "Almuerzo" -> "🍲"
    "Cena" -> "🍽️"
    "Postres" -> "🍰"
    "Bebidas" -> "🥤"
    "Cócteles" -> "🍹"
    "Snacks" -> "🍿"
    else -> "📦"
}

fun sha(s: String): String =
    MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = Repo(applicationContext)
        val shared = intent?.getStringExtra(Intent.EXTRA_TEXT)?.trim() ?: ""
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(primary = Orange, secondary = OrangeDark, background = Cream, surface = Color.White)
            ) {
                Surface(color = Cream, modifier = Modifier.fillMaxSize()) { Root(repo, shared) }
            }
        }
    }
}

@Composable
fun Root(repo: Repo, shared: String) {
    var user by remember { mutableStateOf(repo.data.user) }
    var loggedIn by remember { mutableStateOf(repo.data.loggedIn) }
    val u = user
    if (u == null) {
        RegisterScreen { nu ->
            repo.data.user = nu
            repo.data.loggedIn = true
            repo.save()
            user = nu
            loggedIn = true
        }
    } else if (!loggedIn) {
        LoginScreen(u,
            onLogin = {
                repo.data.loggedIn = true
                repo.save()
                loggedIn = true
            },
            onOther = {
                repo.data.user = null
                repo.data.loggedIn = false
                repo.save()
                user = null
            })
    } else {
        Home(repo, u, shared) {
            repo.data.loggedIn = false
            repo.save()
            loggedIn = false
        }
    }
}

@Composable
fun AuthShell(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Orange, OrangeDark)), RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))
                .padding(vertical = 40.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🍳", fontSize = 56.sp)
                Text("Mi Recetario", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = Color.White.copy(alpha = 0.9f))
            }
        }
        Card(
            Modifier.fillMaxWidth().padding(20.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(6.dp)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                content()
            }
        }
    }
}

@Composable
fun RegisterScreen(onRegister: (User) -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var pass2 by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    AuthShell("Crear cuenta", "Guarda y organiza tus recetas favoritas") {
        OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(email, { email = it }, label = { Text("Correo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(pass, { pass = it }, label = { Text("Contraseña") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(pass2, { pass2 = it }, label = { Text("Repetir contraseña") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = {
                val msg: String? = when {
                    name.isBlank() -> "Escribe tu nombre"
                    !email.contains("@") || !email.contains(".") -> "Correo no válido"
                    pass.length < 6 -> "La contraseña debe tener al menos 6 caracteres"
                    pass != pass2 -> "Las contraseñas no coinciden"
                    else -> null
                }
                err = msg
                if (msg == null) onRegister(User(name.trim(), email.trim().lowercase(), sha(pass)))
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp)
        ) { Text("Registrarme", fontSize = 16.sp) }
    }
}

@Composable
fun LoginScreen(user: User, onLogin: () -> Unit, onOther: () -> Unit) {
    var pass by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    AuthShell("Hola de nuevo, ${user.name} 👋", "Tus recetas te esperan") {
        OutlinedTextField(pass, { pass = it }, label = { Text("Contraseña") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = {
                if (sha(pass) == user.passHash) onLogin() else err = "Contraseña incorrecta"
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp)
        ) { Text("Entrar", fontSize = 16.sp) }
        TextButton(onClick = onOther) { Text("Usar otra cuenta") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChipRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (o in options) {
            FilterChip(
                selected = o == selected,
                onClick = { onSelect(o) },
                label = { Text(if (o == "Todas") "Todas" else catIcon(o) + " " + o) }
            )
        }
    }
}

@Composable
fun Home(repo: Repo, user: User, initialUrl: String, onLogout: () -> Unit) {
    val scope = rememberCoroutineScope()
    var recipes by remember { mutableStateOf(repo.data.recipes.toList()) }
    var prices by remember { mutableStateOf(repo.data.prices.toList()) }
    var selected by remember { mutableStateOf<Recipe?>(null) }
    var url by remember { mutableStateOf(initialUrl) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var priceFor by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("Todas") }
    var newCat by remember { mutableStateOf("Otros") }

    BackHandler(enabled = selected != null) { selected = null }

    val r = selected
    val cardColors = CardDefaults.cardColors(containerColor = Color.White)

    val q = query.trim().lowercase()
    val shown = recipes.filter { rec ->
        val catOk = filter == "Todas" || (rec.category ?: "Otros") == filter
        val textOk = q.isEmpty() ||
            rec.name.lowercase().contains(q) ||
            rec.ingredients.any { it.lowercase().contains(q) } ||
            rec.steps.any { it.lowercase().contains(q) }
        catOk && textOk
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Orange, OrangeDark)), RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                .padding(horizontal = 12.dp, vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (r != null) {
                TextButton(onClick = { selected = null }, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
                    Text("←", fontSize = 24.sp)
                }
            }
            Column(Modifier.weight(1f).padding(start = if (r == null) 8.dp else 0.dp)) {
                Text(if (r == null) "🍳 Mi Recetario" else "Receta", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                Text(
                    if (r == null) "Hola, ${user.name} 👋" else r.name,
                    color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2
                )
            }
            if (r == null) {
                TextButton(onClick = onLogout, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
                    Text("Salir")
                }
            }
        }

        if (r == null) {
            LazyColumn(
                Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = cardColors,
                        elevation = CardDefaults.cardElevation(4.dp)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("➕ Agregar receta", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            OutlinedTextField(url, { url = it }, label = { Text("Pega el link de una receta") },
                                modifier = Modifier.fillMaxWidth(), singleLine = true)
                            Text("Categoría", fontSize = 12.sp, color = Color.Gray)
                            ChipRow(categories, newCat) { newCat = it }
                            Button(
                                enabled = !loading && url.isNotBlank(),
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                onClick = {
                                    loading = true
                                    error = null
                                    scope.launch {
                                        try {
                                            val rec = withContext(Dispatchers.IO) { RecipeParser.fromUrl(url.trim()) }
                                            repo.data.recipes.add(0, rec.copy(category = newCat))
                                            repo.save()
                                            recipes = repo.data.recipes.toList()
                                            url = ""
                                        } catch (e: Exception) {
                                            error = e.message ?: "Error al leer el link"
                                        }
                                        loading = false
                                    }
                                }
                            ) { Text(if (loading) "Extrayendo..." else "Guardar receta") }
                            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
                item {
                    Text("Mis recetas (${shown.size})", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                item {
                    OutlinedTextField(
                        query, { query = it },
                        label = { Text("🔍 Buscar por nombre o ingrediente") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    )
                }
                item {
                    ChipRow(listOf("Todas") + categories, filter) { filter = it }
                }
                if (recipes.isEmpty()) {
                    item {
                        Text("Aún no tienes recetas 🍽️\nPega un link arriba para empezar.", color = Color.Gray)
                    }
                } else if (shown.isEmpty()) {
                    item { Text("No hay recetas con esa búsqueda 🔎", color = Color.Gray) }
                }
                items(shown) { rec ->
                    Card(
                        Modifier.fillMaxWidth().clickable { selected = rec },
                        shape = RoundedCornerShape(20.dp),
                        colors = cardColors,
                        elevation = CardDefaults.cardElevation(4.dp)
                    ) {
                        Column {
                            AsyncImage(
                                rec.imageUrl, null,
                                Modifier.fillMaxWidth().height(150.dp).background(Peach),
                                contentScale = ContentScale.Crop
                            )
                            Column(Modifier.padding(12.dp)) {
                                Text(rec.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 2)
                                Text(
                                    "${catIcon(rec.category)} ${rec.category ?: "Otros"} · 🥕 ${rec.ingredients.size} · 👩‍🍳 ${rec.steps.size} pasos",
                                    fontSize = 12.sp, color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Column {
                        Spacer(Modifier.height(12.dp))
                        AsyncImage(
                            r.imageUrl, null,
                            Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(20.dp)).background(Peach),
                            contentScale = ContentScale.Crop
                        )
                        Text("Categoría", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 12.dp))
                        ChipRow(categories, r.category ?: "Otros") { c ->
                            val idx = repo.data.recipes.indexOfFirst { it.id == r.id }
                            if (idx >= 0) {
                                val upd = r.copy(category = c)
                                repo.data.recipes[idx] = upd
                                repo.save()
                                recipes = repo.data.recipes.toList()
                                selected = upd
                            }
                        }
                        Text("🥕 Ingredientes", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                            color = OrangeDark, modifier = Modifier.padding(top = 16.dp))
                    }
                }
                items(r.ingredients) { ing ->
                    val best = prices.filter { it.ingredient == ing }.minByOrNull { it.price }
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = cardColors) {
                        Row(Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(ing)
                                best?.let {
                                    Text("★ Más barato: ${it.store} $${it.price}", fontSize = 12.sp, color = OrangeDark)
                                }
                            }
                            TextButton(onClick = { priceFor = ing }) { Text("💰 Precios") }
                        }
                    }
                }
                item {
                    Text("👩‍🍳 Preparación", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        color = OrangeDark, modifier = Modifier.padding(top = 16.dp))
                }
                items(r.steps.withIndex().toList()) { (i, s) ->
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            Modifier.size(28.dp).clip(CircleShape).background(Orange),
                            contentAlignment = Alignment.Center
                        ) { Text("${i + 1}", color = Color.White, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.width(10.dp))
                        Text(s, modifier = Modifier.weight(1f))
                    }
                }
                item {
                    TextButton(onClick = {
                        repo.data.recipes.removeAll { it.id == r.id }
                        repo.save()
                        recipes = repo.data.recipes.toList()
                        selected = null
                    }) { Text("🗑️ Eliminar receta", color = Color.Gray) }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    priceFor?.let { ing ->
        val ctx = LocalContext.current
        var store by remember { mutableStateOf("") }
        var price by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { priceFor = null },
            title = { Text(ing, fontSize = 15.sp) },
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
                            repo.data.prices.add(Price(ing, store.trim(), v))
                            repo.save()
                            prices = repo.data.prices.toList()
                            store = ""
                            price = ""
                        }
                    }) { Text("Guardar precio") }
                    TextButton(onClick = {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode("supermercado"))))
                    }) { Text("📍 Ver tiendas cerca de mí") }
                }
            },
            confirmButton = { TextButton(onClick = { priceFor = null }) { Text("Cerrar") } }
        )
    }
}
