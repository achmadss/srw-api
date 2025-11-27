package module.service

import module.v1.service.MinIOStorageService
import module.v1.model.Image
import module.v1.repository.ImageRepository
import java.io.InputStream
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Service for handling image operations (no public routes)
 * Used internally by SubmissionService for image uploads
 */
class ImageService(
    private val imageRepository: ImageRepository,
    private val storageService: MinIOStorageService
) {

    /**
     * Upload an image to MinIO and create database record
     *
     * @param inputStream Image file input stream
     * @param fileName Original filename
     * @param contentType MIME type (e.g., image/jpeg, image/png)
     * @param size File size in bytes
     * @param submissionId The submission this image belongs to
     * @return Created Image entity
     */
    fun uploadImage(
        inputStream: InputStream,
        fileName: String,
        contentType: String,
        size: Long,
        submissionId: Int
    ): Image {
        return transaction {
            // Validate content type
            require(isValidImageContentType(contentType)) {
                "Invalid image content type: $contentType. Only JPEG, PNG, GIF, and WebP are supported."
            }

            // Upload to MinIO storage
            val objectKey = storageService.uploadFile(
                inputStream = inputStream,
                fileName = fileName,
                contentType = contentType,
                size = size
            )

            // Get public URL
            val imageUrl = storageService.getObjectUrl(objectKey)

            // Create database record with objectKey as ID (unique)
            imageRepository.create(
                id = objectKey,
                url = imageUrl,
                submissionId = submissionId
            )
        }
    }

    /**
     * Upload multiple images for a submission
     *
     * @param images List of image data (inputStream, fileName, contentType, size)
     * @param submissionId The submission these images belong to
     * @return List of created Image entities
     */
    fun uploadMultipleImages(
        images: List<ImageUploadData>,
        submissionId: Int
    ): List<Image> {
        return transaction {
            images.map { imageData ->
                uploadImage(
                    inputStream = imageData.inputStream,
                    fileName = imageData.fileName,
                    contentType = imageData.contentType,
                    size = imageData.size,
                    submissionId = submissionId
                )
            }
        }
    }

    /**
     * Get image by ID
     */
    fun getImage(imageId: String): Image? {
        return transaction {
            imageRepository.findById(imageId)
        }
    }

    /**
     * Get all images for a submission
     */
    fun getImagesBySubmission(submissionId: Int): List<Image> {
        return transaction {
            imageRepository.findBySubmission(submissionId)
        }
    }

    /**
     * Delete image from storage and database
     *
     * @param imageId The image ID (objectKey)
     */
    fun deleteImage(imageId: String) {
        transaction {
            // Delete from MinIO
            storageService.deleteFile(imageId)

            // Delete from database
            imageRepository.delete(imageId)
        }
    }

    /**
     * Delete multiple images
     *
     * @param imageIds List of image IDs to delete
     */
    fun deleteMultipleImages(imageIds: List<String>) {
        transaction {
            // Delete from MinIO in batch
            storageService.deleteFiles(imageIds)

            // Delete from database
            imageIds.forEach { imageRepository.delete(it) }
        }
    }

    /**
     * Check if content type is a valid image type
     */
    private fun isValidImageContentType(contentType: String): Boolean {
        val validTypes = listOf(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/gif",
            "image/webp"
        )
        return contentType.lowercase() in validTypes
    }
}

/**
 * Data class for image upload information
 */
data class ImageUploadData(
    val inputStream: InputStream,
    val fileName: String,
    val contentType: String,
    val size: Long
)
