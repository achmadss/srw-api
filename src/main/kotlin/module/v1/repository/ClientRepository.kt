package module.v1.repository

import module.v1.model.Client
import module.v1.model.ClientTable
import org.jetbrains.exposed.v1.core.eq
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class ClientRepository {
    fun create(name: String, nfc: String, address: String): Client {
        require(name.isNotBlank()) { "Name cannot be blank" }
        require(nfc.isNotBlank()) { "NFC cannot be blank" }
        require(address.isNotBlank()) { "Address cannot be blank" }

        val existingClient = Client.find { ClientTable.nfc eq nfc }.firstOrNull()
        require(existingClient == null) { "Client with NFC '$nfc' already exists" }

        val now = Clock.System.now()
        return Client.new {
            this.name = name
            this.nfc = nfc
            this.address = address
            this.createdAt = now
            this.updatedAt = now
        }
    }

    fun findById(id: Int): Client? {
        return Client.findById(id)
    }

    fun findByNfc(nfc: String): Client? {
        return Client.find { ClientTable.nfc eq nfc }.firstOrNull()
    }

    fun findAllPaginated(page: Int, pageSize: Int): List<Client> {
        val offset = (page - 1) * pageSize
        return Client.all()
            .limit(pageSize)
            .offset(offset.toLong())
            .toList()
    }

    fun totalCount(): Int {
        return Client.count().toInt()
    }

    fun findAll(): List<Client> {
        return Client.all().toList()
    }

    fun update(id: Int, name: String?, nfc: String?, address: String?): Client {
        val client = Client.findById(id) ?: throw IllegalArgumentException("Client with id $id not found")

        name?.let {
            require(it.isNotBlank()) { "Name cannot be blank" }
            client.name = it
        }

        nfc?.let {
            require(it.isNotBlank()) { "NFC cannot be blank" }
            val existingClient = Client.find { ClientTable.nfc eq it }.firstOrNull()
            if (existingClient != null && existingClient.id.value != id) {
                throw IllegalArgumentException("Client with NFC '$it' already exists")
            }
            client.nfc = it
        }

        address?.let {
            require(it.isNotBlank()) { "Address cannot be blank" }
            client.address = it
        }

        client.updatedAt = Clock.System.now()
        return client
    }

    fun delete(id: Int) {
        val client = Client.findById(id) ?: throw IllegalArgumentException("Client with id $id not found")
        client.delete()
    }
}