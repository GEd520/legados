package io.legado.app.ui.config

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.net.Uri
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.databinding.ActivityThemeManageBinding
import io.legado.app.databinding.ItemThemeConfigBinding
import io.legado.app.help.config.ApplicationThemeManager
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.lib.dialogs.selector
import io.legado.app.ui.widget.recycler.VerticalDivider
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.utils.externalFiles
import io.legado.app.utils.getFile
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.startActivity
import io.legado.app.utils.viewbindingdelegate.viewBinding
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileOutputStream

private const val MENU_CREATE = 6101
private const val MENU_IMPORT = 6102
private const val MENU_EXPORT = 6103

class ApplicationThemeActivity : BaseActivity<ActivityThemeManageBinding>() {

    override val binding by viewBinding(ActivityThemeManageBinding::inflate)
    private val adapter by lazy { Adapter(this) }
    private val importTheme = registerForActivityResult(HandleFileContract()) {
        it.uri?.let(::importTheme)
    }
    private val exportTheme = registerForActivityResult(HandleFileContract()) {
        if (it.uri != null) toastOnUi(R.string.export_success)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.titleBar.title = getString(R.string.application_theme_manage)
        binding.tabContainer.visibility = View.GONE
        binding.tvSummary.text = getString(R.string.application_theme_summary)
        binding.tvAddTheme.text = getString(R.string.application_theme_create)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.addItemDecoration(VerticalDivider(this))
        binding.recyclerView.adapter = adapter
        binding.tvAddTheme.setOnClickListener { showNameDialog() }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, MENU_CREATE, 0, R.string.application_theme_create)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(0, MENU_IMPORT, 1, R.string.application_theme_import)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(0, MENU_EXPORT, 2, R.string.application_theme_export)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        return true
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            MENU_CREATE -> { showNameDialog(); true }
            MENU_IMPORT -> { selectImport(); true }
            MENU_EXPORT -> { exportCurrent(); true }
            else -> super.onCompatOptionsItemSelected(item)
        }
    }

    private fun selectImport() {
        importTheme.launch {
            mode = HandleFileContract.FILE
            title = getString(R.string.application_theme_import)
            allowExtensions = arrayOf("json")
        }
    }

    private fun importTheme(uri: Uri) {
        lifecycleScope.launch {
            runCatching {
                val file = externalFiles.getFile("applicationThemeImports", "import_${System.currentTimeMillis()}.json")
                file.parentFile?.mkdirs()
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(file).use { output -> input.copyTo(output) }
                } ?: error(getString(R.string.file_not_exist))
                withContext(Dispatchers.IO) { ApplicationThemeManager.importFile(file) }
            }.onSuccess {
                toastOnUi(R.string.import_success)
                refresh()
            }.onFailure {
                toastOnUi(it.localizedMessage ?: getString(R.string.error))
            }
        }
    }

    private fun exportCurrent() {
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { ApplicationThemeManager.exportCurrent(this@ApplicationThemeActivity) }
            }.onSuccess { file ->
                exportTheme.launch {
                    mode = HandleFileContract.EXPORT
                    title = getString(R.string.application_theme_export)
                    fileData = HandleFileContract.FileData(file.name, file, "application/json")
                    onlyOtherActions = true
                    otherActions = arrayListOf(
                        SelectItem(getString(R.string.sys_folder_picker), HandleFileContract.DIR),
                        SelectItem(getString(R.string.app_folder_picker), 10),
                        SelectItem(getString(R.string.manual_input), 112)
                    )
                }
            }.onFailure {
                toastOnUi(it.localizedMessage ?: getString(R.string.error))
            }
        }
    }

    private fun refresh() {
        val items = ApplicationThemeManager.load()
        adapter.setItems(items)
        binding.tvMsg.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        binding.tvMsg.text = getString(R.string.application_theme_empty)
    }

    private fun showNameDialog(config: ApplicationThemeManager.Config? = null) {
        val input = EditText(this).apply {
            hint = getString(R.string.application_theme_name)
            setText(config?.name.orEmpty())
            setSelection(text.length)
        }
        alert(if (config == null) R.string.application_theme_create else R.string.application_theme_rename) {
            customView { input }
            okButton {
                val name = input.text.toString().trim()
                if (name.isBlank()) {
                    toastOnUi(R.string.input_is_empty)
                    return@okButton
                }
                runCatching {
                    if (config == null) {
                        val created = ApplicationThemeManager.captureCurrent(this@ApplicationThemeActivity, name)
                        ApplicationThemeManager.add(created)
                        openEditor(created)
                    } else {
                        ApplicationThemeManager.rename(config.id, name)
                    }
                }.onSuccess { refresh() }
                    .onFailure { toastOnUi(R.string.application_theme_name_exists) }
            }
            cancelButton()
        }
    }

    private fun apply(config: ApplicationThemeManager.Config) {
        runCatching { ApplicationThemeManager.apply(this, config) }
            .onSuccess {
                toastOnUi(R.string.application_theme_applied)
                recreate()
            }
            .onFailure { toastOnUi(it.localizedMessage ?: getString(R.string.error)) }
    }

    private fun showActions(config: ApplicationThemeManager.Config) {
        val items = listOf(
            getString(R.string.edit),
            getString(R.string.application_theme_update_current),
            getString(R.string.application_theme_rename),
            getString(R.string.delete)
        )
        selector(config.name, items) { _, index ->
            when (index) {
                0 -> openEditor(config)
                1 -> {
                    ApplicationThemeManager.replace(
                        ApplicationThemeManager.captureCurrent(this, config.name, config.id)
                    )
                    toastOnUi(R.string.success)
                    refresh()
                }
                2 -> showNameDialog(config)
                3 -> confirmDelete(config)
            }
        }
    }

    private fun openEditor(config: ApplicationThemeManager.Config) {
        startActivity<ApplicationThemeEditActivity> {
            putExtra(ApplicationThemeEditActivity.EXTRA_ID, config.id)
        }
    }

    private fun confirmDelete(config: ApplicationThemeManager.Config) {
        alert(R.string.delete, R.string.sure_del) {
            okButton {
                ApplicationThemeManager.delete(this@ApplicationThemeActivity, config.id)
                refresh()
            }
            cancelButton()
        }
    }

    private inner class Adapter(context: Context) :
        RecyclerAdapter<ApplicationThemeManager.Config, ItemThemeConfigBinding>(context) {

        override fun getViewBinding(parent: ViewGroup): ItemThemeConfigBinding {
            return ItemThemeConfigBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemThemeConfigBinding,
            item: ApplicationThemeManager.Config,
            payloads: MutableList<Any>
        ) = binding.run {
            tvName.text = item.name
            tvInfo.text = ApplicationThemeManager.summary(context, item)
            tvInfo.maxLines = 2
            tvBuiltin.visibility = View.GONE
            tvEdit.visibility = View.VISIBLE
            cbSelect.visibility = View.GONE
            ivShare.visibility = View.GONE
            ivDelete.visibility = View.GONE
            val current = ApplicationThemeManager.isCurrent(context, item)
            ivCurrent.visibility = if (current) View.VISIBLE else View.GONE
            tvApply.text = getString(if (current) R.string.applied else R.string.apply)
            val primary = runCatching { Color.parseColor(item.dayTheme?.primaryColor) }
                .getOrDefault(context.getColor(R.color.primary))
            val background = runCatching { Color.parseColor(item.dayTheme?.backgroundColor) }
                .getOrDefault(context.getColor(R.color.background))
            previewContainer.background = rounded(background)
            previewPrimary.background = rounded(primary)
            previewBar1.background = rounded(primary)
            previewBar2.background = rounded(primary)
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemThemeConfigBinding) {
            binding.tvApply.setOnClickListener {
                getItem(holder.layoutPosition)?.let(::apply)
            }
            binding.tvMore.setOnClickListener {
                getItem(holder.layoutPosition)?.let(::showActions)
            }
            binding.tvEdit.setOnClickListener {
                getItem(holder.layoutPosition)?.let(::openEditor)
            }
            binding.root.setOnClickListener {
                getItem(holder.layoutPosition)?.let(::apply)
            }
        }

        private fun rounded(color: Int) = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 8f * resources.displayMetrics.density
            setColor(color)
        }
    }
}
