package module.v1.repository

import module.v1.model.Image
import module.v1.model.MLStatus
import module.v1.model.Submission
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class ImageRepository {
    fun create(id: String, url: String, submissionId: Int): Image {
        require(id.isNotBlank()) { "ID cannot be blank" }
        require(url.isNotBlank()) { "URL cannot be blank" }

        val submission = Submission.findById(submissionId) ?: throw IllegalArgumentException("Submission with id $submissionId not found")

        val existingImage = Image.findById(id)
        require(existingImage == null) { "Image with id '$id' already exists" }

        val now = Clock.System.now()
        return Image.new(id) {
            this.url = url
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

    fun update(id: String, url: String?, submissionId: Int?): Image {
        val image = Image.findById(id) ?: throw IllegalArgumentException("Image with id $id not found")

        url?.let {
            require(it.isNotBlank()) { "URL cannot be blank" }
            image.url = it
        }

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