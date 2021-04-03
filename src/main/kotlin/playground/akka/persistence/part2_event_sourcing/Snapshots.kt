package playground.akka.persistence.part2_event_sourcing

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.event.Logging
import akka.persistence.AbstractPersistentActor
import akka.persistence.SaveSnapshotFailure
import akka.persistence.SaveSnapshotSuccess
import akka.persistence.SnapshotOffer
import java.io.Serializable
import java.util.*

class Snapshots {

    // Commands
    data class ReceivedMessage(val contents: String) // message From your contact
    data class SentMessage(val contents: String) // message TO your contact

    // events
    data class ReceivedMessageRecord(val id: Int, val contents: String): Serializable
    data class SentMessageRecord(val id: Int, val contents: String): Serializable

    class Chat(val owner: String, val contact: String) : AbstractPersistentActor() {
        private val log = Logging.getLogger(this)
        private var currentMessageId = 0
        private val MAX_MESSAGES = 10
        private val lastMessages: Queue<Pair<String, String>> = LinkedList()
        private var commandsWithoutCheckpoint = 0

        companion object {
            fun create(owner: String, contact: String): Props {
                return Props.create(Chat::class.java, owner, contact)
            }
        }

        override fun persistenceId(): String = "$owner-$contact-chat"

        override fun createReceive(): Receive {
            return receiveBuilder()
                .match(ReceivedMessage::class.java) { receivedMessage ->
                    persist(ReceivedMessageRecord(currentMessageId, receivedMessage.contents)) {
                        log.info("Received message: ${it.contents}")
                        maybeReplaceMessage(contact, it.contents)
                        currentMessageId += 1
                        maybeCheckpoint()
                    }
                }
                .match(SentMessage::class.java) { sentMessage ->
                    persist(SentMessageRecord(currentMessageId, sentMessage.contents)) {
                        log.info("Sent message: ${it.contents}")
                        maybeReplaceMessage(owner, it.contents)
                        currentMessageId += 1
                        maybeCheckpoint()
                    }
                }
                .matchEquals("print") {
                    log.info("Most recent messages: $lastMessages")
                }
                .match(SaveSnapshotSuccess::class.java) {
                    log.info("Saving snapshot succeeded: ${it.metadata()}")
                }
                .match(SaveSnapshotFailure::class.java) {
                    log.warning("Saving snapshot ${it.metadata()} failed because of ${it.cause()}")
                }
                .build()
        }

        override fun createReceiveRecover(): Receive {
            return receiveBuilder()
                .match(ReceivedMessageRecord::class.java) {
                    log.info("Recovered received message: ${it.contents}")
                    maybeReplaceMessage(contact, it.contents)
                    currentMessageId = it.id
                }
                .match(SentMessageRecord::class.java) {
                    log.info("Recovered sent message: ${it.id}: ${it.contents}")
                    maybeReplaceMessage(owner, it.contents)
                    currentMessageId = it.id
                }
                // special recover message to recover snapshot
                .match(SnapshotOffer::class.java) {
                    log.info("Recovered snapshot")
                    (it.snapshot() as Queue<Pair<String, String>>).forEach { message ->
                        lastMessages.add(message)
                    }
                }
                .build()
        }

        private fun maybeReplaceMessage(sender: String, contents: String) {
            if (lastMessages.size >= MAX_MESSAGES)
                lastMessages.poll()
            lastMessages.add((sender to contents))
        }

        private fun maybeCheckpoint() {
            commandsWithoutCheckpoint++
            if (commandsWithoutCheckpoint >= MAX_MESSAGES) {
                log.info("Saving checkpoint....")
                saveSnapshot(lastMessages) // asynchronous operation
                // if saveSnapshot was successful you will receive a message called SaveSnapshotSuccess
                // otherwise it will receive SaveSnapshotFailure
                commandsWithoutCheckpoint = 0
            }
        }
    }
}

fun main() {
    val system = ActorSystem.create("SnapshotsDemo")
    val chat = system.actorOf(Snapshots.Chat.create("daniel123", "martin345"))

    /*(1..100000).forEach {
        chat.tell(Snapshots.ReceivedMessage("Akka Rocks $it"), ActorRef.noSender())
        chat.tell(Snapshots.SentMessage("Akka Rules $it"), ActorRef.noSender())
    }*/

    chat.tell("print", ActorRef.noSender())

    /*
        pattern:
        - after each persist, maybe save a snapshot (logic is up to you)
        - if you save a snapshot, handler the SnapshotOffer message in createReceiveRecover
        - (optional, but best practice) handle SaveSnapshotSuccess and SaveSnapshotFailure in createReceive
        - profit from the extra speed
     */
}