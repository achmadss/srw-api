package repository

import model.Image
import model.MLStatus
import model.Submission
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class ImageRepository {
    fun create(id: String, submissionId: Int): Image {
        require(id.isNotBlank()) { "ID cannot be blank" }

        val submission = Submission.findById(submissionId) ?: throw IllegalArgumentException("Submission with id $submissionId not found")

        val existingImage = Image.findById(id)
        require(existingImage == null) { "Image with id '$id' already exists" }

        val now = Clock.System.now()
        return Image.new(id) {
            this.submission = submission
            this.createdAt = now
            this.updatedAt = now
        }
    }

    fun findById(id: String): Image? {
        return Image.findById(id)
    }

    fun findAll(): List<Image> {
        return Image.all().toList()
    }

    fun findBySubmission(submissionId: Int): List<Image> {
        val submission = Submission.findById(submissionId) ?: throw IllegalArgumentException("Submission with id $submissionId not found")
        return submission.images.toList()
    }

    fun update(id: String, submissionId: Int?): Image {
        val image = Image.findById(id) ?: throw IllegalArgumentException("Image with id $id not found")

        submissionId?.let {
            val submission = Submission.findById(it) ?: throw IllegalArgumentException("Submission with id $it not found")
            image.submission = submission
        }

        image.updatedAt = Clock.System.now()
        return image
    }

    fun delete(id: String) {
        val image = Image.findById(id) ?: throw IllegalArgumentException("Image with id $id not found")
        image.delete()
    }

    fun updateMLStatus(id: String, status: MLStatus, error: String?): Image {
        val image = Image.findById(id) ?: throw IllegalArgumentException("Image with id $id not found")
        image.mlStatus = status.name
        image.mlError = error
        image.updatedAt = Clock.System.now()
        return image
    }
}