package playground.akka.essentials.part2actors

import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props

fun main() {
    val system = ActorSystem.create("ChangingActorBehavior")
    val fussyKid = system.actorOf(FussyKid.create(), "fussyKid")
    val mom = system.actorOf(Mom.create(), "mom")
    val statelessFussyKid = system.actorOf(StatelessFussyKid.create(), "statelessFussyKid")

    mom.tell(Mom.MomStart(statelessFussyKid), ActorRef.noSender())

    /**
     * Exercises
     * 1 - recreate the Counter Actor with context.become and nu mutable state
     * 2 - simplified voting system
     *
     */

    val counterActor2 = system.actorOf(CounterActor2.create(), "counterActor2")
    (1..5).forEach { counterActor2.tell(CounterActor2.Companion.Increment, ActorRef.noSender()) }
    (1..3).forEach { counterActor2.tell(CounterActor2.Companion.Decrement, ActorRef.noSender()) }
    counterActor2.tell(CounterActor2.Companion.Print, ActorRef.noSender())

    val alice = system.actorOf(Citizen.create(), "alice")
    val bob = system.actorOf(Citizen.create(), "bob")
    val charlie = system.actorOf(Citizen.create(), "charlie")
    val daniel = system.actorOf(Citizen.create(), "daniel")

    alice.tell(Vote("Martin"), alice)
    bob.tell(Vote("Jonas"), bob)
    charlie.tell(Vote("Roland"), charlie)
    daniel.tell(Vote("Roland"), daniel)

    val voteAggregator = system.actorOf(VoteAggregator.create(), "voteAggregator")
    voteAggregator.tell(AggregateVotes(setOf(alice, bob, charlie, daniel)), ActorRef.noSender())
    /*
        Print the status of votes

        Martin -> 1
        Jonas -> 1
        Roland -> 2
     */
}

class FussyKid : AbstractActor() {

    companion object {
        fun create(): Props {
            return Props.create(FussyKid::class.java)
        }
    }

    object KidAccept
    object KidReject

    val HAPPY = "happy"
    val SAD = "sad"
    var state = HAPPY
    override fun createReceive(): Receive {
        return receiveBuilder()
            .matchEquals(Mom.Food(Mom.VEGETABLE)) { state = SAD }
            .matchEquals(Mom.Food(Mom.CHOCOLATE)) { state = HAPPY }
            .match(Mom.Ask::class.java) {
                if (state == HAPPY) sender.tell(KidAccept, self)
                else sender.tell(KidReject, self)
            }
            .build()
    }
}

class Mom : AbstractActor() {
    companion object {
        fun create(): Props {
            return Props.create(Mom::class.java)
        }

        val VEGETABLE = "veggies"
        val CHOCOLATE = "chocolate"
    }

    data class Food(val food: String)
    data class Ask(val message: String)
    data class MomStart(val kidRef: ActorRef)

    override fun createReceive(): Receive {
        return receiveBuilder()
            .match(MomStart::class.java) {
                it.kidRef.tell(Food(VEGETABLE), self)
                it.kidRef.tell(Ask("Do you want to play?"), self)
            }
            .match(FussyKid.KidAccept::class.java) { println("Yay, my kid is happy!") }
            .match(FussyKid.KidReject::class.java) { println("My kid is sad, but at least he's health!") }
            .build()
    }
}

class StatelessFussyKid : AbstractActor() {

    companion object {
        fun create(): Props {
            return Props.create(StatelessFussyKid::class.java)
        }
    }

    override fun createReceive(): Receive = happyReceive()

    private fun happyReceive(): Receive {
        return receiveBuilder()
            .matchEquals(Mom.Food(Mom.VEGETABLE)) { context.become(sadReceive()) }
            .matchEquals(Mom.Food(Mom.CHOCOLATE)) {}
            .match(Mom.Ask::class.java) { sender.tell(FussyKid.KidAccept, self) }
            .build()
    }

    private fun sadReceive(): Receive {
        return receiveBuilder()
            .matchEquals(Mom.Food(Mom.VEGETABLE)) {}
            .matchEquals(Mom.Food(Mom.CHOCOLATE)) { context.become(happyReceive()) }
            .match(Mom.Ask::class.java) { sender.tell(FussyKid.KidReject, self) }
            .build()
    }

}

class CounterActor2() : AbstractActor() {
    companion object {
        fun create(): Props {
            return Props.create(CounterActor2::class.java)
        }

        object Increment
        object Decrement
        object Print
    }

    override fun createReceive(): Receive = countReceive(0)

    private fun countReceive(value: Int): Receive {
        return receiveBuilder()
            .match(Increment::class.java) { context.become(countReceive(value + 1)) }
            .match(Decrement::class.java) { context.become(countReceive(value - 1)) }
            .match(Print::class.java) { println("[counter] my current count is $value") }
            .build()
    }
}


data class Vote(val candidate: String)
data class AggregateVotes(val citizens: Set<ActorRef>)
data class VoteStatusReply(val candidate: String)
object VoteStatusRequest
object PrintVotingResult
class Citizen : AbstractActor() {
    companion object {
        fun create(): Props {
            return Props.create(Citizen::class.java)
        }
    }

    override fun createReceive(): Receive = citizenVoteReceive()

    private fun citizenVoteReceive(candidate: String = ""): Receive {
        return receiveBuilder()
            .match(Vote::class.java) { msg -> context.become(citizenVoteReceive(msg.candidate)) }
            .match(VoteStatusRequest::class.java) { sender.tell(VoteStatusReply(candidate), self) }
            .build()
    }
}

class VoteAggregator : AbstractActor() {
    companion object {
        fun create(): Props {
            return Props.create(VoteAggregator::class.java)
        }
    }

    override fun createReceive(): Receive = aggregatedVotesReceive(mutableMapOf(), setOf())

    private fun aggregatedVotesReceive(result: MutableMap<String, Int>, remaining: Set<ActorRef>): Receive {
        return receiveBuilder()
            .match(AggregateVotes::class.java) { citizens ->
                context.become(aggregatedVotesReceive(result, citizens.citizens))
                citizens.citizens.forEach {
                    it.tell(VoteStatusRequest, self)
                }
            }
            .match(VoteStatusReply::class.java) {
                val cvc = result.getOrDefault(it.candidate, 0)
                result[it.candidate] = cvc + 1
                val newRemaining = remaining - sender
                if (newRemaining.isEmpty())
                    println(result)
                context.become(aggregatedVotesReceive(result, newRemaining))
            }
            .build()
    }
}