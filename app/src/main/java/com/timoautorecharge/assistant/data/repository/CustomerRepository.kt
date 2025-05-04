package com.timoautorecharge.assistant.data.repository

import com.timoautorecharge.assistant.data.database.CustomerDao
import com.timoautorecharge.assistant.data.model.Customer
import kotlinx.coroutines.flow.Flow

class CustomerRepository(private val customerDao: CustomerDao) {
    
    fun getAllCustomers(): Flow<List<Customer>> {
        return customerDao.getAllCustomers()
    }
    
    suspend fun getCustomerByPhoneNumber(phoneNumber: String): Customer? {
        return customerDao.getCustomerByPhoneNumber(phoneNumber)
    }
    
    suspend fun saveCustomer(customer: Customer) {
        customerDao.insertCustomer(customer)
    }
    
    suspend fun updateCustomer(customer: Customer) {
        customerDao.updateCustomer(customer)
    }
}
