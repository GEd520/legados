package io.legado.app.ui.config

import android.os.Bundle
import android.widget.TextView
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.data.repository.CoverGalleryRepository
import io.legado.app.databinding.ActivityApplicationThemeEditBinding
import io.legado.app.help.config.ApplicationThemeManager
import io.legado.app.help.config.NavigationBarConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.help.config.TopBarConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.selector
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding

class ApplicationThemeEditActivity : BaseActivity<ActivityApplicationThemeEditBinding>() {

    override val binding by viewBinding(ActivityApplicationThemeEditBinding::inflate)
    private var config: ApplicationThemeManager.Config? = null

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        val id = intent.getStringExtra(EXTRA_ID).orEmpty()
        config = ApplicationThemeManager.find(id)
        if (config == null) {
            toastOnUi(R.string.error)
            finish()
            return
        }
        binding.titleBar.title = getString(R.string.application_theme_edit)
        bindActions()
        render()
    }

    private fun bindActions() = binding.run {
        rowDayTheme.setOnClickListener { selectTheme(false) }
        rowNightTheme.setOnClickListener { selectTheme(true) }
        rowDayTopBar.setOnClickListener { selectTopBar(false) }
        rowNightTopBar.setOnClickListener { selectTopBar(true) }
        rowDayBottomBar.setOnClickListener { selectBottomBar(false) }
        rowNightBottomBar.setOnClickListener { selectBottomBar(true) }
        rowDayCover.setOnClickListener { selectCover(false) }
        rowNightCover.setOnClickListener { selectCover(true) }
        btnSave.setOnClickListener { save() }
        btnDelete.setOnClickListener { confirmDelete() }
    }

    private fun render(updateName: Boolean = true) {
        val item = config ?: return
        if (updateName) binding.etName.setText(item.name)
        setRow(binding.rowDayTheme, R.string.application_theme_component_theme, item.dayTheme?.themeName)
        setRow(binding.rowNightTheme, R.string.application_theme_component_theme, item.nightTheme?.themeName)
        setRow(binding.rowDayTopBar, R.string.application_theme_component_top_bar, topBarName(false, item.dayTopBarDir))
        setRow(binding.rowNightTopBar, R.string.application_theme_component_top_bar, topBarName(true, item.nightTopBarDir))
        setRow(binding.rowDayBottomBar, R.string.application_theme_component_bottom_bar, bottomBarName(false, item.dayBottomBarId))
        setRow(binding.rowNightBottomBar, R.string.application_theme_component_bottom_bar, bottomBarName(true, item.nightBottomBarId))
        val covers = CoverGalleryRepository()
        setRow(binding.rowDayCover, R.string.application_theme_component_cover, covers.getGroupName(item.dayCoverGroupId))
        setRow(binding.rowNightCover, R.string.application_theme_component_cover, covers.getGroupName(item.nightCoverGroupId))
    }

    private fun setRow(view: TextView, titleRes: Int, value: String?) {
        view.text = getString(
            R.string.application_theme_component_value,
            getString(titleRes),
            value ?: getString(R.string.application_theme_not_set)
        )
    }

    private fun selectTheme(isNight: Boolean) {
        val options = ThemeConfig.configList.filter { it.isNightTheme == isNight }
        val labels = listOf(getString(R.string.application_theme_not_set)) + options.map { it.themeName }
        selector(getString(R.string.application_theme_component_theme), labels) { _, index ->
            val item = config ?: return@selector
            val selected = options.getOrNull(index - 1)?.copy()
            config = if (isNight) item.copy(nightTheme = selected) else item.copy(dayTheme = selected)
            render(updateName = false)
        }
    }

    private fun selectTopBar(isNight: Boolean) {
        val options = TopBarConfig.loadEntries(this, isNight)
        val labels = listOf(getString(R.string.application_theme_not_set)) + options.map { it.config.name }
        selector(getString(R.string.application_theme_component_top_bar), labels) { _, index ->
            val item = config ?: return@selector
            val selected = options.getOrNull(index - 1)?.dirName.orEmpty()
            config = if (isNight) item.copy(nightTopBarDir = selected) else item.copy(dayTopBarDir = selected)
            render(updateName = false)
        }
    }

    private fun selectBottomBar(isNight: Boolean) {
        val options = NavigationBarConfig.loadConfigs(this).filter { it.isNight == isNight }
        val labels = listOf(getString(R.string.application_theme_not_set)) + options.map { it.name }
        selector(getString(R.string.application_theme_component_bottom_bar), labels) { _, index ->
            val item = config ?: return@selector
            val selected = options.getOrNull(index - 1)?.id
            config = if (isNight) item.copy(nightBottomBarId = selected) else item.copy(dayBottomBarId = selected)
            render(updateName = false)
        }
    }

    private fun selectCover(isNight: Boolean) {
        val options = CoverGalleryRepository().allGroupsWithImages().map { it.group }
        val labels = listOf(getString(R.string.application_theme_not_set)) + options.map { it.name }
        selector(getString(R.string.application_theme_component_cover), labels) { _, index ->
            val item = config ?: return@selector
            val selected = options.getOrNull(index - 1)?.id
            config = if (isNight) item.copy(nightCoverGroupId = selected) else item.copy(dayCoverGroupId = selected)
            render(updateName = false)
        }
    }

    private fun save() {
        val item = config ?: return
        val name = binding.etName.text.toString().trim()
        if (name.isBlank()) {
            toastOnUi(R.string.input_is_empty)
            return
        }
        runCatching {
            ApplicationThemeManager.replace(item.copy(name = name, updatedAt = System.currentTimeMillis()))
        }.onSuccess {
            toastOnUi(R.string.success)
            finish()
        }.onFailure {
            toastOnUi(R.string.application_theme_name_exists)
        }
    }

    private fun confirmDelete() {
        alert(R.string.delete, R.string.sure_del) {
            okButton {
                config?.let { ApplicationThemeManager.delete(this@ApplicationThemeEditActivity, it.id) }
                finish()
            }
            cancelButton()
        }
    }

    private fun topBarName(isNight: Boolean, dirName: String): String? {
        if (dirName.isBlank()) return null
        return TopBarConfig.loadEntries(this, isNight).firstOrNull { it.dirName == dirName }?.config?.name
    }

    private fun bottomBarName(isNight: Boolean, id: String?): String? {
        return NavigationBarConfig.loadConfigs(this).firstOrNull { it.isNight == isNight && it.id == id }?.name
    }

    companion object {
        const val EXTRA_ID = "applicationThemeId"
    }
}
