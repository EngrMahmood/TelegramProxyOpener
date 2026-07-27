package com.example.telegramproxyopener

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.telegramproxyopener.ui.theme.TelegramProxyOpenerTheme
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

private const val TAG = "TelegramProxyOpener"

// Show the interstitial every other time the user opens a proxy in Telegram,
// so it doesn't interrupt the very first attempt or every single one after.
private const val INTERSTITIAL_EVERY_N_OPENS = 2

// ---------------- Data Models ----------------
enum class ProxyType { SOCKS5, MTPROTO }

data class Proxy(
    val server: String,
    val port: Int,
    val secret: String? = null,
    val type: ProxyType,
    val ping: Int? = null,
    val isOnline: Boolean?
)

// ---------------- MainActivity ----------------
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MobileAds.initialize(this)
        setContent {
            TelegramProxyOpenerTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ProxySelector()
                }
            }
        }
    }
}

// ---------------- Fetch MTProto Proxies ----------------
// Source: raw tg://proxy links, e.g. "https://t.me/proxy?server=host&port=443&secret=ee..."
suspend fun fetchMtProtoProxies(): List<Proxy> = withContext(Dispatchers.IO) {
    val client = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    val request = Request.Builder()
        .url("https://raw.githubusercontent.com/SoliSpirit/mtproto/master/all_proxies.txt")
        .build()

    try {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyList<Proxy>()
            val bodyString = response.body?.string() ?: return@withContext emptyList<Proxy>()

            bodyString.lineSequence()
                .mapNotNull { line ->
                    val uri = try { Uri.parse(line.trim()) } catch (e: Exception) { null }
                    val server = uri?.getQueryParameter("server")
                    val port = uri?.getQueryParameter("port")?.toIntOrNull()
                    val secret = uri?.getQueryParameter("secret")
                    if (server.isNullOrBlank() || port == null || secret.isNullOrBlank()) return@mapNotNull null
                    Proxy(server = server, port = port, secret = secret, type = ProxyType.MTPROTO, ping = null, isOnline = null)
                }
                .take(10)
                .toList()
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to fetch MTProto proxies", e)
        emptyList()
    }
}

// ---------------- Fetch SOCKS5 Proxies ----------------
// Source: plain "ip:port" lines
suspend fun fetchSocksProxies(): List<Proxy> = withContext(Dispatchers.IO) {
    val client = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    val request = Request.Builder()
        .url("https://raw.githubusercontent.com/hookzof/socks5_list/master/proxy.txt")
        .build()

    try {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyList<Proxy>()
            val bodyString = response.body?.string() ?: return@withContext emptyList<Proxy>()

            bodyString.lineSequence()
                .mapNotNull { line ->
                    val parts = line.trim().split(":")
                    if (parts.size != 2) return@mapNotNull null
                    val port = parts[1].toIntOrNull() ?: return@mapNotNull null
                    Proxy(server = parts[0], port = port, secret = null, type = ProxyType.SOCKS5, ping = null, isOnline = null)
                }
                .take(10)
                .toList()
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to fetch SOCKS5 proxies", e)
        emptyList()
    }
}

suspend fun checkProxyStatus(host: String, port: Int): Boolean = withContext(Dispatchers.IO) {
    try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), 1500)
            true
        }
    } catch (e: Exception) {
        false
    }
}

// ---------------- Compose UI ----------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxySelector() {
    val context = LocalContext.current
    val activity = context as? Activity

    var proxies by remember { mutableStateOf(listOf<Proxy>()) }
    var selectedProxy by remember { mutableStateOf<Proxy?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var refreshTrigger by remember { mutableStateOf(0) }
    var telegramOpenCount by remember { mutableStateOf(0) }

    val interstitialAdManager = remember {
        InterstitialAdManager(context.getString(R.string.admob_interstitial_ad_unit_id))
    }
    LaunchedEffect(Unit) { interstitialAdManager.load(context) }

    LaunchedEffect(refreshTrigger) {
        try {
            isLoading = true
            errorMessage = null

            val socks = fetchSocksProxies().take(5)
            val mtproto = fetchMtProtoProxies().take(5)

            proxies = mtproto + socks
            selectedProxy = proxies.firstOrNull()

            if (proxies.isEmpty()) {
                errorMessage = "No proxies found"
            } else {
                launch {
                    proxies.forEachIndexed { index, proxy ->
                        val isOnline = checkProxyStatus(proxy.server, proxy.port)
                        proxies = proxies.toMutableList().apply {
                            this[index] = this[index].copy(isOnline = isOnline)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load proxies", e)
            errorMessage = "Failed to fetch proxies"
            selectedProxy = null
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            refreshTrigger++
        }
    }

    fun openSelectedProxyInTelegram() {
        val p = selectedProxy ?: return
        val uri = when (p.type) {
            ProxyType.SOCKS5 -> "tg://socks?server=${p.server}&port=${p.port}"
            ProxyType.MTPROTO -> "tg://proxy?server=${p.server}&port=${p.port}&secret=${p.secret}&tag=My Proxy"
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply { setPackage("org.telegram.messenger") }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Telegram not installed", Toast.LENGTH_SHORT).show()
            val playIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=org.telegram.messenger"))
            context.startActivity(playIntent)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = { refreshTrigger++ }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        bottomBar = {
            BannerAd(adUnitId = stringResource(R.string.admob_banner_ad_unit_id))
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                errorMessage != null -> {
                    Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                        Text(errorMessage!!, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item { SectionHeader("MTProto Proxies") }
                        items(proxies.filter { it.type == ProxyType.MTPROTO }) { proxy ->
                            ProxyCard(proxy = proxy, isSelected = selectedProxy == proxy, onSelect = { selectedProxy = it })
                        }

                        item { SectionHeader("SOCKS5 Proxies") }
                        items(proxies.filter { it.type == ProxyType.SOCKS5 }) { proxy ->
                            ProxyCard(proxy = proxy, isSelected = selectedProxy == proxy, onSelect = { selectedProxy = it })
                        }
                    }

                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Button(
                            onClick = {
                                telegramOpenCount++
                                if (activity != null && telegramOpenCount % INTERSTITIAL_EVERY_N_OPENS == 0) {
                                    interstitialAdManager.showIfReady(activity) { openSelectedProxyInTelegram() }
                                } else {
                                    openSelectedProxyInTelegram()
                                }
                            },
                            enabled = selectedProxy != null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Open in Telegram")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = {
                                val shareIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "Download Telegram Proxy Opener APK:\nhttps://github.com/EngrMahmood/TelegramProxyOpener/releases"
                                    )
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share App"))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Share App")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
fun ProxyCard(proxy: Proxy, isSelected: Boolean, onSelect: (Proxy) -> Unit) {
    ElevatedCard(
        onClick = { onSelect(proxy) },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val typeIcon: ImageVector = if (proxy.type == ProxyType.MTPROTO) Icons.Filled.Security else Icons.Filled.Wifi
            Icon(typeIcon, contentDescription = proxy.type.name, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text("${proxy.server}:${proxy.port}", style = MaterialTheme.typography.bodyLarge)
                ProxyStatus(proxy.isOnline)
            }

            RadioButton(selected = isSelected, onClick = { onSelect(proxy) })
        }
    }
}

@Composable
fun ProxyStatus(isOnline: Boolean?) {
    when (isOnline) {
        true -> StatusRow(Icons.Filled.CheckCircle, "Available", Color(0xFF2E7D32))
        false -> StatusRow(Icons.Filled.Cancel, "Unavailable", Color(0xFFC62828))
        null -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp).padding(end = 6.dp), strokeWidth = 2.dp)
            Text("Checking...", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun StatusRow(icon: ImageVector, label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

@Preview(showBackground = true)
@Composable
fun ProxySelectorPreview() {
    TelegramProxyOpenerTheme {
        ProxySelector()
    }
}
