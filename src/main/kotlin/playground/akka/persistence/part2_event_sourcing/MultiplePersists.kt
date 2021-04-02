package playground.akka.persistence.part2_event_sourcing

import akka.actor.AbstractLoggingActor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.event.Logging
import akka.persistence.AbstractPersistentActor
import java.io.Serializable
import java.util.*

class MultiplePersists {

    /*
        Diligent accountant: with every invoice, will persist TWO events
        - a tax record for the fiscal authority
        - an invoice record for personal logs or some auditing authority
     */

    // Commands
    data class Invoice(val recipient: String, val date: Date, val amount: Int)

    // Events
    data class TaxRecord(val taxId: String, val recordId: Int, val date: Date, val totalAmount: Int): Serializable
    data class InvoiceRecord(val invoiceRecordId: Int, val recipient: String, val date: Date, val amount: Int): Serializable

    class DiligentAccountant private constructor(private var taxId: String, private val taxAuthority: ActorRef) :
        AbstractPersistentActor() {
        private val log = Logging.getLogger(this)
        private var latestTaxRecordId = 0
        private var latestInvoiceRecordId = 0

        companion object {
            fun create(taxId: String, taxAuthority: ActorRef): Props {
                return Props.create(DiligentAccountant::class.java, taxId, taxAuthority)
            }
        }

        override fun persistenceId(): String = "diligent-accountant"

        override fun createReceive(): Receive {
            return receiveBuilder()
                .match(Invoice::class.java) { invoice ->
                    val taxRecord = TaxRecord(taxId, latestTaxRecordId, invoice.date, invoice.amount / 3)
                    val invoiceRecord = InvoiceRecord(latestInvoiceRecordId, invoice.recipient, invoice.date, invoice.amount)
                    persist(taxRecord) { taxRecord ->
                        taxAuthority.tell(taxRecord, self)
                        latestTaxRecordId += 1

                        persist("I hereby declare this tax record to be true and complete") { declaration ->
                            taxAuthority.tell(declaration, self)
                        }
                    }
                    persist(invoiceRecord) { invoiceRecord ->
                        taxAuthority.tell(invoiceRecord, self)
                        latestInvoiceRecordId += 1

                        persist("I hereby declare this invoice record to be true and complete") { declaration ->
                            taxAuthority.tell(declaration, self)
                        }
                    }
                }
                .build()
        }

        override fun createReceiveRecover(): Receive {
            return receiveBuilder()
                .matchAny {
                    log.info("Recovered: $it")
                }
                .build()
        }

    }

    class TaxAuthority : AbstractLoggingActor() {
        override fun createReceive(): Receive {
            return receiveBuilder()
                .matchAny {
                    log().info("Received: $it")
                }
                .build()
        }

    }
}

fun main() {
    val system = ActorSystem.create("MultiplePersistsDemo")
    val authority = system.actorOf(Props.create(MultiplePersists.TaxAuthority::class.java), "HMRC")
    val accountant = system.actorOf(MultiplePersists.DiligentAccountant.create("UK654987_45654", authority))

    accountant.tell(MultiplePersists.Invoice("The Sofa Company", Date(), 2000), ActorRef.noSender())

    /*
        The message ordering( TaxRecord -> InvoiceRecord) is GUARANTEED
     */
    /**
     * PERSISTENCE IS ALSO BASED ON MESSAGE PASSING.
     * JOURNALS ARE IMPLEMENTED VIA ACTORS
     */

    // NESTED PERSISTING

    accountant.tell(MultiplePersists.Invoice("The Super car Company", Date(), 20004302), ActorRef.noSender())
}