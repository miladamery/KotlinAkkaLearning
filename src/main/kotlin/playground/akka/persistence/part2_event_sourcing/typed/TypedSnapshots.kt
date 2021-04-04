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

class TypedSnapshots {

    // Commands
    sealed class Command {
        data class ReceivedMessage(val contents: String): Command() // message From your contact
        data class SentMessage(val contents: String): Command() // message TO your contact
        object Print: Command()
    }

    sealed class Event {
        data class ReceivedMessageRecord(val id: Int, val contents: String) : Event(), Serializable
        data class SentMessageRecord(val id: Int, val contents: String) : Event(), Serializable
    }

    data class ChatSate(val currentMessageId: Int, val lastMessages: Queue<Pair<String, String>>): Serializable

    class Chat private constructor(
        private val owner: String,
        private val contact: String,
        id: PersistenceId,
        val context: ActorContext<Command>
    ) : EventSourcedBehavior<Command, Event, ChatSate>(id) {
        private val MAX_MESSAGES = 10
        companion object {
            fun create(owner: String, contact: String): Behavior<Command> {
                return Behaviors.setup { ctx ->
                    Chat(owner, contact, PersistenceId.ofUniqueId("$owner-$contact-chat"), ctx)
                }
            }
        }

        override fun emptyState(): ChatSate {
            return ChatSate(0, LinkedList())
        }

        override fun commandHandler(): CommandHandler<Command, Event, ChatSate> {
            return newCommandHandlerBuilder()
                .forAnyState()
                .onCommand(Command.ReceivedMessage::class.java) { state, command ->
                    Effect().persist(Event.ReceivedMessageRecord(state.currentMessageId, command.contents))
                }
                .onCommand(Command.SentMessage::class.java){ state, command ->
                    Effect().persist(Event.SentMessageRecord(state.currentMessageId, command.contents))
                }
                .onCommand(Command.Print::class.java) { state, command ->
                    context.log.info("Most recent messages: ${state.lastMessages}")
                    Effect().none()
                }
                .build()
        }

        override fun eventHandler(): EventHandler<ChatSate, Event> {
            return newEventHandlerBuilder()
                .forAnyState()
                .onEvent(Event.ReceivedMessageRecord::class.java) { state, event ->
                    context.log.info("Received message: ${event.contents}")
                    if (state.lastMessages.size >= MAX_MESSAGES)
                        state.lastMessages.remove()
                    state.lastMessages.add((contact to event.contents))
                    ChatSate(state.currentMessageId + 1, state.lastMessages)
                }
                .onEvent(Event.SentMessageRecord::class.java) {state, event ->
                    context.log.info("Sent message: ${event.contents}")
                    if (state.lastMessages.size >= MAX_MESSAGES)
                        state.lastMessages.remove()
                    state.lastMessages.add((owner to event.contents))
                    ChatSate(state.currentMessageId + 1, state.lastMessages)
                }
                .build()
        }

        override fun shouldSnapshot(state: ChatSate, event: Event?, sequenceNr: Long): Boolean {
            context.log.info("Saving checkpoint....")
            return state.lastMessages.size >= MAX_MESSAGES
        }
    }
}

fun main() {
    val system = ActorSystem.create(TypedSnapshots.Chat.create("Daniel", "Martin"), "TypedSnapshots")

    /*(1..100000).forEach {
        system.tell(TypedSnapshots.Command.ReceivedMessage("Akka Rocks $it"))
        system.tell(TypedSnapshots.Command.SentMessage("Akka Rules $it"))
        Thread.sleep(2)
    }*/
    system.tell(TypedSnapshots.Command.Print)
}