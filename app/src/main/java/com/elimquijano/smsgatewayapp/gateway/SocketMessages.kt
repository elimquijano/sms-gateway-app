package com.elimquijano.smsgatewayapp.gateway

import com.google.gson.annotations.SerializedName

data class ServerMessage(
    val type: String,
    val payload: SmsTaskPayload?
)

data class SmsTaskPayload(
    val taskId: String,
    val numero: String,
    val mensaje: String,
    val attempts: Int
)

data class ClientStatusUpdate(
    val type: String = "STATUS_UPDATE",
    val status: String,
    val taskId: String,
    val details: String? = null,
    @SerializedName("task") val failedTask: SmsTaskPayload? = null
)