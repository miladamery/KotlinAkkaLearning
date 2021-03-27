package playground.akka.essentials.part2actors.typed

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive

fun main() {
    // Distributed Word Counting

    /*
        create WordCounterMaster
        send Initialize(10) to wordCounterMaster
        send "Akka is awesome" to wordCounterMaster
            wcm will send a WordCountTask("...") to one of its children
                child replies with a WordCountReply(3) to the master
            master replies with 3 to master

        requester -> wcm -> wcw
                  <- wcm <-
     */
    // round robin logic
    // 1,2,3,4,5 and 7 tasks
    // 1,2,3,4,5,1,2

    val system = ActorSystem.create(WordCountRequester.create(), "ChildExerciseTyped")
    val texts = listOf("I Love Akka", "Scala is super dope", "yes", "me too")
    texts.forEach {
        system.tell(it)
    }
}

class WordCounterMaster private constructor(
    actorContext: ActorContext<Command>,
    private val children: List<ActorRef<WordCountTask>> = listOf(),
    private val currentChildIndex: Int = 0,
    private val requestMap: Map<Int, ActorRef<Any>> = mapOf(),
    private val currentTaskId: Int = 0
) : AbstractBehavior<Command>(actorContext) {

    companion object {
        fun create(): Behavior<Command> {
            return Behaviors.setup { ctx -> WordCounterMaster(ctx) }
        }
    }

    override fun createReceive(): Receive<Command> {
        return newReceiveBuilder()
            .onMessage(Initialize::class.java) { onInitializeCommand(it) }
            .onMessage(CountWords::class.java) { onCountCommand(it) }
            .onMessage(WordCountReply::class.java) { onWordCountReplyCommand(it) }
            .build()
    }

    private fun onInitializeCommand(initialize: Initialize): Behavior<Command> {
        context.log.info("Initializing...")
        val newChildren = mutableListOf<ActorRef<WordCountTask>>()
        (1..initialize.nChildren).forEach {
            newChildren.add(context.spawn(WordCounterWorker.create(), "worker_$it"))
        }
        return WordCounterMaster(context, newChildren.toList())
    }

    private fun onCountCommand(countWords: CountWords): Behavior<Command> {
        if (children.isNotEmpty()) {
            val workerIndex = if (currentChildIndex <= children.size - 1) currentChildIndex else 0
            context.log.info("I have received: ${countWords.text} - I will send it to child $workerIndex")
            children[workerIndex].tell(WordCountTask(currentTaskId, countWords.text, context.self))
            val newRequestMap = requestMap + (currentTaskId to countWords.replyTo)
            return WordCounterMaster(context, children, workerIndex + 1, newRequestMap, currentTaskId + 1)
        } else {
            return this
        }
    }

    private fun onWordCountReplyCommand(reply: WordCountReply): Behavior<Command> {
        context.log.info("I have received reply for task id ${reply.id} with ${reply.count}")
        requestMap[reply.id]?.tell(reply.count)
        val newRequestMap = requestMap - reply.id
        return WordCounterMaster(context, children, currentChildIndex, newRequestMap, currentTaskId)
    }
}

class WordCounterWorker private constructor(actorContext: ActorContext<WordCountTask>) :
    AbstractBehavior<WordCountTask>(actorContext) {

    companion object {
        fun create(): Behavior<WordCountTask> {
            return Behaviors.setup { ctx -> WordCounterWorker(ctx) }
        }
    }

    override fun createReceive(): Receive<WordCountTask> {
        return newReceiveBuilder()
            .onMessage(WordCountTask::class.java) { onWordCountTaskCommand(it) }
            .build()
    }

    private fun onWordCountTaskCommand(task: WordCountTask): Behavior<WordCountTask> {
        context.log.info("I have received task id ${task.id} with ${task.text}")
        val count = task.text.split(" ").size
        task.replyTo.tell(WordCountReply(task.id, count))
        return this
    }
}

class WordCountRequester private constructor(context: ActorContext<Any>) : AbstractBehavior<Any>(context) {

    companion object {
        fun create(): Behavior<Any> {
            return Behaviors.setup { ctx -> WordCountRequester(ctx) }
        }
    }

    private val wordCounterMaster: ActorRef<Command> = context.spawn(WordCounterMaster.create(), "WCM")

    init {
        context.self.tell(Initialize(3))
    }

    override fun createReceive(): Receive<Any> {
        return newReceiveBuilder()
            .onMessage(Initialize::class.java) {
                wordCounterMaster.tell(it)
                this
            }
            .onMessage(Integer::class.java) {
                context.log.info("[requester] I have received: $it")
                this
            }
            .onMessage(String::class.java) {
                wordCounterMaster.tell(CountWords(it, context.self))
                this
            }
            .build()
    }

}

interface Command
data class Initialize(val nChildren: Int) : Command
data class CountWords(val text: String, val replyTo: ActorRef<Any>) : Command
data class WordCountTask(val id: Int, val text: String, val replyTo: ActorRef<Command>) : Command
data class WordCountReply(val id: Int, val count: Int) : Command