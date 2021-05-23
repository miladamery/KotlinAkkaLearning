package playground.akka.cluster.part3_clustering.cluster_exercise

import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.cluster.Cluster
import akka.cluster.ClusterEvent
import akka.event.Logging
import akka.event.LoggingAdapter
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import java.io.Serializable

class ClusterChat {
}

object ChatDomain {
    data class ChatMessage(val nickname: String, val contents: String) : Serializable
    data class UserMessage(val contents: String) : Serializable
    data class EnterRoom(val fullAddress: String, val nickname: String) : Serializable
}

class ChatActor(val nickname: String, val port: Int) : AbstractActor() {
    private val log: LoggingAdapter = Logging.getLogger(context.system, this)
    val cluster = Cluster.get(context.system)

    val chatRoom = mutableMapOf<String, String>()

    override fun preStart() {
        cluster.subscribe(self, ClusterEvent.MemberUp::class.java, ClusterEvent.UnreachableMember::class.java)
    }

    override fun postStop() {
        cluster.unsubscribe(self)
    }

    override fun createReceive(): Receive {
        return receiveBuilder()
            .match(ClusterEvent.MemberUp::class.java) {
                // TODO("Send a special EnterRoom message to chatActor deployed on the new Node (hint: use Actor selection)")
                val enterRoomMsg = ChatDomain.EnterRoom(cluster.selfAddress().toString(), nickname)
                val chatActorSelection = context.actorSelection("${it.member().address()}/user/chatActor")
                chatActorSelection.tell(enterRoomMsg, self)

            }
            .match(ClusterEvent.MemberRemoved::class.java) {
                // TODO remove the member from your data structures
                val remoteNickname = chatRoom[it.member().address().toString()]
                log.info("$remoteNickname left the room.")
                chatRoom.remove(it.member().address().toString())
            }
            .match(ChatDomain.EnterRoom::class.java) {
                // TODO add the member to your data structures
                if (it.nickname != nickname) {
                    log.info("${it.nickname} entered the room.")
                    chatRoom[it.fullAddress] = it.nickname
                }
            }
            .match(ChatDomain.UserMessage::class.java) {
                // TODO broadcast the content ( as chat messages) to the rest of the cluster members
                chatRoom.keys.forEach { address ->
                    context
                        .actorSelection("$address/user/chatActor")
                        .tell(ChatDomain.ChatMessage(nickname, it.contents), self)
                }
            }
            .match(ChatDomain.ChatMessage::class.java) {
                log.info("[${it.nickname}] ${it.contents}")
            }
            .build()
    }
}

class ChatApp(val nickname: String, port: Int) {
    private val config: Config = ConfigFactory.parseString(
        """
        akka.remote.artery.canonical.port = $port
    """.trimIndent()
    )
        .withFallback(ConfigFactory.load("part3_clustering/clusterChat.conf"))
    private val system: ActorSystem = ActorSystem.create("RTJVMCluster", config)
    private val chatActor = system.actorOf(Props.create(ChatActor::class.java, nickname, port), "chatActor")

    fun readLineAndSendToCluster() {
        while (true) {
            print("Enter your message as $nickname: ")
            val input = readLine()
            input?.let {
                chatActor.tell(ChatDomain.UserMessage(it), ActorRef.noSender())
            }
        }
    }
}