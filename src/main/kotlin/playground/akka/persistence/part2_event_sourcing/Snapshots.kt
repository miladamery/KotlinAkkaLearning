package playground.akka.persistence.part2_event_sourcing

import akka.actor.ActorSystem
import akka.actor.Props
import akka.persistence.AbstractPersistentActor
import java.util.*

class Snapshots {

    // Commands
    data class ReceivedMessage(val contents: String) // message From your contact
    data class SentMessage(val contents: String) // message TO your contact

    // events
    data class ReceivedMessageRecord(val id: Int, val contents: String)
    data class SentMessageRecord(val id: Int, val contents: String)

    class Chat(val owner: String, val contact: String): AbstractPersistentActor() {
        private var currentMessageId = 0
        private val MAX_MESSAGES = 10
        private val lastMessages: Queue<Pair<String, String>> = LinkedList()
        companion object {
            fun create(owner: String, contact: String): Props {
                return Props.create(Chat::class.java, owner, contact)
            }
        }

        override fun persistenceId(): String = "$owner-$contact-chat"

        override fun createReceive(): Receive {
            return receiveBuilder()
                .build()
        }

        override fun createReceiveRecover(): Receive {
            return receiveBuilder()
                .build()
        }

    }
}

fun main() {
    val system = ActorSystem.create("SnapshotsDemo")
    val chat = system.actorOf(Snapshots.Chat.create("daniel123", "martin345"))
}