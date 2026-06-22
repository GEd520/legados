package io.legado.app.ui.config

import android.graphics.Color
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.R
import io.legado.app.ui.theme.pageTopBarBackground
import io.legado.app.ui.theme.pageTopBarColors
import io.legado.app.help.DefaultData
import io.legado.app.help.config.CoverHtmlTemplateConfig
import io.legado.app.constant.EventBus
import io.legado.app.model.BookCover
import io.legado.app.ui.widget.code.CodeView
import io.legado.app.ui.widget.code.addHtmlPattern
import io.legado.app.ui.widget.code.addJsPattern
import io.legado.app.ui.widget.image.CoverImageView
import io.legado.app.utils.postEvent
import io.legado.app.utils.toastOnUi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoverHtmlCodeScreen(
    template: CoverHtmlTemplateConfig.Template?,
    isNewTemplate: Boolean,
    onBackClick: () -> Unit,
    onShowTemplateList: () -> Unit
) {
    val context = LocalContext.current
    val topBarColors = pageTopBarColors()
    val containerColor = coverHtmlCardContainerColor()
    
    var templateName by remember { mutableStateOf("") }
    var htmlCode by remember { mutableStateOf("") }
    var bookName by remember { mutableStateOf("示例书名") }
    var author by remember { mutableStateOf("示例作者") }
    
    var originalName by remember { mutableStateOf("") }
    var originalHtmlCode by remember { mutableStateOf("") }
    
    var currentTemplate by remember { mutableStateOf(template) }
    var currentIsNewTemplate by remember { mutableStateOf(isNewTemplate) }
    
    var showSaveDialog by remember { mutableStateOf(false) }
    var pendingTemplateSwitch by remember { mutableStateOf<CoverHtmlTemplateConfig.Template?>(null) }
    
    var webView by remember { mutableStateOf<WebView?>(null) }
    var codeView by remember { mutableStateOf<CodeView?>(null) }
    var webViewReady by remember { mutableStateOf(false) }
    
    LaunchedEffect(currentTemplate, currentIsNewTemplate) {
        if (currentIsNewTemplate) {
            templateName = ""
            htmlCode = DefaultData.coverHtmlTemplate
            originalName = ""
            originalHtmlCode = DefaultData.coverHtmlTemplate
        } else {
            val t = currentTemplate ?: CoverHtmlTemplateConfig.getSelectedTemplate()
            templateName = t.name
            htmlCode = t.htmlCode
            originalName = t.name
            originalHtmlCode = t.htmlCode
            currentTemplate = t
        }
    }
    
    fun previewCover() {
        val renderedHtml = BookCover.renderHtmlTemplate(htmlCode, bookName, author)
        webView?.loadDataWithBaseURL(
            null,
            renderedHtml,
            "text/html",
            "UTF-8",
            null
        )
    }
    
    fun doSaveTemplate() {
        if (htmlCode.isNotBlank()) {
            if (currentIsNewTemplate) {
                val newTemplate = CoverHtmlTemplateConfig.Template(
                    id = CoverHtmlTemplateConfig.generateId(),
                    name = templateName.ifEmpty { "未命名模板" },
                    htmlCode = htmlCode,
                    isSelected = true
                )
                CoverHtmlTemplateConfig.addTemplate(newTemplate)
                CoverHtmlTemplateConfig.setSelectedTemplate(newTemplate.id)
                currentTemplate = newTemplate
                currentIsNewTemplate = false
            } else {
                val existingTemplate = currentTemplate?.copy(
                    name = templateName.ifEmpty { "未命名模板" },
                    htmlCode = htmlCode
                )
                if (existingTemplate != null) {
                    CoverHtmlTemplateConfig.updateTemplate(existingTemplate)
                    currentTemplate = existingTemplate
                }
            }
            originalName = templateName
            originalHtmlCode = htmlCode
            CoverImageView.clearHtmlCoverCache()
            postEvent(EventBus.COVER_HTML_TEMPLATE_CHANGED, "")
            postEvent(EventBus.BOOKSHELF_REFRESH, "")
        }
    }
    
    if (showSaveDialog && pendingTemplateSwitch != null) {
        AlertDialog(
            onDismissRequest = { 
                showSaveDialog = false
                pendingTemplateSwitch = null
            },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(stringResource(R.string.cover_html_save_changes)) },
            text = { Text(stringResource(R.string.cover_html_unsaved_hint)) },
            confirmButton = {
                TextButton(onClick = {
                    doSaveTemplate()
                    currentTemplate = pendingTemplateSwitch
                    currentIsNewTemplate = false
                    showSaveDialog = false
                    pendingTemplateSwitch = null
                }) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    currentTemplate = pendingTemplateSwitch
                    currentIsNewTemplate = false
                    showSaveDialog = false
                    pendingTemplateSwitch = null
                }) {
                    Text(stringResource(R.string.discard))
                }
            }
        )
    }
    
    LaunchedEffect(htmlCode, bookName, author, webViewReady) {
        if (webViewReady && htmlCode.isNotBlank()) {
            previewCover()
        }
    }
    
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                modifier = Modifier.pageTopBarBackground(topBarColors),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    scrolledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    navigationIconContentColor = topBarColors.contentColor,
                    titleContentColor = topBarColors.contentColor,
                    actionIconContentColor = topBarColors.contentColor
                ),
                title = {
                    Text(
                        text = stringResource(R.string.cover_html_code),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                },
                actions = {
                    IconButton(onClick = onShowTemplateList) {
                        Icon(Icons.Default.Sort, contentDescription = "模板列表")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                OutlinedTextField(
                    value = templateName,
                    onValueChange = { templateName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.cover_html_template_name)) },
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = stringResource(R.string.cover_html_preview),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = bookName,
                        onValueChange = { bookName = it },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.cover_html_book_name)) },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = author,
                        onValueChange = { author = it },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.cover_html_author)) },
                        singleLine = true
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                TextButton(onClick = { previewCover() }) {
                    Text(stringResource(R.string.cover_html_preview))
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Box(
                    modifier = Modifier
                        .size(180.dp, 270.dp)
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(4.dp))
                        .background(androidx.compose.ui.graphics.Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx.applicationContext).apply {
                                settings.javaScriptEnabled = true
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                settings.setSupportZoom(false)
                                settings.displayZoomControls = false
                                setBackgroundColor(Color.WHITE)
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                    }
                                }
                                webView = this
                                webViewReady = true
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = stringResource(R.string.html_code),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp),
                    color = containerColor,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            CodeView(ctx).apply {
                                addHtmlPattern()
                                addJsPattern()
                                setPadding(16, 16, 16, 16)
                                codeView = this
                            }
                        },
                        update = { view ->
                            if (view.text.toString() != htmlCode) {
                                view.setText(htmlCode)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp)
                    )
                }
            }
            
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = {
                        templateName = ""
                        htmlCode = DefaultData.coverHtmlTemplate
                        codeView?.setText(DefaultData.coverHtmlTemplate)
                    }) {
                        Text(stringResource(R.string.btn_default_s))
                    }
                    
                    Row {
                        TextButton(onClick = onBackClick) {
                            Text(
                                stringResource(R.string.cancel),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = {
                            if (htmlCode.isBlank()) {
                                context.toastOnUi(R.string.cover_html_code_empty)
                            } else {
                                doSaveTemplate()
                                onBackClick()
                            }
                        }) {
                            Text(stringResource(R.string.ok))
                        }
                    }
                }
            }
        }
    }
}
