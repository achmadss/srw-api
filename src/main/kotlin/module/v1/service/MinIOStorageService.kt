package module.v1.service

import io.minio.BucketExistsArgs
import io.minio.GetObjectArgs
import io.minio.GetPresignedObjectUrlArgs
import io.minio.ListObjectsArgs
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.minio.RemoveObjectArgs
import io.minio.StatObjectArgs
import io.minio.http.Method
import io.minio.messages.Item
import java.io.InputStream
import java.util.UUID

/**
 * Service for handling file storage operations with MinIO
 */
class MinIOStorageService(
    private val endpoint: String,
    private val accessKey: String,
    private val secretKey: String,
    private val bucketName: String
) {
    private val minioClient: MinioClient = MinioClient.builder()
        .endpoint(endpoint)
        .credentials(accessKey, secretKey)
        .build()

    init {
        // Ensure bucket exists on initialization
        createBucketIfNotExists()
    }

    /**
     * Create bucket if it doesn't exist
     */
    private fun createBucketIfNotExists() {
        val bucketExists = minioClient.bucketExists(
            BucketExistsArgs.builder()
                .bucket(bucketName)
                .build()
        )

        if (!bucketExists) {
            minioClient.makeBucket(
                MakeBucketArgs.builder()
                    .bucket(bucketName)
                    .build()
            )
        }
    }

    /**
     * Upload a file to MinIO
     *
     * @param inputStream The file input stream
     * @param fileName Original file name
     * @param contentType MIME type of the file
     * @param size Size of the file in bytes
     * @return The object key (file path) in MinIO
     */
    fun uploadFile(
        inputStream: InputStream,
        fileName: String,
        contentType: String,
        size: Long
    ): String {
        // Generate unique key with UUID to avoid filename conflicts
        val fileExtension = fileName.substringAfterLast(".", "")
        val uniqueKey = "${UUID.randomUUID()}${if (fileExtension.isNotEmpty()) ".$fileExtension" else ""}"

        minioClient.putObject(
            PutObjectArgs.builder()
                .bucket(bucketName)
                .`object`(uniqueKey)
                .stream(inputStream, size, -1)
                .contentType(contentType)
                .build()
        )

        return uniqueKey
    }

    /**
     * Get the public URL for an object
     *
     * @param objectKey The object key in MinIO
     * @return The full URL to access the object
     */
    fun getObjectUrl(objectKey: String): String {
        return "$endpoint/$bucketName/$objectKey"
    }

    /**
     * Get a presigned URL for temporary access (7 days)
     *
     * @param objectKey The object key in MinIO
     * @param expirySeconds Expiry time in seconds (default: 7 days)
     * @return Presigned URL
     */
    fun getPresignedUrl(objectKey: String, expirySeconds: Int = 604800): String {
        return minioClient.getPresignedObjectUrl(
            GetPresignedObjectUrlArgs.builder()
                .method(Method.GET)
                .bucket(bucketName)
                .`object`(objectKey)
                .expiry(expirySeconds)
                .build()
        )
    }

    /**
     * Download a file from MinIO
     *
     * @param objectKey The object key in MinIO
     * @return InputStream of the file
     */
    fun downloadFile(objectKey: String): InputStream {
        return minioClient.getObject(
            GetObjectArgs.builder()
                .bucket(bucketName)
                .`object`(objectKey)
                .build()
        )
    }

    /**
     * Delete a file from MinIO
     *
     * @param objectKey The object key in MinIO
     */
    fun deleteFile(objectKey: String) {
        minioClient.removeObject(
            RemoveObjectArgs.builder()
                .bucket(bucketName)
                .`object`(objectKey)
                .build()
        )
    }

    /**
     * Delete multiple files from MinIO
     *
     * @param objectKeys List of object keys to delete
     */
    fun deleteFiles(objectKeys: List<String>) {
        objectKeys.forEach { deleteFile(it) }
    }

    /**
     * Check if an object exists
     *
     * @param objectKey The object key in MinIO
     * @return true if object exists, false otherwise
     */
    fun objectExists(objectKey: String): Boolean {
        return try {
            minioClient.statObject(
                StatObjectArgs.builder()
                    .bucket(bucketName)
                    .`object`(objectKey)
                    .build()
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * List all objects in the bucket
     *
     * @param prefix Optional prefix to filter objects
     * @return List of object keys
     */
    fun listObjects(prefix: String = ""): List<String> {
        val objects = mutableListOf<String>()
        val results = minioClient.listObjects(
            ListObjectsArgs.builder()
                .bucket(bucketName)
                .prefix(prefix)
                .build()
        )

        for (result in results) {
            val item: Item = result.get()
            objects.add(item.objectName())
        }

        return objects
    }
}