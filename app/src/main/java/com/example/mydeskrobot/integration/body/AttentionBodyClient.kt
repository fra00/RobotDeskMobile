package com.example.mydeskrobot.integration.body

/**
 * Narrow body API used by attention centering (testable with fakes).
 */
interface AttentionBodyClient {
    suspend fun getStatus(): BodyApiResult<BodyStatus>

    suspend fun moveJoint(
        joint: BodyJoint,
        delta: Int?,
        position: Int?,
        speed: Int?,
    ): BodyApiResult<BodyOkResponse>
}

internal fun BodyApiClient.asAttentionBodyClient(): AttentionBodyClient = object : AttentionBodyClient {
    override suspend fun getStatus(): BodyApiResult<BodyStatus> = this@asAttentionBodyClient.getStatus()

    override suspend fun moveJoint(
        joint: BodyJoint,
        delta: Int?,
        position: Int?,
        speed: Int?,
    ): BodyApiResult<BodyOkResponse> = this@asAttentionBodyClient.moveJoint(
        joint = joint,
        delta = delta,
        position = position,
        speed = speed,
    )
}
