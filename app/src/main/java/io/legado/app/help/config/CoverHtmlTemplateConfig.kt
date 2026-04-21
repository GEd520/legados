package io.legado.app.help.config

import androidx.annotation.Keep
import io.legado.app.help.DefaultData
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import splitties.init.appCtx
import java.io.File

/**
 * HTML封面模板配置管理类
 * 
 * 负责管理HTML封面模板的增删改查和持久化存储
 * 支持多个模板存储，用户可选择当前使用的模板
 */
@Keep
object CoverHtmlTemplateConfig {

    const val configFileName = "coverHtmlTemplate.json"
    val configFilePath = FileUtils.getPath(appCtx.filesDir, configFileName)

    /**
     * 模板列表
     * 首次访问时从文件加载，若文件不存在则使用默认模板
     */
    val templateList: ArrayList<Template> by lazy {
        val list = getTemplates() ?: getDefaultTemplates()
        ArrayList(list)
    }

    /**
     * 获取默认模板列表
     */
    private fun getDefaultTemplates(): List<Template> {
        return listOf(
            Template(
                id = "default",
                name = "默认模板",
                htmlCode = DefaultData.coverHtmlTemplate,
                isSelected = true
            )
        )
    }

    /**
     * 从文件加载模板列表
     */
    private fun getTemplates(): List<Template>? {
        val configFile = File(configFilePath)
        if (configFile.exists()) {
            kotlin.runCatching {
                val json = configFile.readText()
                return GSON.fromJsonArray<Template>(json).getOrThrow()
            }
        }
        return null
    }

    /**
     * 保存模板列表到文件
     */
    fun save() {
        val json = GSON.toJson(templateList)
        FileUtils.delete(configFilePath)
        FileUtils.createFileIfNotExist(configFilePath).writeText(json)
    }

    /**
     * 添加模板
     * 
     * @param template 模板对象
     */
    fun addTemplate(template: Template) {
        templateList.add(template)
        save()
    }

    /**
     * 更新模板
     * 
     * @param template 更新后的模板对象
     */
    fun updateTemplate(template: Template) {
        val index = templateList.indexOfFirst { it.id == template.id }
        if (index != -1) {
            templateList[index] = template
            save()
        }
    }

    /**
     * 删除模板
     * 
     * @param index 模板索引
     */
    fun deleteTemplate(index: Int) {
        if (index in 0 until templateList.size) {
            val wasSelected = templateList[index].isSelected
            templateList.removeAt(index)
            if (wasSelected && templateList.isNotEmpty()) {
                templateList[0].isSelected = true
            }
            save()
        }
    }

    /**
     * 删除模板
     * 
     * @param id 模板ID
     */
    fun deleteTemplateById(id: String) {
        val index = templateList.indexOfFirst { it.id == id }
        if (index != -1) {
            deleteTemplate(index)
        }
    }

    /**
     * 设置当前使用的模板
     * 
     * @param id 模板ID
     */
    fun setSelectedTemplate(id: String) {
        templateList.forEach { it.isSelected = (it.id == id) }
        save()
    }

    /**
     * 获取当前选中的模板
     * 
     * @return 当前选中的模板，若无则返回第一个模板
     */
    fun getSelectedTemplate(): Template {
        return templateList.find { it.isSelected } ?: templateList.firstOrNull()
        ?: getDefaultTemplates().first()
    }

    /**
     * 根据ID获取模板
     * 
     * @param id 模板ID
     * @return 模板对象，若不存在则返回null
     */
    fun getTemplateById(id: String): Template? {
        return templateList.find { it.id == id }
    }

    /**
     * 生成新模板ID
     */
    fun generateId(): String {
        return System.currentTimeMillis().toString()
    }

    /**
     * HTML封面模板数据类
     * 
     * @property id 唯一标识
     * @property name 模板名称
     * @property htmlCode HTML代码模板，支持 {{bookName}} 和 {{author}} 变量
     * @property isSelected 是否为当前使用的模板
     */
    @Keep
    data class Template(
        var id: String = "",
        var name: String = "",
        var htmlCode: String = "",
        var isSelected: Boolean = false
    ) {

        override fun hashCode(): Int {
            return GSON.toJson(this).hashCode()
        }

        override fun equals(other: Any?): Boolean {
            if (other is Template) {
                return other.id == id
            }
            return false
        }

    }

}
