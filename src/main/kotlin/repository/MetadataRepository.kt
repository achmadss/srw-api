package repository

import model.Image
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import model.Metadata
import model.Trash

@OptIn(ExperimentalTime::class)
class MetadataRepository {
    fun create(amount: Int, imageId: String, trashName: String): Metadata {
        require(amount >= 0) { "Amount must be non-negative" }

        val image = Image.findById(imageId) ?: throw IllegalArgumentException("Image with id $imageId not found")
        val trash = Trash.findById(trashName) ?: throw IllegalArgumentException("Trash with name $trashName not found")

        val now = Clock.System.now()
        return Metadata.new {
            this.amount = amount
            this.image = image
            this.trash = trash
            this.createdAt = now
            this.updatedAt = now
        }
    }

    fun findById(id: Int): Metadata? {
        return Metadata.findById(id)
    }

    fun findAll(): List<Metadata> {
        return Metadata.all().toList()
    }

    fun findByImage(imageId: String): List<Metadata> {
        val image = Image.findById(imageId) ?: throw IllegalArgumentException("Image with id $imageId not found")
        return image.metadata.toList()
    }

    fun findByTrash(trashName: String): List<Metadata> {
        val trash = Trash.findById(trashName) ?: throw IllegalArgumentException("Trash with name $trashName not found")
        return trash.metadata.toList()
    }

    fun update(id: Int, amount: Int?, imageId: String?, trashName: String?): Metadata {
        val metadata = Metadata.findById(id) ?: throw IllegalArgumentException("Metadata with id $id not found")

        amount?.let {
            require(it >= 0) { "Amount must be non-negative" }
            metadata.amount = it
        }

        imageId?.let {
            val image = Image.findById(it) ?: throw IllegalArgumentException("Image with id $it not found")
            metadata.image = image
        }

        trashName?.let {
            val trash = Trash.findById(it) ?: throw IllegalArgumentException("Trash with name $it not found")
            metadata.trash = trash
        }

        metadata.updatedAt = Clock.System.now()
        return metadata
    }

    fun delete(id: Int) {
        val metadata = Metadata.findById(id) ?: throw IllegalArgumentException("Metadata with id $id not found")
        metadata.delete()
    }
}