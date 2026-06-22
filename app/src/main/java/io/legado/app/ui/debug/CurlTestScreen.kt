package io.legado.app.ui.debug

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.theme.pageTopBarBackground
import io.legado.app.ui.theme.pageTopBarColors
import io.legado.app.help.http.StrResponse
import io.legado.app.help.http.newCallStrResponse
import io.legado.app.utils.sendToClip
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody

data class ParsedCurl(
    val url: String = "",
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    val errors: List<String> = emptyList()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurlTestScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val topBarColors = pageTopBarColors()
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val client = remember { OkHttpClient.Builder().build() }
    
    var curlCommand by remember { mutableStateOf("") }
    var parsedCurl by remember { mutableStateOf<ParsedCurl?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var responseCode by remember { mutableStateOf(0) }
    var responseMessage by remember { mutableStateOf("") }
    var responseTime by remember { mutableStateOf(0L) }
    var responseSize by remember { mutableStateOf(0L) }
    var responseHeaders by remember { mutableStateOf("") }
    var responseBody by remember { mutableStateOf("") }
    var showParsedDialog by remember { mutableStateOf(false) }
    var showResponseHeadersDialog by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    
    if (showParsedDialog && parsedCurl != null) {
        AlertDialog(
            onDismissRequest = { showParsedDialog = false },
            title = { Text(stringResource(R.string.debug_curl_parsed)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    if (parsedCurl!!.errors.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.debug_curl_warnings),
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                        parsedCurl!!.errors.forEach { error ->
                            Text(
                                text = "• $error",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    ParsedItem(stringResource(R.string.debug_curl_method), parsedCurl!!.method)
                    ParsedItem(stringResource(R.string.debug_curl_url), parsedCurl!!.url)
                    
                    if (parsedCurl!!.headers.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.debug_headers),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        parsedCurl!!.headers.forEach { (key, value) ->
                            Text(
                                text = "$key: $value",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    if (!parsedCurl!!.body.isNullOrEmpty()) {
                        Text(
                            text = stringResource(R.string.debug_body),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        SelectionContainer {
                            Text(
                                text = parsedCurl!!.body!!,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showParsedDialog = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        )
    }
    
    if (showResponseHeadersDialog && responseHeaders.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showResponseHeadersDialog = false },
            title = { Text(stringResource(R.string.debug_response_headers)) },
            text = {
                SelectionContainer {
                    Text(
                        text = responseHeaders,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showResponseHeadersDialog = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        )
    }
    
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                modifier = Modifier.pageTopBarBackground(topBarColors),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                    navigationIconContentColor = topBarColors.contentColor,
                    titleContentColor = topBarColors.contentColor,
                    actionIconContentColor = topBarColors.contentColor
                ),
                title = {
                    Text(
                        text = stringResource(R.string.debug_curl_test),
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Medium)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val clipText = clipboardManager.getText()?.text ?: ""
                        if (clipText.isNotBlank()) {
                            curlCommand = clipText
                        } else {
                            context.toastOnUi(R.string.debug_clipboard_empty)
                        }
                    }) {
                        Icon(Icons.Default.ContentPaste, contentDescription = "粘贴")
                    }
                    IconButton(onClick = {
                        curlCommand = ""
                        parsedCurl = null
                        responseCode = 0
                        responseMessage = ""
                        responseTime = 0
                        responseSize = 0
                        responseHeaders = ""
                        responseBody = ""
                    }) {
                        Icon(Icons.Default.Clear, contentDescription = "清空")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.debug_curl_input),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = curlCommand,
                        onValueChange = { curlCommand = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp),
                        placeholder = { Text(stringResource(R.string.debug_curl_hint)) }
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (curlCommand.isBlank()) {
                                    context.toastOnUi(R.string.debug_curl_empty)
                                    return@Button
                                }
                                
                                val parsed = parseCurlCommand(curlCommand)
                                parsedCurl = parsed
                                
                                if (parsed.url.isBlank()) {
                                    context.toastOnUi(R.string.debug_curl_parse_error)
                                    return@Button
                                }
                                
                                if (parsed.errors.isNotEmpty()) {
                                    showParsedDialog = true
                                }
                                
                                isLoading = true
                                responseBody = context.getString(R.string.debug_loading)
                                
                                val strError = context.getString(R.string.debug_error)
                                
                                coroutineScope.launch {
                                    try {
                                        val response = withContext(Dispatchers.IO) {
                                            executeCurlRequest(client, parsed)
                                        }
                                        
                                        responseCode = response.code()
                                        responseMessage = response.message()
                                        responseTime = response.raw.receivedResponseAtMillis - response.raw.sentRequestAtMillis
                                        responseSize = response.body?.length?.toLong() ?: 0L
                                        
                                        val headerBuilder = StringBuilder()
                                        response.raw.headers.forEach { (name, value) ->
                                            headerBuilder.append("$name: $value\n")
                                        }
                                        responseHeaders = headerBuilder.toString()
                                        
                                        responseBody = response.body ?: ""
                                    } catch (e: Exception) {
                                        responseBody = "$strError: ${e.message}"
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isLoading
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(stringResource(R.string.debug_execute))
                        }
                        
                        OutlinedButton(
                            onClick = {
                                if (curlCommand.isBlank()) {
                                    context.toastOnUi(R.string.debug_curl_empty)
                                    return@OutlinedButton
                                }
                                val parsed = parseCurlCommand(curlCommand)
                                parsedCurl = parsed
                                showParsedDialog = true
                            }
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.debug_curl_preview))
                        }
                    }
                }
            }
            
            if (responseCode > 0 || responseMessage.isNotEmpty()) {
                Card(
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.debug_response_info),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { showResponseHeadersDialog = true }) {
                                Icon(Icons.Default.Info, contentDescription = "查看响应头")
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            ResponseStatItem(
                                label = stringResource(R.string.debug_status_code),
                                value = responseCode.toString(),
                                valueColor = when {
                                    responseCode in 200..299 -> Color(0xFF4CAF50)
                                    responseCode in 300..399 -> Color(0xFFFF9800)
                                    else -> MaterialTheme.colorScheme.error
                                }
                            )
                            ResponseStatItem(
                                label = stringResource(R.string.debug_duration),
                                value = "${responseTime}ms"
                            )
                            ResponseStatItem(
                                label = stringResource(R.string.debug_size),
                                value = formatSize(responseSize)
                            )
                        }
                    }
                }
            }
            
            if (responseBody.isNotEmpty() && responseBody != context.getString(R.string.debug_loading)) {
                Card(
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.debug_response_body),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Box {
                                IconButton(onClick = { expanded = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "更多")
                                }
                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.debug_copy)) },
                                        onClick = {
                                            expanded = false
                                            context.sendToClip(responseBody)
                                        },
                                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.debug_format_json)) },
                                        onClick = {
                                            expanded = false
                                            responseBody = tryFormatJson(responseBody)
                                        },
                                        leadingIcon = { Icon(Icons.Default.FormatAlignLeft, contentDescription = null) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.debug_view_source)) },
                                        onClick = {
                                            expanded = false
                                            val intent = android.content.Intent(context, io.legado.app.ui.code.CodeEditActivity::class.java).apply {
                                                putExtra("text", responseBody)
                                                putExtra("title", context.getString(R.string.debug_response_body))
                                            }
                                            context.startActivity(intent)
                                        },
                                        leadingIcon = { Icon(Icons.Default.Code, contentDescription = null) }
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        SelectionContainer {
                            Text(
                                text = responseBody,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 300.dp)
                                    .horizontalScroll(rememberScrollState())
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ParsedItem(label: String, value: String) {
    Text(
        text = label,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
    Text(
        text = value,
        style = MaterialTheme.typography.bodySmall
    )
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun ResponseStatItem(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.primary
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
        else -> String.format("%.1f MB", bytes / (1024.0 * 1024))
    }
}

private fun tryFormatJson(json: String): String {
    return try {
        val jsonObject = org.json.JSONObject(json)
        jsonObject.toString(2)
    } catch (e: Exception) {
        try {
            val jsonArray = org.json.JSONArray(json)
            jsonArray.toString(2)
        } catch (e2: Exception) {
            json
        }
    }
}

private fun parseCurlCommand(curl: String): ParsedCurl {
    val errors = mutableListOf<String>()
    var url = ""
    var method = "GET"
    val headers = mutableMapOf<String, String>()
    var body: String? = null
    
    val normalizedCommand = curl
        .replace("\\\n", " ")
        .replace("\\\r\n", " ")
        .trim()
    
    val tokens = mutableListOf<String>()
    var currentToken = StringBuilder()
    var inSingleQuote = false
    var inDoubleQuote = false
    
    for (char in normalizedCommand) {
        when {
            char == '\'' && !inDoubleQuote -> inSingleQuote = !inSingleQuote
            char == '"' && !inSingleQuote -> inDoubleQuote = !inDoubleQuote
            char.isWhitespace() && !inSingleQuote && !inDoubleQuote -> {
                if (currentToken.isNotEmpty()) {
                    tokens.add(currentToken.toString())
                    currentToken = StringBuilder()
                }
            }
            else -> currentToken.append(char)
        }
    }
    if (currentToken.isNotEmpty()) {
        tokens.add(currentToken.toString())
    }
    
    var i = 0
    while (i < tokens.size) {
        val token = tokens[i]
        
        when {
            token == "curl" -> { }
            token.startsWith("http://") || token.startsWith("https://") -> {
                url = token
            }
            token == "-X" || token == "--request" -> {
                if (i + 1 < tokens.size) {
                    method = tokens[i + 1].uppercase()
                    i++
                }
            }
            token == "-H" || token == "--header" -> {
                if (i + 1 < tokens.size) {
                    val headerValue = tokens[i + 1]
                    val colonIndex = headerValue.indexOf(':')
                    if (colonIndex > 0) {
                        val key = headerValue.substring(0, colonIndex).trim()
                        val value = headerValue.substring(colonIndex + 1).trim()
                        headers[key] = value
                    }
                    i++
                }
            }
            token == "-d" || token == "--data" || token == "--data-raw" -> {
                if (i + 1 < tokens.size) {
                    body = tokens[i + 1]
                    if (method == "GET") method = "POST"
                    i++
                }
            }
            token == "--data-binary" -> {
                if (i + 1 < tokens.size) {
                    body = tokens[i + 1]
                    if (method == "GET") method = "POST"
                    i++
                }
            }
            token == "-u" || token == "--user" -> {
                if (i + 1 < tokens.size) {
                    headers["Authorization"] = "Basic " + android.util.Base64.encodeToString(
                        tokens[i + 1].toByteArray(),
                        android.util.Base64.NO_WRAP
                    )
                    i++
                }
            }
            token == "-A" || token == "--user-agent" -> {
                if (i + 1 < tokens.size) {
                    headers["User-Agent"] = tokens[i + 1]
                    i++
                }
            }
            token == "-e" || token == "--referer" -> {
                if (i + 1 < tokens.size) {
                    headers["Referer"] = tokens[i + 1]
                    i++
                }
            }
            token == "-b" || token == "--cookie" -> {
                if (i + 1 < tokens.size) {
                    headers["Cookie"] = tokens[i + 1]
                    i++
                }
            }
            token == "--compressed" -> {
                headers["Accept-Encoding"] = "gzip, deflate"
            }
            token.startsWith("-") && !token.startsWith("http") -> {
                if (i + 1 < tokens.size && !tokens[i + 1].startsWith("-")) {
                    i++
                }
            }
        }
        i++
    }
    
    if (url.isEmpty()) {
        errors.add("未找到URL")
    }
    
    return ParsedCurl(
        url = url,
        method = method,
        headers = headers,
        body = body,
        errors = errors
    )
}

private suspend fun executeCurlRequest(
    client: OkHttpClient,
    parsed: ParsedCurl
): StrResponse {
    return client.newCallStrResponse {
        url(parsed.url)
        
        when (parsed.method) {
            "GET" -> get()
            "POST" -> {
                val bodyText = parsed.body ?: ""
                val contentType = parsed.headers["Content-Type"] ?: "application/json; charset=UTF-8"
                val requestBody = bodyText.toRequestBody(contentType.toMediaType())
                post(requestBody)
            }
            "PUT" -> {
                val bodyText = parsed.body ?: ""
                val contentType = parsed.headers["Content-Type"] ?: "application/json; charset=UTF-8"
                val requestBody = bodyText.toRequestBody(contentType.toMediaType())
                put(requestBody)
            }
            "DELETE" -> {
                val bodyText = parsed.body
                if (bodyText != null) {
                    val contentType = parsed.headers["Content-Type"] ?: "application/json; charset=UTF-8"
                    val requestBody = bodyText.toRequestBody(contentType.toMediaType())
                    delete(requestBody)
                } else {
                    delete()
                }
            }
            "HEAD" -> head()
            "PATCH" -> {
                val bodyText = parsed.body ?: ""
                val contentType = parsed.headers["Content-Type"] ?: "application/json; charset=UTF-8"
                val requestBody = bodyText.toRequestBody(contentType.toMediaType())
                patch(requestBody)
            }
            else -> get()
        }
        
        parsed.headers.forEach { (key, value) ->
            addHeader(key, value)
        }
    }
}
