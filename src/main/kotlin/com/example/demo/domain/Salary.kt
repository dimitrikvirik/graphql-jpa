package com.example.demo.domain

import org.hibernate.annotations.Cascade
import org.hibernate.annotations.CascadeType
import javax.persistence.*

@Entity( name = "salary")
data class Salary(
    @Id
    @GeneratedValue( strategy = GenerationType.IDENTITY)
    val id: Int?,

    var amount: Int?,

    @Embedded
    val identity: Identity?,

    @ElementCollection
    var currencies: List<String>?,

    @ManyToOne
    @Cascade(CascadeType.ALL)
    var bank: Bank?
){}
