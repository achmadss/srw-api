package repository

import model.Client
import model.Point
import model.PointTable
import model.Submission
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import util.AesUtil
import javax.crypto.SecretKey
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class PointRepository(
    private val aesKey: SecretKey
) {
    private fun Int.encrypt(): String = AesUtil.encryptInt(this, aesKey)

    private fun String.decryptAmount(): Int = AesUtil.decryptInt(this, aesKey)

    fun Point.decryptedAmount(): Int = this.amount.decryptAmount()

    fun create(clientId: Int, amount: Int, submissionId: Int?): Point {
        val client = Client.findById(clientId) ?: throw IllegalArgumentException("Client with id $clientId not found")

        val submission = submissionId?.let {
            Submission.findById(it) ?: throw IllegalArgumentException("Submission with id $it not found")
        }

        val now = Clock.System.now()
        return Point.new {
            this.client = client
            this.submission = submission
            this.amount = amount.encrypt()
            this.createdAt = now
        }
    }

    fun findById(id: Int): Point? {
        return Point.findById(id)
    }

    fun findAll(): List<Point> {
        return Point.all().toList()
    }

    fun findByClient(clientId: Int): List<Point> {
        val client = Client.findById(clientId) ?: throw IllegalArgumentException("Client with id $clientId not found")
        return client.points.toList()
    }

    fun getClientTotalPoints(clientId: Int): Int {
        val client = Client.findById(clientId) ?: throw IllegalArgumentException("Client with id $clientId not found")
        return client.points.sumOf { it.amount.decryptAmount() }
    }

    fun findByClientPaginated(clientId: Int, page: Int, pageSize: Int): List<Point> {
        return Point.find { PointTable.client eq clientId }
            .orderBy(PointTable.createdAt to SortOrder.DESC)
            .limit(pageSize)
            .offset(((page - 1) * pageSize).toLong())
            .toList()
    }

    fun totalCountByClient(clientId: Int): Int {
        return Point.find { PointTable.client eq clientId }.count().toInt()
    }

    fun delete(id: Int) {
        val point = Point.findById(id) ?: throw IllegalArgumentException("Point with id $id not found")
        point.delete()
    }
}