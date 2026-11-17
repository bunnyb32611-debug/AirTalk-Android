package com.airtalk.app

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.util.UUID
import kotlin.concurrent.thread

private val Ink = Color(0xFF0B1220)
private val Panel = Color(0xFF142238)
private val Teal = Color(0xFF19C6B4)
private val Muted = Color(0xFF9BB0C5)
private val SERVICE_ID = UUID.fromString("7b7e0001-7b7e-4b7e-9b7e-7b7e00000001")

class MainActivity : ComponentActivity() {
    private val adapter: BluetoothAdapter? by lazy { BluetoothAdapter.getDefaultAdapter() }
    private val found = mutableStateListOf<BluetoothDevice>()
    private var socket: BluetoothSocket? = null
    private var writer: PrintWriter? = null
    private var onIncoming: ((String) -> Unit)? = null
    private var onStatus: ((String) -> Unit)? = null

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        // The chat screen may start before Android finishes showing the permission prompt.
        // Retry the Bluetooth listener only after the user grants the required permissions.
        if (result.isNotEmpty() && result.values.all { it }) {
            startServer()
        } else {
            runOnUiThread { onStatus?.invoke("Bluetooth permission is required to receive messages.") }
        }
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothDevice.ACTION_FOUND) {
                val device = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) else @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                if (device != null && found.none { it.address == device.address }) found.add(device)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AirTalkScreen(
                devices = found,
                onScan = { scanDevices() },
                onConnect = { device -> connect(device) },
                onSend = { message -> sendMessage(message) },
                onListen = { incoming, status -> onIncoming = incoming; onStatus = status; startServer() },
                onEnableBluetooth = { startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
            )
        }
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        if (Build.VERSION.SDK_INT >= 33) {
            // Bluetooth discovery broadcasts are sent by the Android system.
            registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(receiver, filter)
        }
        askPermissions()
    }

    private fun askPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (permissions.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) permissionLauncher.launch(permissions)
    }

    @Suppress("MissingPermission")
    private fun scanDevices() {
        if (adapter == null) return
        found.clear()
        try {
            if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) { askPermissions(); return }
            adapter?.cancelDiscovery()
            adapter?.startDiscovery()
        } catch (_: SecurityException) { }
    }

    @Suppress("MissingPermission")
    private fun connect(device: BluetoothDevice) {
        thread {
            try {
                adapter?.cancelDiscovery()
                val s = device.createRfcommSocketToServiceRecord(SERVICE_ID)
                s.connect(); socket = s
                writer = PrintWriter(s.outputStream, true)
                runOnUiThread { onStatus?.invoke("Connected to ${device.name ?: "device"}") }
                listen(s)
            } catch (e: Exception) { runOnUiThread { onStatus?.invoke("Connection failed. Pair both phones first.") } }
        }
    }

    @Suppress("MissingPermission")
    private fun startServer() {
        val requiredPermission = if (Build.VERSION.SDK_INT >= 31) {
            Manifest.permission.BLUETOOTH_CONNECT
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (ContextCompat.checkSelfPermission(this, requiredPermission) != PackageManager.PERMISSION_GRANTED) {
            runOnUiThread { onStatus?.invoke("Allow Bluetooth permission to start receiving messages.") }
            return
        }
        thread {
            try {
                val server = adapter?.listenUsingRfcommWithServiceRecord("AirTalk", SERVICE_ID) ?: return@thread
                val s = server.accept(); socket = s; writer = PrintWriter(s.outputStream, true)
                runOnUiThread { onStatus?.invoke("Connected") }
                listen(s)
            } catch (_: Exception) { }
        }
    }

    private fun listen(s: BluetoothSocket) {
        thread {
            try {
                val reader = BufferedReader(InputStreamReader(s.inputStream))
                while (true) { val line = reader.readLine() ?: break; runOnUiThread { onIncoming?.invoke(line) } }
            } catch (_: Exception) { runOnUiThread { onStatus?.invoke("Disconnected") } }
        }
    }

    private fun sendMessage(message: String) { thread { try { writer?.println(message) } catch (_: Exception) { } } }

    override fun onDestroy() {
        try { unregisterReceiver(receiver) } catch (_: Exception) { }
        try { socket?.close() } catch (_: Exception) { }
        super.onDestroy()
    }
}

@Composable
private fun AirTalkScreen(devices: List<BluetoothDevice>, onScan: () -> Unit, onConnect: (BluetoothDevice) -> Unit, onSend: (String) -> Unit, onListen: ((String) -> Unit, (String) -> Unit) -> Unit, onEnableBluetooth: () -> Unit) {
    var tab by remember { mutableStateOf(0) }
    var status by remember { mutableStateOf("Not connected") }
    var message by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<Pair<String, Boolean>>() }
    LaunchedEffect(Unit) { onListen({ messages.add(it to false) }, { status = it }) }
    MaterialTheme(colorScheme = darkColorScheme(primary = Teal, background = Ink, surface = Panel)) {
        Scaffold(containerColor = Ink, bottomBar = {
            NavigationBar(containerColor = Panel) {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Default.Send, null) }, label = { Text("Chats") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Default.Bluetooth, null) }, label = { Text("Nearby") })
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).background(Teal, RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Wifi, null, tint = Ink) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { Text("AirTalk", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold); Text("Offline messenger", color = Muted, fontSize = 12.sp) }
                    Box(Modifier.size(9.dp).background(if (status.contains("Connected")) Teal else Muted, CircleShape))
                }
                Spacer(Modifier.height(18.dp))
                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text("CONNECTION STATUS", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(5.dp)); Text(status, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("Bluetooth works without mobile data or Wi-Fi internet.", color = Muted, fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(16.dp))
                if (tab == 0) {
                    Text("Your conversation", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (messages.isEmpty()) item { Text("No messages yet. Connect to a nearby phone to start chatting.", color = Muted, fontSize = 13.sp) }
                        items(messages) { (body, mine) -> Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) { Surface(color = if (mine) Teal else Panel, shape = RoundedCornerShape(15.dp)) { Text(body, Modifier.padding(12.dp), color = if (mine) Ink else Color.White) } } }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = message, onValueChange = { message = it }, modifier = Modifier.weight(1f), placeholder = { Text("Write a message…") }, singleLine = true)
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = { if (message.isNotBlank()) { val m = message; messages.add(m to true); onSend(m); message = "" } }) { Icon(Icons.Default.Send, null, tint = Teal) }
                    }
                } else {
                    Text("Nearby devices", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(5.dp)); Text("Pair phones in Android Bluetooth settings, then scan.", color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onEnableBluetooth, colors = ButtonDefaults.buttonColors(containerColor = Panel)) { Text("Enable Bluetooth") }
                        Button(onClick = onScan) { Text("Scan") }
                    }
                    Spacer(Modifier.height(12.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(devices) { device ->
                            Card(Modifier.fillMaxWidth().clickable { onConnect(device) }, colors = CardDefaults.cardColors(containerColor = Panel)) {
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Bluetooth, null, tint = Teal); Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) { Text(device.name ?: "Unknown device", color = Color.White); Text(device.address, color = Muted, fontSize = 10.sp) }
                                    Text("Connect", color = Teal, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                        if (devices.isEmpty()) item { Text("No devices found yet.", color = Muted, fontSize = 13.sp) }
                    }
                }
            }
        }
    }
}
