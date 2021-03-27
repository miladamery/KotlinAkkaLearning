package playground.akka.essentials.part2actors.typed

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive

fun main() {

}

interface MomKidCommand
data class Food(val food: String) : MomKidCommand
data class Ask(val message: String, val replyTo: ActorRef<MomKidCommand>) : MomKidCommand
data class MomStart(val kidRef: ActorRef<MomKidCommand>) : MomKidCommand
object KidAccept : MomKidCommand
object KidReject : MomKidCommand

class FussyKid private constructor(context: ActorContext<MomKidCommand>, private val state: String = HAPPY) :
    AbstractBehavior<MomKidCommand>(context) {

    companion object {

        fun create(): Behavior<MomKidCommand> {
            return Behaviors.setup { ctx -> FussyKid(ctx) }
        }

        val HAPPY = "HAPPY"
        val SAD = "SAD"
    }

    override fun createReceive(): Receive<MomKidCommand> {
        return newReceiveBuilder()
            .onMessageEquals(Food(Mom.VEGETABLE)) { onVeggiesCommand() }
            .onMessageEquals(Food(Mom.CHOCOLATE)) { onChocoCommand() }
            .onMessage(Ask::class.java) {msg -> onAskCommand(msg) }
            .build()
    }

    private fun onVeggiesCommand(): Behavior<MomKidCommand> {
        return FussyKid(context, SAD)
    }

    private fun onChocoCommand(): Behavior<MomKidCommand> {
        return FussyKid(context, HAPPY)
    }

    private fun onAskCommand(ask: Ask): Behavior<MomKidCommand> {
        if (state == HAPPY)
            ask.replyTo.tell(KidAccept)
        else
            ask.replyTo.tell(KidReject)
        return this
    }
}

class Mom private constructor(context: ActorContext<MomKidCommand>) : AbstractBehavior<MomKidCommand>(context) {

    companion object {
        fun create(): Behavior<MomKidCommand> {
            return Behaviors.setup { ctx -> Mom(ctx) }
        }

        val VEGETABLE = "veggies"
        val CHOCOLATE = "chocolate"
    }

    override fun createReceive(): Receive<MomKidCommand> {
        return newReceiveBuilder()
            .onMessage(MomStart::class.java) {msg ->
                msg.kidRef.tell(Food(VEGETABLE))
                msg.kidRef.tell(Ask("Do you want to play?", context.self))
                this
            }
            .onMessage(KidAccept::class.java) {
                println("Yay, my kid is happy!")
                this
            }
            .onMessage(KidReject::class.java) {
                println("My kid is sad, but at least he's health!")
                this
            }
            .build()
    }

}