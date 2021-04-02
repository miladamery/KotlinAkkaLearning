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

class TypedPersistentActorsExercise {
    /*
        Persistent actor for a voting station
        Keep:
            - the citizens who voted
            - the pool: mapping between a candidate and the number f received votes so far

        The actor must be able to recover its state if it's shut down or restarted
     */
    interface Command
    object VoteResult : Command
    data class Vote(val citizenPID: String, val candidate: String) : Command, Serializable
    data class VoteStationState(val citizensVoted: List<String>, val pool: Map<String, Int>)

    class VotingStation private constructor(id: PersistenceId, val context: ActorContext<Command>) :
        EventSourcedBehavior<Command, Vote, VoteStationState>(id) {

        companion object {
            fun create(): Behavior<Command> {
                return Behaviors.setup { ctx ->
                    VotingStation(PersistenceId.ofUniqueId("voting-station-persistent-typed"), ctx)
                }
            }
        }

        override fun emptyState(): VoteStationState {
            return VoteStationState(listOf(), mapOf())
        }

        override fun commandHandler(): CommandHandler<Command, Vote, VoteStationState> {
            return newCommandHandlerBuilder()
                .forAnyState()
                .onCommand(Vote::class.java) { state, command ->
                    if (!state.citizensVoted.contains(command.citizenPID)) {
                        context.log.info("Persisted: $command")
                        Effect().persist(command)
                    } else {
                        context.log.info("Citizen with id: ${command.citizenPID} has voted!")
                        Effect().none()
                    }
                }
                .onCommand(VoteResult::class.java) { state, command ->
                    context.log.info("Current state: \nCitizens:${state.citizensVoted}\nPools:${state.pool}")
                    Effect().none()
                }
                .build()
        }

        override fun eventHandler(): EventHandler<VoteStationState, Vote> {
            return newEventHandlerBuilder()
                .forAnyState()
                .onEvent(Vote::class.java) { state, event ->
                    context.log.info("Recovered $event")
                    val candidateVotes = state.pool.getOrDefault(event.candidate, 0)
                    VoteStationState(
                        state.citizensVoted + event.citizenPID,
                        state.pool + (event.candidate to candidateVotes + 1)
                    )
                }
                .build()
        }


    }
}

fun main() {
    val system = ActorSystem.create(TypedPersistentActorsExercise.VotingStation.create(), "simpleTypedVotingStation")

    val votes = mutableMapOf(
        "Alice" to "Martin",
        "Bob" to "Roland",
        "Charlie" to "Martin",
        "David" to "Jonas",
        "Daniel" to "Martin"
    )

    votes.forEach {
        //system.tell(TypedPersistentActorsExercise.Vote(it.key, it.value))
    }
    system.tell(TypedPersistentActorsExercise.VoteResult)
}