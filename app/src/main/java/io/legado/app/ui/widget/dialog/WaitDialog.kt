package io.legado.app.ui.widget.dialog

import android.app.Dialog
import android.content.Context
import android.view.View
import io.legado.app.databinding.DialogWaitBinding


@Suppress("unused")
class WaitDialog(context: Context) : Dialog(context) {

    val binding = DialogWaitBinding.inflate(layoutInflater)

    init {
        setCanceledOnTouchOutside(false)
        setContentView(binding.root)
    }

    fun setText(text: String): WaitDialog {
        binding.tvMsg.text = text
        return this
    }

    fun setText(res: Int): WaitDialog {
        binding.tvMsg.setText(res)
        return this
    }

    fun setProgress(current: Int, total: Int): WaitDialog {
        val safeTotal = total.coerceAtLeast(1)
        val safeCurrent = current.coerceIn(0, safeTotal)
        val percent = safeCurrent * 100 / safeTotal
        binding.pbProgress.visibility = View.VISIBLE
        binding.tvProgress.visibility = View.VISIBLE
        binding.pbProgress.progress = percent
        binding.tvProgress.text = "$safeCurrent/$safeTotal  $percent%"
        return this
    }

    fun clearProgress(): WaitDialog {
        binding.pbProgress.visibility = View.GONE
        binding.tvProgress.visibility = View.GONE
        binding.pbProgress.progress = 0
        binding.tvProgress.text = ""
        return this
    }

}
