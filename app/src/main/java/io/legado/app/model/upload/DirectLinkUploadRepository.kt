package io.legado.app.model.upload

import io.legado.app.data.appDb
import io.legado.app.data.entities.DirectLinkUploadRule
import io.legado.app.data.entities.UploadHistory
import io.legado.app.data.entities.UploadHistoryWithRule
import io.legado.app.help.DirectLinkUpload
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject
import kotlinx.coroutines.flow.Flow

/**
 * 直链上传数据仓库
 * 
 * 封装数据访问逻辑，提供统一的数据操作接口
 * 遵循 Repository 模式，隔离数据源和业务逻辑
 */
class DirectLinkUploadRepository {

    // 数据访问对象
    private val ruleDao = appDb.directLinkUploadRuleDao
    private val historyDao = appDb.uploadHistoryDao

    data class BackupData(
        val version: Int = 1,
        val rules: List<DirectLinkUploadRule> = emptyList()
    )

    /**
     * 获取所有规则（响应式）
     * 
     * @return 规则列表的Flow流
     */
    fun getRules(): Flow<List<DirectLinkUploadRule>> = ruleDao.flowAll()

    /**
     * 根据ID获取规则
     * 
     * @param id 规则ID
     * @return 规则对象，如果不存在则返回null
     */
    suspend fun getRuleById(id: Long): DirectLinkUploadRule? = ruleDao.getById(id)

    /**
     * 获取默认规则
     * 优先返回标记为默认的规则，如果没有则返回第一个规则
     * 
     * @return 默认规则，如果没有规则则返回null
     */
    suspend fun getDefaultRule(): DirectLinkUploadRule? {
        // 先尝试获取标记为默认的规则
        val default = ruleDao.getDefault()
        if (default != null) return default
        
        // 如果没有默认规则，返回第一个规则
        val all = ruleDao.getAll()
        return all.firstOrNull()
    }

    /**
     * 添加规则
     * 
     * @param rule 要添加的规则
     */
    suspend fun addRule(rule: DirectLinkUploadRule) {
        ruleDao.insert(rule)
    }

    /**
     * 更新规则
     * 
     * @param rule 要更新的规则
     */
    suspend fun updateRule(rule: DirectLinkUploadRule) {
        ruleDao.update(rule)
    }

    /**
     * 删除规则
     * 
     * @param rule 要删除的规则
     */
    suspend fun deleteRule(rule: DirectLinkUploadRule) {
        ruleDao.delete(rule)
    }

    /**
     * 设置默认规则
     * 
     * @param ruleId 规则ID
     */
    suspend fun setDefaultRule(ruleId: Long) {
        ruleDao.setDefault(ruleId)
    }

    /**
     * 增加规则的上传次数
     * 
     * @param ruleId 规则ID
     */
    suspend fun incrementUploadCount(ruleId: Long) {
        ruleDao.incrementUploadCount(ruleId)
    }

    /**
     * 获取所有历史记录（响应式）
     * 
     * @return 历史记录列表的Flow流
     */
    fun getHistories(): Flow<List<UploadHistory>> = historyDao.flowAll()

    /**
     * 获取所有历史记录（带规则名称，响应式）
     * 关联查询规则表，获取最新的规则名称
     * 
     * @return 历史记录与规则名称的关联列表的Flow流
     */
    fun getHistoriesWithRule(): Flow<List<UploadHistoryWithRule>> = historyDao.flowAllWithRule()

    /**
     * 根据规则ID获取历史记录（响应式）
     * 
     * @param ruleId 规则ID
     * @return 历史记录列表的Flow流
     */
    fun getHistoriesByRule(ruleId: Long): Flow<List<UploadHistory>> = 
        historyDao.flowByRuleId(ruleId)

    /**
     * 搜索历史记录
     * 
     * @param keyword 搜索关键词
     * @return 匹配的历史记录列表的Flow流
     */
    fun searchHistories(keyword: String): Flow<List<UploadHistory>> = 
        historyDao.flowSearch(keyword)

    /**
     * 添加历史记录
     * 
     * @param history 要添加的历史记录
     */
    suspend fun addHistory(history: UploadHistory) {
        historyDao.insert(history)
    }

    /**
     * 删除历史记录
     * 
     * @param history 要删除的历史记录
     */
    suspend fun deleteHistory(history: UploadHistory) {
        historyDao.delete(history)
    }

    /**
     * 清除所有历史记录
     * 
     * @return 删除的记录数
     */
    suspend fun clearAllHistories(): Int = historyDao.deleteAll()

    /**
     * 删除指定天数之前的历史记录
     * 
     * @param days 天数
     * @return 删除的记录数
     */
    suspend fun deleteOldHistories(days: Int): Int {
        val timestamp = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
        return historyDao.deleteOldRecords(timestamp)
    }

    /**
     * 获取上传统计信息
     * 
     * @return 上传统计数据
     */
    suspend fun getUploadStats(): UploadStats {
        val totalCount = historyDao.getCount()
        val successCount = historyDao.getSuccessCount()
        val totalSize = historyDao.getTotalUploadSize() ?: 0L
        return UploadStats(
            totalCount = totalCount,
            successCount = successCount,
            failedCount = totalCount - successCount,
            totalSize = totalSize
        )
    }

    /**
     * 从旧配置迁移数据
     * 将ACache中的旧配置迁移到Room数据库
     * 只在没有规则时执行迁移
     */
    suspend fun migrateFromOldConfig() {
        // 获取旧配置
        val oldRule = DirectLinkUpload.getConfig()
        
        // 如果有旧配置且数据库中没有规则，则迁移
        if (oldRule != null && ruleDao.getCount() == 0) {
            val newRule = DirectLinkUploadRule(
                uploadUrl = oldRule.uploadUrl,
                downloadUrlRule = oldRule.downloadUrlRule,
                summary = oldRule.summary,
                compress = oldRule.compress,
                isDefault = true,
                sortOrder = 0
            )
            ruleDao.insert(newRule)
            
            // 删除旧配置
            DirectLinkUpload.delConfig()
        }
    }

    /**
     * 导入默认规则
     * 从assets中的默认规则文件导入
     * 只在没有规则时导入
     */
    suspend fun importDefaultRules() {
        // 如果已有规则，不导入
        if (ruleDao.getCount() > 0) return
        
        // 获取默认规则列表
        val defaultRules = DirectLinkUpload.defaultRules.mapIndexed { index, rule ->
            DirectLinkUploadRule(
                uploadUrl = rule.uploadUrl,
                downloadUrlRule = rule.downloadUrlRule,
                summary = rule.summary,
                compress = rule.compress,
                sortOrder = index,
                isDefault = index == 0  // 第一个规则设为默认
            )
        }
        
        // 批量插入
        ruleDao.insert(*defaultRules.toTypedArray())
    }

    suspend fun createBackupJson(): String? {
        migrateFromOldConfig()
        val rules = ruleDao.getAll()
        if (rules.isEmpty()) return null
        return GSON.toJson(BackupData(rules = rules))
    }

    fun createBackupJsonSync(): String? {
        val rules = ruleDao.getAllSync()
        if (rules.isNotEmpty()) return GSON.toJson(BackupData(rules = rules))
        val oldRule = DirectLinkUpload.getConfig() ?: return null
        return GSON.toJson(
            BackupData(
                rules = listOf(
                    DirectLinkUploadRule(
                        uploadUrl = oldRule.uploadUrl,
                        downloadUrlRule = oldRule.downloadUrlRule,
                        summary = oldRule.summary,
                        compress = oldRule.compress,
                        isDefault = true
                    )
                )
            )
        )
    }

    suspend fun getBackupRuleCount(): Int {
        migrateFromOldConfig()
        return ruleDao.getCount()
    }

    suspend fun restoreBackupJson(json: String) {
        val rules = parseBackupRules(json)
        if (rules.isEmpty()) return
        ruleDao.deleteAll()
        ruleDao.insert(*ensureDefaultRule(rules).toTypedArray())
        DirectLinkUpload.delConfig()
    }

    private fun parseBackupRules(json: String): List<DirectLinkUploadRule> {
        GSON.fromJsonObject<BackupData>(json).getOrNull()
            ?.rules
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }

        GSON.fromJsonArray<DirectLinkUploadRule>(json).getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }

        GSON.fromJsonObject<DirectLinkUpload.Rule>(json).getOrNull()?.let { oldRule ->
            return listOf(
                DirectLinkUploadRule(
                    uploadUrl = oldRule.uploadUrl,
                    downloadUrlRule = oldRule.downloadUrlRule,
                    summary = oldRule.summary,
                    compress = oldRule.compress,
                    isDefault = true
                )
            )
        }

        return emptyList()
    }

    private fun ensureDefaultRule(rules: List<DirectLinkUploadRule>): List<DirectLinkUploadRule> {
        if (rules.any { it.isDefault }) return rules
        return rules.mapIndexed { index, rule ->
            if (index == 0) rule.copy(isDefault = true) else rule
        }
    }
}

/**
 * 上传统计数据类
 * 
 * @property totalCount 总上传次数
 * @property successCount 成功次数
 * @property failedCount 失败次数
 * @property totalSize 总上传大小（字节）
 */
data class UploadStats(
    val totalCount: Int,
    val successCount: Int,
    val failedCount: Int,
    val totalSize: Long
)
