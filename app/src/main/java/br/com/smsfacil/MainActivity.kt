package br.com.smsfacil

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.SmsManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class SimChoice(val subscriptionId: Int, val slot: Int, val label: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SmsFacilScreen() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsFacilScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var numbersText by remember { mutableStateOf("") }
    var messageText by remember { mutableStateOf("") }
    var sims by remember { mutableStateOf(emptyList<SimChoice>()) }
    var selectedSubId by remember { mutableIntStateOf(-1) }
    var status by remember { mutableStateOf("Pronto. Cole os números, um por linha.") }
    var sending by remember { mutableStateOf(false) }
    var consentConfirmed by remember { mutableStateOf(false) }
    var confirmSend by remember { mutableStateOf(false) }
    var permissionRefresh by remember { mutableIntStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionRefresh++ }

    fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun refreshSims() {
        if (!hasPermission(Manifest.permission.READ_PHONE_STATE)) {
            status = "Conceda a permissão de telefone para identificar os chips."
            return
        }
        try {
            val manager = context.getSystemService(SubscriptionManager::class.java)
            val active = manager.activeSubscriptionInfoList.orEmpty()
            sims = active.map { info: SubscriptionInfo ->
                val slotName = if (info.simSlotIndex == 0) "SIM 1" else "SIM ${info.simSlotIndex + 1}"
                val carrier = info.carrierName?.toString()?.takeIf { it.isNotBlank() } ?: "Operadora não informada"
                SimChoice(info.subscriptionId, info.simSlotIndex, "$slotName — $carrier")
            }
            if (sims.none { it.subscriptionId == selectedSubId }) {
                selectedSubId = sims.firstOrNull()?.subscriptionId ?: -1
            }
            status = if (sims.isEmpty()) "Nenhum chip ativo detectado." else "Chips identificados: ${sims.size}"
        } catch (e: SecurityException) {
            status = "Não foi possível acessar os dados dos chips. Verifique as permissões."
        }
    }

    LaunchedEffect(permissionRefresh) {
        if (hasPermission(Manifest.permission.READ_PHONE_STATE)) refreshSims()
    }

    if (confirmSend) {
        AlertDialog(
            onDismissRequest = { confirmSend = false },
            title = { Text("Confirmar envio") },
            text = {
                Text("Você está prestes a iniciar o envio para ${numbersText.lines().map { it.trim() }.count { it.isNotBlank() }} linhas. Envie apenas para pessoas que autorizaram receber mensagens.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmSend = false
                    if (!consentConfirmed) {
                        status = "Confirme que todos os destinatários autorizaram o recebimento."
                        return@TextButton
                    }
                    if (!hasPermission(Manifest.permission.SEND_SMS)) {
                        permissionLauncher.launch(arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_PHONE_STATE))
                        status = "Conceda as permissões e toque em Enviar novamente."
                        return@TextButton
                    }
                    val numbers = numbersText.lines().map { it.trim() }.filter { it.isNotBlank() }.distinct()
                    if (numbers.isEmpty() || messageText.isBlank() || selectedSubId < 0) {
                        status = "Informe números, mensagem e selecione um chip ativo."
                        return@TextButton
                    }
                    sending = true
                    scope.launch {
                        var sent = 0
                        var failed = 0
                        for ((index, number) in numbers.withIndex()) {
                            if (!sending) break
                            try {
                                val smsManager = if (android.os.Build.VERSION.SDK_INT >= 31) {
                                    context.getSystemService(SmsManager::class.java)
                                        .createForSubscriptionId(selectedSubId)
                                } else {
                                    @Suppress("DEPRECATION")
                                    SmsManager.getSmsManagerForSubscriptionId(selectedSubId)
                                }
                                val parts = smsManager.divideMessage(messageText)
                                smsManager.sendMultipartTextMessage(number, null, parts, null, null)
                                sent++
                                status = "Solicitados: $sent/${numbers.size} • Falhas locais: $failed"
                            } catch (e: Exception) {
                                failed++
                                status = "Falha ao solicitar envio para $number. Continuando fila…"
                            }
                            // A small gap helps keep the UI responsive; it does not bypass carrier limits.
                            delay(1500)
                        }
                        sending = false
                        status = "Fila finalizada. Solicitações iniciadas: $sent • Falhas locais: $failed. Isso não confirma entrega."
                    }
                }) { Text("Confirmar e enviar") }
            },
            dismissButton = { TextButton(onClick = { confirmSend = false }) { Text("Cancelar") } }
        )
    }

    MaterialTheme {
        Scaffold(
            topBar = { TopAppBar(title = { Text("SMS Fácil") }) }
        ) { padding ->
            Column(
                modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Chip para envio", style = MaterialTheme.typography.titleMedium)
                Button(onClick = {
                    permissionLauncher.launch(arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.SEND_SMS))
                }) { Text("Solicitar permissões") }
                OutlinedButton(onClick = { refreshSims() }) { Text("Identificar / atualizar chips") }
                if (sims.isEmpty()) {
                    Text("Nenhum SIM identificado ainda. Verifique se há chip ativo e conceda a permissão.")
                } else {
                    sims.forEach { sim ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RadioButton(selected = selectedSubId == sim.subscriptionId, onClick = { selectedSubId = sim.subscriptionId })
                            Column {
                                Text(sim.label)
                                Text("ID da assinatura: ${sim.subscriptionId}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                HorizontalDivider()
                Text("Lista de números", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = numbersText,
                    onValueChange = { numbersText = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                    label = { Text("Cole um número por linha") },
                    placeholder = { Text("+5511999999999\n+5521999999999") }
                )
                Text("Números únicos: ${numbersText.lines().map { it.trim() }.filter { it.isNotBlank() }.distinct().size}")

                Text("Mensagem", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    label = { Text("Texto do SMS") }
                )

                Row {
                    Checkbox(checked = consentConfirmed, onCheckedChange = { consentConfirmed = it })
                    Text("Confirmo que os destinatários consentiram em receber estas mensagens.")
                }

                Text("Não há um teto diário fixo no app. A operadora, o plano e o Android podem limitar ou bloquear envios.", style = MaterialTheme.typography.bodySmall)

                if (!sending) {
                    Button(
                        onClick = {
                            if (!consentConfirmed) status = "Confirme o consentimento antes de enviar."
                            else if (messageText.isBlank() || numbersText.isBlank()) status = "Preencha a lista e a mensagem."
                            else confirmSend = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Iniciar envios") }
                } else {
                    Button(onClick = { sending = false; status = "Solicitação de pausa registrada." }, modifier = Modifier.fillMaxWidth()) {
                        Text("Pausar fila")
                    }
                }
                OutlinedButton(
                    onClick = { sending = false; status = "Fila parada pelo usuário." },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Parar") }

                Text("Status", style = MaterialTheme.typography.titleMedium)
                Text(status)
                Text("Aviso: o status indica apenas solicitações locais. A entrega depende da rede e não é confirmada por esta versão.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
