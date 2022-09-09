package com.example.demo.domain

import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.ManyToMany

@Entity(name = "bank")

data class Bank(
    @Id
    @GeneratedValue( strategy = javax.persistence.GenerationType.IDENTITY)
    val id: Int?,

    val name: String?,

    @ManyToMany
    val countries: List<Country>?
)