package playground.akka.essentials.part2actors

import akka.actor.AbstractActor
import akka.actor.ActorSystem
import akka.actor.Props
import akka.japi.pf.ReceiveBuilder

fun main(args: Array<String>) {
    // part1 - actor systems
    val actorSystem = ActorSystem.create("firstActorSystem")
    println(actorSystem.name())

    // part 3
    val wordCounter = actorSystem.actorOf(Props.create(WordCountActor::class.java), "wordCounter")
    val anotherWordCounter = actorSystem.actorOf(Props.create(WordCountActor::class.java), "anotherWordCounter")
    // part 4 - communicate. this is asynch
    //wordCounter.tell("I am learning Akka and it's pretty damn cool!", wordCounter)
    wordCounter.tell(1200L , wordCounter)
    anotherWordCounter.tell("A different message!", anotherWordCounter)
}


// part2 - create actors
// word count actor
class WordCountActor : AbstractActor() {
    var totalWords = 0
    override fun createReceive(): Receive {
        return ReceiveBuilder()
            .match(String::class.java) { message ->
                println("I have received: $message")
                totalWords += message.split(" ").size
            }
            .matchAny{ message -> "[word counter] i can not understand ${message.toString()}" }
            .build()
    }

}

