package com.example.demo.repository

/**
 * A Projection for the {@link com.example.demo.domain.Employee} entity
 */
interface EmployeeInfo {
    val id: Int?
    val name: String?
    val salary: SalaryInfo?

    /**
     * A Projection for the {@link com.example.demo.domain.Salary} entity
     */
    interface SalaryInfo {
        val id: Int?
        val amount: Int?
    }
}