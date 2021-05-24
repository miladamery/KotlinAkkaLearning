package playground.akka.cluster.part4_advanced_clustering.cluster_singleton_exercise

import akka.actor.*
import akka.cluster.singleton.ClusterSingletonManager
import akka.cluster.singleton.ClusterSingletonManagerSettings
import akka.cluster.singleton.ClusterSingletonProxy
import akka.cluster.singleton.ClusterSingletonProxySettings
import akka.event.Logging
import com.typesafe.config.ConfigFactory
import java.io.Serializable
import java.time.Duration
import java.util.*

class VotingSystemSingleton {
    data class Person(val id: String, val age: Int) : Serializable {
        companion object {
            fun generate() = Person(UUID.randomUUID().toString(), 16 + Random().nextInt(90))
        }
    }

    data class Vote(val person: Person, val candidate: String) : Serializable
    object VoteAccepted: Serializable
    data class VoteRejected(val reason: String): Serializable
}

class VotingAggregator : AbstractActor() {
    private val log = Logging.getLogger(context.system, this)
    private val CANDIDATES = setOf("Martin", "Roland", "Jonas", "Daniel")

    override fun preStart() {
        context.receiveTimeout = Duration.ofSeconds(60)
    }

    override fun createReceive(): Receive = online(setOf(), mapOf())

    private fun online(personsVoted: Set<String>, polls: Map<String, Int>): Receive {
        return receiveBuilder()
            .match(VotingSystemSingleton.Vote::class.java) {
                if (personsVoted.contains(it.person.id))
                    sender.tell(VotingSystemSingleton.VoteRejected("Already Voted!"), self)
                else if (it.person.age < 18)
                    sender.tell(VotingSystemSingleton.VoteRejected("Not above legal voting age!"), self)
                else if (!CANDIDATES.contains(it.candidate))
                    sender.tell(VotingSystemSingleton.VoteRejected("Invalid Candidate!"), self)
                else {
                    log.info("Recording vote from person ${it.person.id} for ${it.candidate}")
                    val candidateVotes = polls.getOrDefault(it.candidate, 0)
                    sender.tell(VotingSystemSingleton.VoteAccepted, self)
                    context.become(online(personsVoted + it.person.id, polls + (it.candidate to candidateVotes + 1)))
                }

            }
            .match(ReceiveTimeout::class.java) {
                log.info("TIME's up, here are the poll results: $polls")
                context.cancelReceiveTimeout()
                context.become(offline())

            }
            .build()
    }

    private fun offline(): Receive {
        return receiveBuilder()
            .match(VotingSystemSingleton.Vote::class.java) {
                log.warning("Received vote $it, which is invalid as the time is up")
                sender.tell(VotingSystemSingleton.VoteRejected("Can not accept votes after polls closing time"), self)
            }
            .matchAny {
                log.warning("Received $it - will not process more messages after polls closing time")
            }
            .build()
    }
}

class VotingStation(val votingAggregator: ActorRef) : AbstractActor() {
    private val log = Logging.getLogger(context.system, this)
    override fun createReceive(): Receive {
        return receiveBuilder()
            .match(VotingSystemSingleton.Vote::class.java) {
                votingAggregator.tell(it, self)
            }
            .match(VotingSystemSingleton.VoteAccepted::class.java) {
                log.info("Vote was accepted")
            }
            .match(VotingSystemSingleton.VoteRejected::class.java) {
                log.info("Vote was rejected: ${it.reason}")
            }
            .build()
    }
}

fun centralElectionSystemCreator(port: Int) {
    val config = ConfigFactory.parseMap(mapOf("akka.remote.artery.canonical.port" to port))
        .withFallback(ConfigFactory.load("part4_advanced_clustering/votingSystemSingleton.conf"))
    val system = ActorSystem.create("RTJVMCluster", config)

    // TODO 1 - setup cluster singleton here
    system.actorOf(
        ClusterSingletonManager.props(
            Props.create(VotingAggregator::class.java),
            PoisonPill::class.java,
            ClusterSingletonManagerSettings.create(system)
        ), "votingAggregator"
    )
}

fun votingStationAppCreator(port: Int) {
    val config = ConfigFactory.parseMap(mapOf("akka.remote.artery.canonical.port" to port))
        .withFallback(ConfigFactory.load("part4_advanced_clustering/votingSystemSingleton.conf"))
    val system = ActorSystem.create("RTJVMCluster", config)

    // TODO 2 - setup communication to the cluster singleton
    val proxy = system.actorOf(
        ClusterSingletonProxy.props(
            "/user/votingAggregator",
            ClusterSingletonProxySettings.create(system)
        )
    )
    val votingStation = system.actorOf(Props.create(VotingStation::class.java, proxy), "votingStation")
    // TODO 3 - readlines from stdIn and send to cluster
    while (true) {
        val candidate = readLine()
        val vote = candidate?.let { VotingSystemSingleton.Vote(VotingSystemSingleton.Person.generate(), it) }
        votingStation.tell(vote, ActorRef.noSender())
    }
}

