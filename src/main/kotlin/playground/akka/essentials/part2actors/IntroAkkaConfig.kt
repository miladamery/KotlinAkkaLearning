package playground.akka.essentials.part2actors

import akka.actor.AbstractActor
import akka.actor.ActorSystem
import akka.actor.Props
import akka.event.Logging
import akka.event.LoggingAdapter
import com.typesafe.config.ConfigFactory
import playground.akka.essentials.tell
fun main() {
    /**
     *  1- inline configuration
     */

    val configString = """
        akka {
           loglevel = "ERROR"
        }
    """.trimIndent()
    val config = ConfigFactory.parseString(configString)
    val system = ActorSystem.create("ConfigurationDemo", ConfigFactory.load(config))
    val simpleActor = system.actorOf(Props.create(SimpleLoggingActor::class.java))
    simpleActor tell "a message to remember!"

    /**
     * 2 - config file
     */
    val defaultConfigFilesystem = ActorSystem.create("DefaultConfigFileDemo")
    val defaultConfigActor = defaultConfigFilesystem.actorOf(Props.create(SimpleLoggingActor::class.java))
    defaultConfigActor tell "Remember me"

    /**
     * 3 - Separate config in the same file
     */
    val specialConfig = ConfigFactory.load().getConfig("mySpecialConfig")
    val specialConfigSystem = ActorSystem.create("SpecialConfigDemo", specialConfig)
    val specialConfigActor = specialConfigSystem.actorOf(Props.create(SimpleLoggingActor::class.java))
    specialConfigActor tell "Remember me, I am special"

    /**
     * 4 - Separate configs in another files
     */
    val separateConfig = ConfigFactory.load("secretFolder/secretConfiguration.conf")
    println("separate config log level ${separateConfig.getString("akka.loglevel")}")

    /**
     * 5 - different file formats
     * JSON, Properties
     */
    val jsonConfig = ConfigFactory.load("json/jsonConfig.json")
    println("json config: ${jsonConfig.getString("aJsonProperty")}")
    println("json config: ${jsonConfig.getString("akka.loglevel")}")

    val propsConfig = ConfigFactory.load("props/propsConfiguration.properties")
    println("properties config: ${propsConfig.getString("my.simpleProperty")}")
    println("properties config: ${propsConfig.getString("akka.loglevel")}")
}

class SimpleLoggingActor: AbstractActor() {
    private val logger: LoggingAdapter = Logging.getLogger(context.system, this)

    override fun createReceive(): Receive {
        return receiveBuilder()
            .matchAny() {
                logger.info(it.toString())
            }
            .build()
    }
}