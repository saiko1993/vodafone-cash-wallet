package com.timoautorecharge.assistant.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.timoautorecharge.assistant.data.model.UiElementRegistry
import kotlinx.coroutines.flow.Flow

@Dao
interface UiElementRegistryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertElement(element: UiElementRegistry)
    
    @Update
    suspend fun updateElement(element: UiElementRegistry)
    
    @Delete
    suspend fun deleteElement(element: UiElementRegistry)
    
    @Query("SELECT * FROM ui_element_registry WHERE keyName = :keyName LIMIT 1")
    fun getElementByKey(keyName: String): Flow<UiElementRegistry?>
    
    @Query("SELECT * FROM ui_element_registry WHERE appName = :appName")
    fun getElementsByAppName(appName: String): Flow<List<UiElementRegistry>>
    
    @Query("SELECT * FROM ui_element_registry")
    fun getAllElements(): Flow<List<UiElementRegistry>>
    
    @Query("DELETE FROM ui_element_registry WHERE keyName = :keyName")
    suspend fun deleteElementByKey(keyName: String)
}
