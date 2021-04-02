package playground.akka.persistence.part2_event_sourcing

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.event.Logging
import akka.persistence.AbstractPersistentActor
import java.io.Serializable

class PersistentActorsExercise {

    /*
        Persistent actor for a voting station
        Keep:
            - the citizens who voted
            - the pool: mapping between a candidate and the number f received votes so far

        The actor must be able to recover its state if it's shut down or restarted
     */
    data class Vote(val citizenPID: String, val candidate: String)
    data class VoteRecorded(val id: Int, val citizenPID: String, val candidate: String): Serializable
    object ShutDown

    class VotingStation : AbstractPersistentActor() {
        private val log = Logging.getLogger(this)
        private val citizensVoted = mutableListOf<String>()
        private val pool = mutableMapOf<String, Int>()
        private var latestVoteId = 0

        override fun persistenceId(): String = "voting-station-persistent"

        override fun createReceive(): Receive {
            return receiveBuilder()
                .match(Vote::class.java) {
                    /*
                        When you receive a command
                        1) you create an EVENT to persist into store
                        2) you persist the event, then pass in a callback that will get triggered once the event is written
                        3) we update the actor state when the event has persisted
                     */
                    log.info("Received Vote $it")
                    if (!citizensVoted.contains(it.citizenPID)) {
                        val voteRecorded = VoteRecorded(latestVoteId, it.citizenPID, it.candidate)
                        persist(voteRecorded) { voteRecorded ->
                            handleInternalState(voteRecorded.citizenPID, voteRecorded.candidate)
                        }
                        log.info("Persisted: $it")
                    } else {
                        log.info("Citizen with pid: ${it.citizenPID} has voted!")
                    }
                }
                .match(ShutDown::class.java) {
                    context.stop(self)
                }
                .matchEquals("print") {
                    log.info("Current state: \nCitizens:$citizensVoted\nPools:$pool")
                }
                .build()
        }

        override fun createReceiveRecover(): Receive {
            return receiveBuilder()
                .match(VoteRecorded::class.java) { voteRecorded ->
                    handleInternalState(voteRecorded.citizenPID, voteRecorded.candidate)
                    log.info("Recovered: $voteRecorded")
                }
                .build()
        }

        private fun handleInternalState(citizenPID: String, candidate: String) {
            citizensVoted.add(citizenPID)
            val candidateVoteCount = pool.getOrDefault(candidate, 0)
            pool[candidate] = (candidateVoteCount + 1)
            latestVoteId += 1
        }

    }
}

fun main() {
    val system = ActorSystem.create("PersistentActorsExercise")
    val votingStation = system.actorOf(Props.create(PersistentActorsExercise.VotingStation::class.java), "simpleVotingStation")

    val votes = mutableMapOf(
        "Alice" to "Martin",
        "Bob" to "Roland",
        "Charlie" to "Martin",
        "David" to "Jonas",
        "Daniel" to "Martin"
    )

    /*votes.forEach { citizenVote ->
        votingStation.tell(PersistentActorsExercise.Vote(citizenVote.key, citizenVote.value), ActorRef.noSender())
    }*/

    votingStation.tell(PersistentActorsExercise.Vote("Daniel", "Daniel"), ActorRef.noSender())
    votingStation.tell("print", ActorRef.noSender())
}