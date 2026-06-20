package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import io.legado.app.data.entities.CoverGalleryGroup
import io.legado.app.data.entities.CoverGalleryGroupWithImages
import io.legado.app.data.entities.CoverGalleryImage
import kotlinx.coroutines.flow.Flow

@Dao
interface CoverGalleryDao {

    @Transaction
    @Query("select * from cover_gallery_groups order by `order`, id")
    fun flowGroupsWithImages(): Flow<List<CoverGalleryGroupWithImages>>

    @Transaction
    @Query(
        """
        select * from cover_gallery_groups
        where name like '%' || :query || '%'
        order by `order`, id
        """
    )
    fun flowGroupsWithImages(query: String): Flow<List<CoverGalleryGroupWithImages>>

    @Transaction
    @Query("select * from cover_gallery_groups where id = :groupId")
    fun flowGroupWithImages(groupId: Long): Flow<CoverGalleryGroupWithImages?>

    @Query("select * from cover_gallery_groups where id = :groupId")
    suspend fun getGroup(groupId: Long): CoverGalleryGroup?

    @get:Query("select * from cover_gallery_groups order by `order`, id")
    val allGroups: List<CoverGalleryGroup>

    @get:Query("select * from cover_gallery_images order by groupId, `order`, id")
    val allImages: List<CoverGalleryImage>

    @Query("select max(`order`) from cover_gallery_groups")
    suspend fun getMaxGroupOrder(): Int?

    @Query("select max(`order`) from cover_gallery_images where groupId = :groupId")
    suspend fun getMaxImageOrder(groupId: Long): Int?

    @Transaction
    @Query("select * from cover_gallery_groups where isDefault = 1 limit 1")
    fun getDefaultGroupWithImages(): CoverGalleryGroupWithImages?

    @Transaction
    @Query("select * from cover_gallery_groups order by `order`, id")
    fun getAllGroupsWithImages(): List<CoverGalleryGroupWithImages>

    @Transaction
    @Query("select * from cover_gallery_groups where id = :groupId")
    fun getGroupWithImagesNow(groupId: Long): CoverGalleryGroupWithImages?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: CoverGalleryGroup): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertGroups(vararg groups: CoverGalleryGroup)

    @Update
    suspend fun updateGroup(group: CoverGalleryGroup)

    @Query("delete from cover_gallery_groups where id = :groupId")
    suspend fun deleteGroup(groupId: Long)

    @Query("delete from cover_gallery_groups")
    fun deleteAllGroups()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImage(image: CoverGalleryImage): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertImages(vararg images: CoverGalleryImage)

    @Query("delete from cover_gallery_images where id = :imageId")
    suspend fun deleteImage(imageId: Long)

    @Query("delete from cover_gallery_images")
    fun deleteAllImages()

    @Query("update cover_gallery_groups set isDefault = 0")
    suspend fun clearDefaultGroup()

    @Query("update cover_gallery_groups set isDefault = 1, updatedAt = :updatedAt where id = :groupId")
    suspend fun markDefaultGroup(groupId: Long, updatedAt: Long)

    @Query("update cover_gallery_groups set isDefault = 0, updatedAt = :updatedAt where id = :groupId")
    suspend fun unmarkDefaultGroup(groupId: Long, updatedAt: Long)

    @Transaction
    suspend fun setDefaultGroup(groupId: Long) {
        clearDefaultGroup()
        markDefaultGroup(groupId, System.currentTimeMillis())
    }
}
