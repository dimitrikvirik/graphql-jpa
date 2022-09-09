package com.example.demo.domain

import javax.persistence.Column
import javax.persistence.ElementCollection
import javax.persistence.Embeddable

@Embeddable
data class Identity(
    @Column(name = "personal_id")
    var personalId: String?
)