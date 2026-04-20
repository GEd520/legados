package io.legado.app.ui.debug

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.databinding.ActivityHttpDebugBinding
import io.legado.app.help.http.StrResponse
import io.legado.app.help.http.newCallStrResponse
import io.legado.app.help.http.text
import io.legado.app.utils.sendToClip
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class HttpDebugActivity : BaseActivity<ActivityHttpDebugBinding>() {

    override val binding by lazy { ActivityHttpDebugBinding.inflate(layoutInflater) }

    private val methods = listOf("GET", "POST")
    private val client = OkHttpClient.Builder().build()

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        initSpinner()
        initClick()
    }

    private fun initSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, methods)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerMethod.adapter = adapter
        binding.spinnerMethod.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                binding.tilBody.visibility = if (position == 1) View.VISIBLE else View.GONE
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun initClick() {
        binding.btnSend.setOnClickListener {
            sendRequest()
        }
        binding.btnCopyResponse.setOnClickListener {
            val result = binding.tvResponse.text.toString()
            if (result.isNotEmpty()) {
                sendToClip(result)
            }
        }
        binding.btnCopyHeaders.setOnClickListener {
            val headers = binding.tvHeaders.text.toString()
            if (headers.isNotEmpty()) {
                sendToClip(headers)
            }
        }
        binding.btnClear.setOnClickListener {
            binding.etUrl.setText("")
            binding.etHeaders.setText("")
            binding.etBody.setText("")
            binding.tvResponse.text = ""
            binding.tvHeaders.text = ""
        }
    }

    private fun sendRequest() {
        val url = binding.etUrl.text.toString().trim()
        if (url.isEmpty()) {
            toastOnUi(R.string.debug_url_empty)
            return
        }

        binding.btnSend.isEnabled = false
        binding.tvResponse.text = getString(R.string.debug_loading)
        binding.tvHeaders.text = ""

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    doRequest(url)
                }
                showResponse(response)
            } catch (e: Exception) {
                binding.tvResponse.text = "错误: ${e.message}"
            } finally {
                binding.btnSend.isEnabled = true
            }
        }
    }

    private suspend fun doRequest(url: String): StrResponse {
        val methodIndex = binding.spinnerMethod.selectedItemPosition
        val headersText = binding.etHeaders.text.toString()
        val bodyText = binding.etBody.text.toString()

        return client.newCallStrResponse {
            url(url)
            when (methodIndex) {
                0 -> get()
                1 -> {
                    if (bodyText.isNotEmpty()) {
                        val requestBody = bodyText.toRequestBody("application/json; charset=UTF-8".toMediaType())
                        post(requestBody)
                    }
                }
            }
            if (headersText.isNotEmpty()) {
                parseHeaders(headersText).forEach { (key, value) ->
                    addHeader(key, value)
                }
            }
        }
    }

    private fun parseHeaders(headersText: String): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        headersText.lines().forEach { line ->
            val parts = line.split(":", limit = 2)
            if (parts.size == 2) {
                headers[parts[0].trim()] = parts[1].trim()
            }
        }
        return headers
    }

    private fun showResponse(response: StrResponse) {
        val sb = StringBuilder()
        sb.append("状态码: ${response.code()}\n")
        sb.append("消息: ${response.message()}\n")
        sb.append("耗时: ${response.raw.receivedResponseAtMillis - response.raw.sentRequestAtMillis}ms\n")
        binding.tvHeaders.text = sb.toString()

        val body = response.body
        binding.tvResponse.text = body
    }
}
