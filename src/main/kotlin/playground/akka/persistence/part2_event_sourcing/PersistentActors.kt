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
    data class InvoiceBulk(val invoices: List<Invoice>)
    object ShutDown

    // EVENTS
    // The data structure that persistent actors will send to persistence store (journal)
    data class InvoiceRecorded(val id: Int, val recipient: String, val date: Date, val amount: Int) : Serializable

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
                .match(InvoiceBulk::class.java) { it ->
                    /*
                        1) create Events
                        2) Persis all the events
                        3) update the actor state when each event is persisted
                     */
                    // 1)
                    val invoiceIds = latestInvoiceId..(latestInvoiceId + it.invoices.size)
                    val events = it.invoices.zip(invoiceIds).map { pair ->
                        val invoice = pair.first
                        val id = pair.second
                        InvoiceRecorded(id, invoice.recipient, invoice.date, invoice.amount)
                    }
                    // 2)
                    persistAll(events) { /*3)*/invoiceRecorded ->
                        latestInvoiceId += 1
                        totalAmount += invoiceRecorded.amount
                        log.info("Persisted SINGLE $invoiceRecorded as invoice ${invoiceRecorded.id}, for total amount $totalAmount")
                    }
                }
                .match(ShutDown::class.java) {
                    // this will be put on the normal mailbox
                    // that guarantees ShutDown message will be processed after all
                    // Invoices have been correctly treated.
                    context.stop(self)
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

        /*
            This method is called if persisting failed.
            The actor will be STOPPED.

            Best practice: start the actor again after a while
            (Use Backoff supervisor)
         */
        override fun onPersistFailure(cause: Throwable?, event: Any?, seqNr: Long) {
            log.error("Fail to persist $event because of $cause")
            super.onPersistFailure(cause, event, seqNr)
        }

        /*
            Called if the JOURNAL fails to persist the event
            The actor is RESUMED.
         */
        override fun onPersistRejected(cause: Throwable?, event: Any?, seqNr: Long) {
            log.error("Persist reject for $event because of the $cause")
            super.onPersistRejected(cause, event, seqNr)
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

    /*
        Persistence Failures
     */

    /**
     * Persisting multiple events
     *
     * persistAll
     */

    val newInvoices = (1..5).map {
        PersistentActors.Invoice("The awesome chair", Date(), it * 2000)
    }
    //accountant.tell(PersistentActors.InvoiceBulk(newInvoices), ActorRef.noSender())

    /*
      NEVER EVER CALL PERSIST OR PERSISTALL FROM FUTURES. otherwise you risk to breaking
      actor encapsulation because actor thread is free to process messages while you are
      persisting. and if normal actor thread call persis you suddenly have 2 threads persisting events
      simultaneously. so because the event order is non-deterministic you risk corrupting actor state
     */

    /**
     * Shutdown of persistent actors
     * Kill or PoisonPill is not ideal/good on persistent actors
     * because you risk killing the actor before its actually done persisting.
     *
     * best practice: Define your own shutdown messages.
     */
}