package playground.akka.persistence.part3_stores_serialization

import akka.event.Logging
import akka.persistence.*

class SimplePersistentActor : AbstractPersistentActor() {
    private val log = Logging.getLogger(this)
    var nMessage = 0

    override fun persistenceId(): String = "simple-persistent-actor"

    override fun createReceive(): Receive = receiveBuilder()
        .matchEquals("print") {
            log.info("I have persisted $nMessage so far")
        }
        .matchEquals("snap") {
            saveSnapshot(nMessage)
        }
        .match(SaveSnapshotSuccess::class.java) {
            log.info("Save snapshot was successful")
        }
        .match(SaveSnapshotFailure::class.java) {
            log.warning("Save snapshot failed: ${it.cause()}")
        }
        .matchAny {
            log.info("Persisting $it")
            nMessage += 1
        }
        .build()

    override fun createReceiveRecover(): Receive = receiveBuilder()
        .match(SnapshotOffer::class.java) {
            log.info("Recovered snapshot: ${it.snapshot()}")
            nMessage = it.snapshot() as Int
        }
        .match(RecoveryCompleted::class.java) {
            log.info("Recovery done!")
        }
        .matchAny {
            log.info("Recovered: $it")
            nMessage += 1
        }
        .build()

}