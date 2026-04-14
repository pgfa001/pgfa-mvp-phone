package com.provingground.service

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.model.PutObjectRequest
import aws.sdk.kotlin.services.s3.presigners.presignGetObject
import aws.sdk.kotlin.services.s3.presigners.presignPutObject
import kotlin.time.Duration.Companion.seconds

interface VideoStorageService {
    suspend fun createUploadUrl(
        objectKey: String,
        expiresInSeconds: Long = 900
    ): PresignedUploadResult

    suspend fun createReadUrl(
        objectKey: String,
        expiresInSeconds: Long = 900
    ): PresignedReadResult
}

data class PresignedUploadResult(
    val uploadUrl: String,
    val expiresAt: Long
)

data class PresignedReadResult(
    val readUrl: String,
    val expiresAt: Long
)

class S3VideoStorageService(
    private val bucketName: String,
    private val s3Client: S3Client
) : VideoStorageService {

    override suspend fun createUploadUrl(
        objectKey: String,
        expiresInSeconds: Long
    ): PresignedUploadResult {
        val request = PutObjectRequest {
            bucket = bucketName
            key = objectKey
        }

        val presigned = s3Client.presignPutObject(
            input = request,
            duration = expiresInSeconds.seconds
        )

        return PresignedUploadResult(
            uploadUrl = presigned.url.toString(),
            expiresAt = System.currentTimeMillis() + (expiresInSeconds * 1000)
        )
    }

    override suspend fun createReadUrl(
        objectKey: String,
        expiresInSeconds: Long
    ): PresignedReadResult {
        val request = GetObjectRequest {
            bucket = bucketName
            key = objectKey
        }

        val presigned = s3Client.presignGetObject(
            input = request,
            duration = expiresInSeconds.seconds
        )

        return PresignedReadResult(
            readUrl = presigned.url.toString(),
            expiresAt = System.currentTimeMillis() + (expiresInSeconds * 1000)
        )
    }
}