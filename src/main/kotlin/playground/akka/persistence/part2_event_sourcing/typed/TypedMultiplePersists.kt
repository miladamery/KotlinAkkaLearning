package playground.akka.persistence.part2_event_sourcing.typed

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.javadsl.CommandHandler
import akka.persistence.typed.javadsl.EventHandler
import akka.persistence.typed.javadsl.EventSourcedBehavior
import java.io.Serializable
import java.util.*

class TypedMultiplePersists {
    // Commands
    data class Invoice(val recipient: String, val date: Date, val amount: Int)

    // Events
    interface AccountantEvents
    data class TaxRecord(val taxId: String, val recordId: Int, val date: Date, val totalAmount: Int) : Serializable,
        AccountantEvents

    data class InvoiceRecord(val invoiceRecordId: Int, val recipient: String, val date: Date, val amount: Int) :
        Serializable, AccountantEvents

    data class AfterPersistEvent(val message: String) : Serializable, AccountantEvents

    //State
    data class AccountantState(val latestTaxRecordId: Int, val latestInvoiceRecordId: Int)

    class DiligentAccountant private constructor(
        id: PersistenceId,
        val context: ActorContext<Invoice>,
        var taxId: String
    ) : EventSourcedBehavior<Invoice, AccountantEvents, AccountantState>(id) {

        private val taxAuthority: ActorRef<Any> = context.spawn(TaxAuthority.create(), "taxAuthority")

        companion object {
            fun create(taxId: String): Behavior<Invoice> {
                return Behaviors.setup { ctx ->
                    DiligentAccountant(PersistenceId.ofUniqueId("diligent-accountant-typed"), ctx, taxId)
                }
            }
        }

        override fun emptyState(): AccountantState {
            return AccountantState(0, 0)
        }

        override fun commandHandler(): CommandHandler<Invoice, AccountantEvents, AccountantState> {
            return newCommandHandlerBuilder()
                .forAnyState()
                .onCommand(Invoice::class.java) { state, command ->
                    Effect()
                        .persist(TaxRecord(taxId, state.latestTaxRecordId, Date(), command.amount / 3))
                        .thenRun {
                            Effect().persist(AfterPersistEvent("I hereby declare this tax record to be true and complete"))
                        }
                    Effect()
                        .persist(
                            InvoiceRecord(
                                state.latestInvoiceRecordId,
                                command.recipient,
                                command.date,
                                command.amount
                            )
                        )
                        .thenRun {
                            Effect().persist(AfterPersistEvent("I hereby declare this invoice record to be true and complete"))
                        }
                }
                .build()
        }

        override fun eventHandler(): EventHandler<AccountantState, AccountantEvents> {
            return newEventHandlerBuilder()
                .forAnyState()
                .onAnyEvent { state, event ->
                    context.log.info("Event: $event")
                    taxAuthority.tell(event)
                    AccountantState(state.latestTaxRecordId + 1, state.latestInvoiceRecordId + 1)
                }
        }

    }

    class TaxAuthority(context: ActorContext<Any>) : AbstractBehavior<Any>(context) {

        companion object {
            fun create(): Behavior<Any> {
                return Behaviors.setup { ctx -> TaxAuthority(ctx) }
            }
        }

        override fun createReceive(): Receive<Any> {
            return newReceiveBuilder()
                .onAnyMessage {
                    context.log.info("Received: $it")
                    this
                }
                .build()
        }

    }
}

fun main() {
    val system = ActorSystem.create(TypedMultiplePersists.DiligentAccountant.create("UK654987_45654"), "TypedMultiplePersists")

    system.tell(TypedMultiplePersists.Invoice("The Sofa Company", Date(), 2000))
}