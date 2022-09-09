package com.example.demo.domain

import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id

@Entity(name = "bank")
data class Bank(
    @Id
    @GeneratedValue( strategy = javax.persistence.GenerationType.IDENTITY)
    val id: Int,

    val name: String
)