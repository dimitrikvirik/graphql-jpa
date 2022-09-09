package com.example.demo.domain

import org.hibernate.annotations.Cascade
import org.hibernate.annotations.CascadeType
import javax.persistence.*

@Entity(name = "employee")
data class Employee(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Int?,

    var name: String?,

    @ElementCollection
    var contacts: List<String>,

    @Embedded
    var identity: Identity?,

    @OneToOne
    @Cascade(CascadeType.ALL)
    var salary: Salary?
)