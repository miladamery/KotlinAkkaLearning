package playground.akka.persistence.part2_event_sourcing

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.event.Logging
import akka.persistence.AbstractPersistentActor
import java.io.Serializable
import java.util.*

class PersistentActors {

    // COMMANDS
    // Which are normal messages that we programmers send to actor
    data class Invoice(val recipient: String, val date: Date, val amount: Int)

    // EVENTS
    // The data structure that persistent actors will send to persistence store (journal)
    data class InvoiceRecorded(val id: Int, val recipient: String, val date: Date, val amount: Int): Serializable

    class Accountant : AbstractPersistentActor() {
        private val log = Logging.getLogger(this)
        private var latestInvoiceId = 0
        private var totalAmount = 0
        /**
         * The normal receive method
         */
        override fun createReceive(): Receive {
            return receiveBuilder()
                .match(Invoice::class.java) {
                    /*
                        When you receive a command
                        1) you create an EVENT to persist into store
                        2) you persist the event, then pass in a callback that will get triggered once the event is written
                        3) we update the actor state when the event has persisted
                     */
                    log.info("Receive invoice for amount ${it.amount}")
                    val event = InvoiceRecorded(latestInvoiceId, it.recipient, it.date, it.amount)
                    // persist is asynchronous
                    persist(event) { /*There is a time gap to reach here: all other messages send to this actor are stashed*/
                            persistedInvoice ->
                        // SAFE to access mutable state here
                        // This operation is Thread safe
                        // Sender ref is correct too
                        // update state
                        latestInvoiceId += 1
                        totalAmount += persistedInvoice.amount
                        log.info("Persisted $persistedInvoice as invoice ${persistedInvoice.id}, for total amount $totalAmount")
                    }

                }
                .build()
        }


        override fun persistenceId(): String = "simple-accountant"

        /**
         * Handler that will be called on recovery
         */
        override fun createReceiveRecover(): Receive {
            /*
                best practice: follow the logic in the persist steps of createReceive
             */
            return receiveBuilder()
                .match(InvoiceRecorded::class.java) {
                    latestInvoiceId += 1
                    totalAmount += it.amount
                    log.info("Recovered invoice ${it.id} for amount ${it.amount}, total amount: $totalAmount")
                }
                .build()
        }

    }
}

fun main() {
    /*
        Scenario: we have a business and an accountant which keeps track of out invoices
     */
    val system = ActorSystem.create("PersistentActors")
    val accountant = system.actorOf(Props.create(PersistentActors.Accountant::class.java), "simpleAccountant")

    /*(1..10).map {
        accountant.tell(
            PersistentActors.Invoice("The Sofa Company", Date(), it * 1000),
            ActorRef.noSender()
        )
    }*/
}