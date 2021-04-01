package playground.akka.persistence.part2_event_sourcing.typed

import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.javadsl.CommandHandler
import akka.persistence.typed.javadsl.EventHandler
import akka.persistence.typed.javadsl.EventSourcedBehavior
import java.io.Serializable
import java.util.*

class PersistentActors {

    class Accountant private constructor(val context: ActorContext<Command>, persistenceId: PersistenceId) :
        EventSourcedBehavior<Accountant.Command, Accountant.Event, Accountant.State>(persistenceId) {
        interface Command
        data class Invoice(val recipient: String, val date: Date, val amount: Int) : Command

        interface Event
        data class InvoiceRecorded(val id: Int, val recipient: String, val date: Date, val amount: Int) : Event,
            Serializable

        data class State(var latestInvoiceId: Int, var totalAmount: Int)

        companion object {
            fun create(): Behavior<Command> {
                return Behaviors.setup { ctx -> Accountant(ctx, PersistenceId.ofUniqueId("simple-accountant-typed")) }
            }
        }

        override fun emptyState(): State {
            return State(0, 0)
        }

        override fun commandHandler(): CommandHandler<Command, Event, State> {
            return newCommandHandlerBuilder()
                .forAnyState()
                .onCommand(Invoice::class.java) { state, command ->
                    context.log.info("Receive invoice for amount ${command.amount}")
                    Effect().persist(
                        InvoiceRecorded(
                            state.latestInvoiceId,
                            command.recipient,
                            command.date,
                            command.amount
                        )
                    )
                }
                .build()
        }

        override fun eventHandler(): EventHandler<State, Event> {
            return newEventHandlerBuilder()
                .forAnyState()
                .onEvent(InvoiceRecorded::class.java) { state, event ->
                    context.log.info("Persisted $event as invoice ${event.id}, for total amount ${state.totalAmount + event.amount}")
                    State(state.latestInvoiceId + 1, state.totalAmount + event.amount)
                }
                .build()
        }
    }
}

fun main() {
    val system = ActorSystem.create(PersistentActors.Accountant.create(), "PersistentActorsTypedDemo")
    /*(1..10).map {
        system.tell(PersistentActors.Accountant.Invoice("The sofa company", Date(), it * 1000))
    }*/
}