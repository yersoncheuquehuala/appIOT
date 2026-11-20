package com.yerson.appiot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    appIoT()
                }
            }
        }
    }
}

// pedir los datos wifi
suspend fun enviarPeticion(urlTexto: String): String? = withContext(Dispatchers.IO) {
    try {
        val conexion = URL(urlTexto).openConnection() as HttpURLConnection
        conexion.connectTimeout = 2000
        conexion.readTimeout = 2000
        conexion.requestMethod = "GET"
        if (conexion.responseCode == 200) {
            conexion.inputStream.bufferedReader().readText()
        } else null
    } catch (e: Exception) {
        null
    }
}

@Composable
fun appIoT() {
    val scope = rememberCoroutineScope()

    // estados de la app
    var pestanaAdmin by remember { mutableStateOf(false) }
    var ip by remember { mutableStateOf("127.0.0.1") }
    var token by remember { mutableStateOf("") }
    var conectado by remember { mutableStateOf(false) }

    // datos de los sensores
    var alcohol by remember { mutableStateOf("0") }
    var temp by remember { mutableStateOf("0.0") }
    var hum by remember { mutableStateOf("0.0") }
    var modo by remember { mutableStateOf("A") } // A = Auto, M = Manual
    var extractorOn by remember { mutableStateOf(false) }
    var umbralActual by remember { mutableStateOf("400") }
    var inputUmbral by remember { mutableStateOf("400") }

    // bucle cada 2 segundos
    LaunchedEffect(ip, token) {
        while (true) {
            val respuesta = enviarPeticion("http://$ip/status?token=$token")
            if (respuesta != null) {
                val datos = respuesta.trim().split("|")
                if (datos.size >= 6) {
                    alcohol = datos[0]
                    temp = datos[1]
                    hum = datos[2]
                    modo = datos[3]
                    extractorOn = (datos[4] == "1")
                    umbralActual = datos[5]
                    conectado = true
                }

                if (modo == "A" && extractorOn == false && alcohol.toFloat() >= umbralActual.toFloat())
                {
                    extractorOn = true
                }
                else (modo == "A" && extractorOn == false && alcohol.toFloat() < umbralActual.toFloat())
                {
                    extractorOn = false
                }

            } else {
                conectado = false
            }
            delay(2000L)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Barra superior: Estado de red y cambio de vista (Operador / Admin)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (conectado) "En línea" else "Desconectado",
                color = if (conectado) Color.Green else Color.Red,
                fontWeight = FontWeight.Bold
            )
            TextButton(onClick = { pestanaAdmin = !pestanaAdmin }) {
                Text(if (pestanaAdmin) "Ir a Operador" else "Ir a Admin")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (!pestanaAdmin) {
            // vista del operador
            Text("Monitor de Alcohol (MQ-3)", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Nivel de Alcohol: $alcohol (Límite: $umbralActual)", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Temperatura: $temp °C  |  Humedad: $hum %")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Extractor: ${if (extractorOn) "ENCENDIDO" else "APAGADO"}")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text("Modo de Operación: ${if (modo == "A") "Automático" else "Manual"}")
            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    modo = "A"
                    scope.launch { enviarPeticion("http://$ip/control?token=$token&modo=A&ext=${if (extractorOn) 1 else 0}") }
                }) {
                    Text("Modo Auto")
                }
                OutlinedButton(onClick = {
                    modo = "M"
                    scope.launch { enviarPeticion("http://$ip/control?token=$token&modo=M&ext=${if (extractorOn) 1 else 0}") }
                }) {
                    Text("Modo Manual")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            if (modo == "M") {
                Button(
                    onClick = {

                        val nuevoEstado = !extractorOn
                        extractorOn = nuevoEstado
                        modo = "M"
                        scope.launch { enviarPeticion("http://$ip/control?token=$token&modo=M&ext=${if (nuevoEstado) 1 else 0}") }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (extractorOn) "Apagar Extractor" else "Encender Extractor")
                }
            }
        } else {
            // vista admin
            Text("Ajustes de Administrador", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = ip,
                onValueChange = { ip = it },
                label = { Text("IP del Módulo Wi-Fi") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text("Token") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = inputUmbral,
                onValueChange = { inputUmbral = it },
                label = { Text("Nuevo Umbral Sensor MQ-3") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    umbralActual = inputUmbral
                    scope.launch { enviarPeticion("http://$ip/config?token=$token&umbral=$inputUmbral") }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Guardar Umbral")
            }
        }
    }
}