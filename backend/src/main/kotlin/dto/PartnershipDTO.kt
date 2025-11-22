package me.pavekovt.dto

import kotlinx.serialization.Serializable

@Serializable
data class PartnershipDTO(
    val id: String,
    val partnerId: String,
    val partnerName: String,
    val partnerEmail: String,
    val status: String,
    val initiatedByMe: Boolean,
    val createdAt: String,
    val acceptedAt: String? = null
)

@Serializable
data class PartnerInviteRequest(
    val partnerEmail: String
)

@Serializable
data class PartnershipInvitationsDTO(
    val sent: List<PartnershipDTO>,
    val received: List<PartnershipDTO>
)
