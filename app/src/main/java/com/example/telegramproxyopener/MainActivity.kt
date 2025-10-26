package com.example.telegramproxyopener

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.telegramproxyopener.ui.theme.TelegramProxyOpenerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background



// ---------------- Data Models ----------------
enum class ProxyType { SOCKS5, MTPROTO }

data class Proxy(
    val server: String,
    val port: Int,
    val secret: String? = null,
    val type: ProxyType,
    val country: String? = null,
    val provider: String? = null,
    val ping: Int? = null
)


// ---------------- MainActivity ----------------
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
suspend fun fetchMtProtoProxies(): List<Proxy> = withContext(Dispatchers.IO) {
    val client = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .build()
    val request = Request.Builder()
        .url("https://raw.githubusercontent.com/hookzof/socks5_list/master/tg/mtproto.json")
        .build()

    try {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyList<Proxy>()
            val bodyString = response.body()?.string() ?: return@withContext emptyList<Proxy>()
            val jsonArray = JSONArray(bodyString)
            val list = (0 until jsonArray.length()).map { i ->
                val json = jsonArray.getJSONObject(i)
                Proxy(
                    server = json.getString("host"),
                    port = json.getInt("port"),
                    secret = json.getString("secret"),
                    type = ProxyType.MTPROTO,
                    country = json.optString("country", "Unknown"),
                    provider = json.optString("provider", "Unknown"),
                    ping = json.optInt("ping", 9999)
                )
            }.sortedBy { it.ping }
            list.take(5)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}

// ---------------- Fetch SOCKS5 Proxies ----------------
suspend fun fetchSocksProxies(): List<Proxy> = withContext(Dispatchers.IO) {
    val client = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .build()
    val request = Request.Builder()
        .url("https://raw.githubusercontent.com/hookzof/socks5_list/master/tg/socks.json")
        .build()

    try {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyList<Proxy>()
            val bodyString = response.body()?.string() ?: return@withContext emptyList<Proxy>()
            val jsonArray = JSONArray(bodyString)
            val list = (0 until jsonArray.length()).map { i ->
                val json = jsonArray.getJSONObject(i)
                Proxy(
                    server = json.getString("ip"),
                    port = json.getInt("port"),
                    secret = null,
                    type = ProxyType.SOCKS5,
                    country = json.optString("country", "Unknown"),
                    provider = json.optString("provider", "Unknown"),
                    ping = json.optInt("ping", 9999)
                )
            }.sortedBy { it.ping }
            list.take(5)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}

// ---------------- Compose UI ----------------
@Composable
fun ProxySelector() {
    val context = LocalContext.current

    // --- State variables ---
    var proxies by remember { mutableStateOf(listOf<Proxy>()) }
    var selectedProxy by remember { mutableStateOf<Proxy?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Compute fastest proxy based on ping
    val fastestProxy by remember {
        derivedStateOf {
            proxies.filter { it.ping != null }.minByOrNull { it.ping!! }
        }
    }

    // --- Load proxies on first composition ---
    LaunchedEffect(Unit) {
        try {
            isLoading = true
            errorMessage = null

            val socks = fetchSocksProxies().take(5)       // Take top 5 SOCKS5
            val mtproto = fetchMtProtoProxies().take(5)   // Take top 5 MTProto

            proxies = mtproto + socks
            selectedProxy = fastestProxy ?: proxies.firstOrNull()

            if (proxies.isEmpty()) errorMessage = "No proxies found"
        } catch (e: Exception) {
            e.printStackTrace()
            errorMessage = "Failed to fetch proxies"
            selectedProxy = null
        } finally {
            isLoading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Select a proxy:", style = MaterialTheme.typography.titleMedium)
        }

        when {
            isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            errorMessage != null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(errorMessage!!)
                }
            }
            else -> {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    item { Text("MTProto Proxies", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)) }
                    items(proxies.filter { it.type == ProxyType.MTPROTO }) { proxy ->
                        ProxyRow(
                            proxy = proxy,
                            selectedProxy = selectedProxy,
                            fastestProxy = fastestProxy,
                            onSelect = { selectedProxy = it }
                        )
                    }

                    item { Text("SOCKS5 Proxies", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)) }
                    items(proxies.filter { it.type == ProxyType.SOCKS5 }) { proxy ->
                        ProxyRow(
                            proxy = proxy,
                            selectedProxy = selectedProxy,
                            fastestProxy = fastestProxy,
                            onSelect = { selectedProxy = it }
                        )
                    }
                }

                Button(
                    onClick = {
                        selectedProxy?.let { p ->
                            val uri = when (p.type) {
                                ProxyType.SOCKS5 -> "tg://socks?server=${p.server}&port=${p.port}"
                                ProxyType.MTPROTO -> "tg://proxy?server=${p.server}&port=${p.port}&secret=${p.secret}&tag=My Proxy"
                            }
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply { setPackage("org.telegram.messenger") }
                            try { context.startActivity(intent) } catch (e: Exception) {
                                Toast.makeText(context, "Telegram not installed", Toast.LENGTH_SHORT).show()
                                val playIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=org.telegram.messenger"))
                                context.startActivity(playIntent)
                            }
                        }
                    },
                    enabled = selectedProxy != null,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Text("Open in Telegram")
                }
            }
        }
    }
}

// ---------------- Single Proxy Row ----------------
@Composable
fun ProxyRow(proxy: Proxy, selectedProxy: Proxy?, fastestProxy: Proxy?, onSelect: (Proxy) -> Unit) {
    val backgroundColor = if (proxy == fastestProxy) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.background

    Column(modifier = Modifier
        .fillMaxWidth()
        .background(backgroundColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelect(proxy) }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selectedProxy == proxy,
                onClick = { onSelect(proxy) }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text("${proxy.server}:${proxy.port} [${proxy.type}]")
                proxy.country?.let { Text("Country: $it", style = MaterialTheme.typography.bodySmall) }
                proxy.provider?.let { Text("Provider: $it", style = MaterialTheme.typography.bodySmall) }
                proxy.ping?.let { Text("Ping: ${it}ms", style = MaterialTheme.typography.bodySmall) }
            }
        }
        Divider()
    }
}


@Preview(showBackground = true)
@Composable
fun ProxySelectorPreview() {
    TelegramProxyOpenerTheme {
        ProxySelector()
    }
}
