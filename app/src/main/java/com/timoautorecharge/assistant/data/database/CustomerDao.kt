package com.timoautorecharge.assistant.data.database

import androidx.room.*
import com.timoautorecharge.assistant.data.model.Customer
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers ORDER BY lastTransactionDate DESC")
    fun getAllCustomers(): Flow<List<Customer>>
    
    @Query("SELECT * FROM customers WHERE phoneNumber = :phoneNumber")
    suspend fun getCustomerByPhoneNumber(phoneNumber: String): Customer?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: Customer)
    
    @Update
    suspend fun updateCustomer(customer: Customer)
}
