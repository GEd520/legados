package io.legado.app.data.dao

import androidx.room.*
import io.legado.app.data.entities.DirectLinkUploadRule
import kotlinx.coroutines.flow.Flow

/**
 * 直链上传规则数据访问对象（DAO）
 * 
 * 提供规则数据的增删改查操作
 * 所有方法都是挂起函数，支持协程异步调用
 */
@Dao
interface DirectLinkUploadRuleDao {

    /**
     * 获取所有规则（响应式）
     * 按默认规则优先、排序顺序、创建时间排序
     * 
     * @return 规则列表的Flow流，数据变化时自动更新
     */
    @Query("SELECT * FROM direct_link_upload_rules ORDER BY isDefault DESC, sortOrder ASC, createTime DESC")
    fun flowAll(): Flow<List<DirectLinkUploadRule>>

    /**
     * 根据ID获取规则（响应式）
     * 
     * @param id 规则ID
     * @return 规则的Flow流，数据变化时自动更新
     */
    @Query("SELECT * FROM direct_link_upload_rules WHERE id = :id")
    fun flowById(id: Long): Flow<DirectLinkUploadRule?>

    /**
     * 获取默认规则
     * 
     * @return 默认规则，如果没有则返回null
     */
    @Query("SELECT * FROM direct_link_upload_rules WHERE isDefault = 1 LIMIT 1")
    fun getDefault(): DirectLinkUploadRule?

    /**
     * 获取所有规则（一次性）
     * 按默认规则优先、排序顺序、创建时间排序
     * 
     * @return 规则列表
     */
    @Query("SELECT * FROM direct_link_upload_rules ORDER BY isDefault DESC, sortOrder ASC, createTime DESC")
    suspend fun getAll(): List<DirectLinkUploadRule>

    @Query("SELECT * FROM direct_link_upload_rules ORDER BY isDefault DESC, sortOrder ASC, createTime DESC")
    fun getAllSync(): List<DirectLinkUploadRule>

    /**
     * 根据ID获取规则（一次性）
     * 
     * @param id 规则ID
     * @return 规则对象，如果不存在则返回null
     */
    @Query("SELECT * FROM direct_link_upload_rules WHERE id = :id")
    suspend fun getById(id: Long): DirectLinkUploadRule?

    /**
     * 获取规则总数
     * 
     * @return 规则数量
     */
    @Query("SELECT COUNT(*) FROM direct_link_upload_rules")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM direct_link_upload_rules")
    fun getCountSync(): Int

    /**
     * 插入规则（支持批量）
     * 如果规则已存在，则替换
     * 
     * @param rules 要插入的规则（可变参数）
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg rules: DirectLinkUploadRule)

    /**
     * 更新规则
     * 
     * @param rule 要更新的规则
     */
    @Update
    suspend fun update(rule: DirectLinkUploadRule)

    /**
     * 删除规则
     * 
     * @param rule 要删除的规则
     */
    @Delete
    suspend fun delete(rule: DirectLinkUploadRule)

    /**
     * 根据ID删除规则
     * 
     * @param id 要删除的规则ID
     */
    @Query("DELETE FROM direct_link_upload_rules WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * 删除所有规则
     */
    @Query("DELETE FROM direct_link_upload_rules")
    suspend fun deleteAll()

    /**
     * 增加上传次数
     * 同时更新最后使用时间
     * 
     * @param id 规则ID
     * @param time 最后使用时间，默认为当前时间
     */
    @Query("UPDATE direct_link_upload_rules SET uploadCount = uploadCount + 1, lastUsedTime = :time WHERE id = :id")
    suspend fun incrementUploadCount(id: Long, time: Long = System.currentTimeMillis())

    /**
     * 清除所有默认标记
     * 将所有规则的isDefault设置为false
     */
    @Query("UPDATE direct_link_upload_rules SET isDefault = 0")
    suspend fun clearDefault()

    /**
     * 设置指定规则为默认
     * 
     * @param ruleId 规则ID
     */
    @Query("UPDATE direct_link_upload_rules SET isDefault = 1 WHERE id = :ruleId")
    suspend fun setDefaultById(ruleId: Long)

    /**
     * 设置默认规则（事务）
     * 先清除所有默认标记，再设置指定规则为默认
     * 
     * @param ruleId 规则ID
     */
    @Transaction
    suspend fun setDefault(ruleId: Long) {
        clearDefault()
        setDefaultById(ruleId)
    }

    /**
     * 更新规则排序顺序
     * 
     * @param id 规则ID
     * @param order 新的排序顺序
     */
    @Query("UPDATE direct_link_upload_rules SET sortOrder = :order WHERE id = :id")
    suspend fun updateSortOrder(id: Long, order: Int)
}
