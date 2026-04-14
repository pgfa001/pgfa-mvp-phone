package com.provingground.utils

import java.time.LocalDate
import java.time.Period
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object AgeUtils {
    private val dobFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy")

    fun calculateAge(dob: String, today: LocalDate = LocalDate.now(ZoneOffset.UTC)): Int {
        val birthDate = LocalDate.parse(dob, dobFormatter)
        return Period.between(birthDate, today).years
    }
}