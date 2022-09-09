package com.example.demo.repository

import com.example.demo.domain.Employee
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.querydsl.QuerydslPredicateExecutor


interface EmployeeRepository: JpaRepository<Employee, Long>, QuerydslPredicateExecutor<Employee>,
    JpaSpecificationExecutor<Employee> {




}