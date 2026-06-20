package io.legado.app.ui.config

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.data.repository.CoverGalleryRepository
import io.legado.app.lib.dialogs.selector
import io.legado.app.lib.prefs.SwitchPreference
import io.legado.app.lib.prefs.fragment.PreferenceFragment
import io.legado.app.lib.theme.primaryColor
import io.legado.app.model.BookCover
import io.legado.app.constant.EventBus
import io.legado.app.ui.config.covergallery.CoverGalleryActivity
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.widget.image.CoverImageView
import io.legado.app.utils.FileUtils
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.externalFiles
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefString
import io.legado.app.utils.inputStream
import io.legado.app.utils.postEvent
import io.legado.app.utils.putPrefString
import io.legado.app.utils.readUri
import io.legado.app.utils.removePref
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import java.io.FileOutputStream

class CoverConfigFragment : PreferenceFragment(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    private val coverGalleryRepository by lazy { CoverGalleryRepository() }
    private val requestCodeCover = 111
    private val requestCodeCoverDark = 112
    private val selectImage = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            when (it.requestCode) {
                requestCodeCover -> setCoverFromUri(PreferKey.defaultCover, uri)
                requestCodeCoverDark -> setCoverFromUri(PreferKey.defaultCoverDark, uri)
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.pref_config_cover)
        upPreferenceSummary(PreferKey.defaultCover, getPrefString(PreferKey.defaultCover))
        upPreferenceSummary(PreferKey.defaultCoverDark, getPrefString(PreferKey.defaultCoverDark))
        upPreferenceSummary(PreferKey.coverCollectionDay, getPrefString(PreferKey.coverCollectionDay))
        upPreferenceSummary(PreferKey.coverCollectionNight, getPrefString(PreferKey.coverCollectionNight))
        findPreference<SwitchPreference>(PreferKey.coverShowAuthor)
            ?.isEnabled = getPrefBoolean(PreferKey.coverShowName)
        findPreference<SwitchPreference>(PreferKey.coverShowAuthorN)
            ?.isEnabled = getPrefBoolean(PreferKey.coverShowNameN)
        upCoverHtmlCodeVisibility()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.setTitle(R.string.cover_config)
        listView.setEdgeEffectColor(primaryColor)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        sharedPreferences ?: return
        when (key) {
            PreferKey.defaultCover,
            PreferKey.defaultCoverDark,
            PreferKey.coverCollectionDay,
            PreferKey.coverCollectionNight -> {
                upPreferenceSummary(key, getPrefString(key))
            }

            PreferKey.coverHtmlEnable -> {
                upCoverHtmlCodeVisibility()
                CoverImageView.clearHtmlCoverCache()
                postEvent(EventBus.BOOKSHELF_REFRESH, "")
                if (getPrefBoolean(PreferKey.coverHtmlEnable)) {
                    CoverHtmlActivity.startEditTemplate(requireContext(), isNew = false)
                }
            }

            PreferKey.coverShowName -> {
                findPreference<SwitchPreference>(PreferKey.coverShowAuthor)
                    ?.isEnabled = getPrefBoolean(key)
                BookCover.upDefaultCover()
            }

            PreferKey.coverShowNameN -> {
                findPreference<SwitchPreference>(PreferKey.coverShowAuthorN)
                    ?.isEnabled = getPrefBoolean(key)
                BookCover.upDefaultCover()
            }

            PreferKey.coverShowAuthor,
            PreferKey.coverShowAuthorN,
            PreferKey.coverCollectionModeDay,
            PreferKey.coverCollectionModeNight -> {
                refreshCover()
            }
        }
    }

    @SuppressLint("PrivateResource")
    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        when (preference.key) {
            "coverRule" -> showDialogFragment(CoverRuleConfigDialog())
            "coverHtmlCode" -> CoverHtmlActivity.startTemplateList(requireContext())
            "coverGallery" -> CoverGalleryActivity.start(requireContext())
            PreferKey.coverCollectionDay -> selectCoverCollection(false)
            PreferKey.coverCollectionNight -> selectCoverCollection(true)
            PreferKey.defaultCover ->
                if (getPrefString(preference.key).isNullOrEmpty()) {
                    selectImage.launch {
                        requestCode = requestCodeCover
                        mode = HandleFileContract.IMAGE
                    }
                } else {
                    context?.selector(
                        items = arrayListOf(
                            getString(R.string.delete),
                            getString(R.string.select_image)
                        )
                    ) { _, i ->
                        if (i == 0) {
                            removePref(preference.key)
                            BookCover.upDefaultCover()
                        } else {
                            selectImage.launch {
                                requestCode = requestCodeCover
                                mode = HandleFileContract.IMAGE
                            }
                        }
                    }
                }

            PreferKey.defaultCoverDark ->
                if (getPrefString(preference.key).isNullOrEmpty()) {
                    selectImage.launch {
                        requestCode = requestCodeCoverDark
                        mode = HandleFileContract.IMAGE
                    }
                } else {
                    context?.selector(
                        items = arrayListOf(
                            getString(R.string.delete),
                            getString(R.string.select_image)
                        )
                    ) { _, i ->
                        if (i == 0) {
                            removePref(preference.key)
                            BookCover.upDefaultCover()
                        } else {
                            selectImage.launch {
                                requestCode = requestCodeCoverDark
                                mode = HandleFileContract.IMAGE
                            }
                        }
                    }
                }
        }
        return super.onPreferenceTreeClick(preference)
    }

    private fun upPreferenceSummary(preferenceKey: String, value: String?) {
        val preference = findPreference<Preference>(preferenceKey) ?: return
        when (preferenceKey) {
            PreferKey.defaultCover,
            PreferKey.defaultCoverDark -> preference.summary = if (value.isNullOrBlank()) {
                getString(R.string.select_image)
            } else {
                value
            }

            PreferKey.coverCollectionDay,
            PreferKey.coverCollectionNight -> {
                lifecycleScope.launch {
                    val groupName = withContext(IO) {
                        coverGalleryRepository.getGroupName(value?.toLongOrNull())
                    }
                    preference.summary = groupName ?: getString(R.string.cover_collection_none)
                }
            }

            else -> preference.summary = value
        }
    }

    private fun selectCoverCollection(isNight: Boolean) {
        lifecycleScope.launch {
            val groups = withContext(IO) {
                coverGalleryRepository.allGroupsWithImages()
                    .filter { it.images.isNotEmpty() }
            }
            val items = arrayListOf(getString(R.string.cover_collection_none))
            items.addAll(groups.map { "${it.group.name} (${it.images.size})" })
            context?.selector(items = items) { _, index ->
                val selected = if (index <= 0) null else groups.getOrNull(index - 1)?.group
                coverGalleryRepository.setSelectedGroup(isNight, selected?.id)
                upPreferenceSummary(
                    if (isNight) PreferKey.coverCollectionNight else PreferKey.coverCollectionDay,
                    selected?.id?.toString()
                )
                refreshCover()
            }
        }
    }

    private fun refreshCover() {
        BookCover.upDefaultCover()
        postEvent(EventBus.BOOKSHELF_REFRESH, System.currentTimeMillis().toString())
        postEvent(EventBus.REFRESH_BOOK_INFO, false)
    }

    /**
     * 根据HTML封面启用开关控制"自动生成封面代码"菜单项的可见性
     */
    private fun upCoverHtmlCodeVisibility() {
        findPreference<Preference>("coverHtmlCode")?.isVisible =
            getPrefBoolean(PreferKey.coverHtmlEnable)
    }

    private fun setCoverFromUri(preferenceKey: String, uri: Uri) {
        readUri(uri) { fileDoc, inputStream ->
            kotlin.runCatching {
                var file = requireContext().externalFiles
                val suffix = if (fileDoc.name.contains(".9.png", true)) {
                    ".9.png"
                } else {
                    "." + fileDoc.name.substringAfterLast(".")
                }
                val fileName = uri.inputStream(requireContext()).getOrThrow().use {
                    MD5Utils.md5Encode(it) + suffix
                }
                file = FileUtils.createFileIfNotExist(file, "covers", fileName)
                FileOutputStream(file).use {
                    inputStream.copyTo(it)
                }
                putPrefString(preferenceKey, file.absolutePath)
                BookCover.upDefaultCover()
            }.onFailure {
                appCtx.toastOnUi(it.localizedMessage)
            }
        }
    }

}
