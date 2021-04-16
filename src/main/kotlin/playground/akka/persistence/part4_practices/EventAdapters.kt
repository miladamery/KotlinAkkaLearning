package playground.akka.persistence.part4_practices

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.event.Logging
import akka.persistence.AbstractPersistentActor
import akka.persistence.journal.EventSeq
import akka.persistence.journal.ReadEventAdapter
import com.typesafe.config.ConfigFactory
import java.io.Serializable

class EventAdapters {

    // store for acoustic guitars
    companion object {
        val ACOUSTIC = "acoustic"
        val ELECTRIC = "electric"
    }

    data class AddGuitar(val guitar: Guitar, val quantity: Int)
    data class Guitar(val id: String, val model: String, val make: String, val guitarType: String = ACOUSTIC)
    data class GuitarAdded(
        val guitarId: String,
        val guitarModel: String,
        val guitarMake: String,
        val quantity: Int,
        val guitarType: String): Serializable
    data class GuitarAddedV2(
        val guitarId: String,
        val guitarModel: String,
        val guitarMake: String,
        val quantity: Int,
        val guitarType: String): Serializable

    class InventoryManager: AbstractPersistentActor() {
        private val log = Logging.getLogger(this)
        private val inventory = mutableMapOf<Guitar, Int>()
        override fun createReceive(): Receive {
            return receiveBuilder()
                .match(AddGuitar::class.java) {guitar ->
                    persist(GuitarAddedV2(guitar.guitar.id, guitar.guitar.model, guitar.guitar.model, guitar.quantity, guitar.guitar.guitarType)) {
                        addGuitarInventory(guitar.guitar, guitar.quantity)
                        log.info("Added ${guitar.quantity} x ${guitar.guitar} to inventory")
                    }
                }
                .matchEquals("print") {
                    log.info("Current inventory is: $inventory")
                }
                .build()
        }

        override fun persistenceId(): String = "guitar-inventory-manager"

        override fun createReceiveRecover(): Receive {
            return receiveBuilder()
                .match(GuitarAddedV2::class.java) {
                    log.info("Recovered $it")
                    val guitar = Guitar(it.guitarId, it.guitarModel, it.guitarModel, it.guitarType)
                    addGuitarInventory(guitar, it.quantity)
                }
                .build()
        }

        fun addGuitarInventory(guitar: Guitar, quantity: Int) {
            val existingQuantity = inventory.getOrDefault(guitar, 0)
            inventory[guitar] = existingQuantity + quantity
        }

    }

    class GuitarReadEventAdapter: ReadEventAdapter {
        /*
            journal -> serializer -> read event adapter -> actor
            (bytes)       (GA)             (GAV2)           (receiveRecover)
         */
        override fun fromJournal(event: Any?, manifest: String?): EventSeq {
            if (event is GuitarAdded) {
                return EventSeq.single(GuitarAddedV2(event.guitarId, event.guitarModel, event.guitarMake, event.quantity, ACOUSTIC))
            }
            return EventSeq.single(event)
        }
    }

    // WriteEventAdapter - used for backward compatibility
    // actor -> write event adapter -> serializer -> journal
    // EventAdapters
}

fun main() {
    val system = ActorSystem.create("EventAdapters", ConfigFactory.load().getConfig("eventAdapters"))
    val inventoryManager = system.actorOf(Props.create(EventAdapters.InventoryManager::class.java))

    val guitars = (1..10).map { EventAdapters.Guitar(it.toString(), "Hakker $it", "RockTheJVM") }
    guitars.forEach {
        inventoryManager.tell(EventAdapters.AddGuitar(it, 5), ActorRef.noSender())
    }
}