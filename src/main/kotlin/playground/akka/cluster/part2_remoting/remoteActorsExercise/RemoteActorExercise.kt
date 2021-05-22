package playground.akka.cluster.part2_remoting.remoteActorsExercise

import akka.actor.*
import akka.event.Logging
import akka.event.LoggingAdapter
import akka.routing.RoundRobinGroup
import com.typesafe.config.ConfigFactory
import java.io.Serializable
import java.nio.file.Files
import java.nio.file.Paths

object WordCountDomain {
    data class Initialize(val nWorkers: Int)
    data class WordCountTask(val text: String): Serializable
    data class WordCountResult(val count: Int): Serializable
    object EndWordCount
}

class WordCountWorker: AbstractActor() {
    private val log: LoggingAdapter = Logging.getLogger(context.system, this)
    override fun createReceive(): Receive {
        return receiveBuilder()
            .match(WordCountDomain.WordCountTask::class.java) {
                log.info("I'm processing: ${it.text}")
                sender.tell(WordCountDomain.WordCountResult(it.text.split(" ").size), self)
            }
            .build()
    }
}

class WordCountMaster: AbstractActor() {
    private val log: LoggingAdapter = Logging.getLogger(context.system, this)

    override fun createReceive(): Receive {
        return receiveBuilder()
            .match(WordCountDomain.Initialize::class.java) {
                (1..it.nWorkers).map { index ->
                    val selection = context.actorSelection("akka://WorkerSystem@localhost:2552/user/wordCountWorker$index")
                    selection.tell(Identify(42), self)
                }
                context.become(initState(0, it.nWorkers, listOf()))
            }
            .build()
    }

    private fun initState(currentReadyWorkers: Int, totalRequiredWorkers: Int, workerActors: List<ActorRef>): Receive {
        return receiveBuilder()
            .match(ActorIdentity::class.java) {
                if (it.actorRef.isPresent) {
                    val newWorkerActors = workerActors + it.actorRef.get()
                    if (currentReadyWorkers + 1 == totalRequiredWorkers) {
                        context.become(online(newWorkerActors, 0, 0,0))
                    } else {
                        context.become(initState(currentReadyWorkers + 1, totalRequiredWorkers, newWorkerActors))
                    }
                } else {
                    log.info("No actorRef is present")
                }
            }
            .build()
    }

    private fun online(workers: List<ActorRef>, remainingTasks: Int, totalCount: Int, workerIndex: Int): Receive {
        return receiveBuilder()
            .match(String::class.java) {
                val sentences = it.split("\\. ")
                var newWorkerIndex = workerIndex
                sentences.forEach { text ->
                    workers[newWorkerIndex].tell(WordCountDomain.WordCountTask(text), self)
                    newWorkerIndex = (newWorkerIndex + 1) % workers.size
                }
                context.become(online(workers, remainingTasks + sentences.size, totalCount, newWorkerIndex))
            }
            .match(WordCountDomain.WordCountResult::class.java) {
                if (remainingTasks == 1) {
                    log.info("TOTAL RESULT: ${totalCount + it.count}")
                    workers.forEach { worker -> worker.tell(PoisonPill::class.java, self) }
                    context.stop(self)
                } else {
                    context.become(online(workers, remainingTasks - 1, totalCount + it.count, workerIndex))
                }
            }
            .build()
    }

}


class RemoteActorExercise {
}

fun main() {
    val config = ConfigFactory.parseString("""
        akka.remote.artery.canonical.port = 2551
    """.trimIndent())
        .withFallback(ConfigFactory.load("part2_remoting/remoteActorsExercise.conf"))
    val system = ActorSystem.create("MasterSystem", config)
    val master = system.actorOf(Props.create(WordCountMaster::class.java), "wordCountMaster")
    master.tell(WordCountDomain.Initialize(5), ActorRef.noSender())
    Thread.sleep(1000)

    var i = 0
    Files.lines(Paths.get("C:\\Projects\\KotlinAkkaLearning\\src\\main\\resources\\txt\\lipsum.txt")).forEach { line ->
        master.tell(line, ActorRef.noSender())
    }


}