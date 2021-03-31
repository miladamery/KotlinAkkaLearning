package playground.akka.essentials.part5patterns

import akka.actor.AbstractLoggingActor
import akka.actor.ActorSystem
import akka.actor.Props
import akka.testkit.javadsl.TestKit
import org.junit.AfterClass
import org.junit.BeforeClass

import akka.pattern.Patterns.ask
import akka.pattern.Patterns.pipe
import org.junit.Test
import java.time.Duration
import java.util.concurrent.CompletableFuture

class AskSpec {
    companion object {
        lateinit var testKit: TestKit

        @BeforeClass
        @JvmStatic
        fun setup() {
            val system = ActorSystem.create("AskSpec")
            testKit = TestKit(system)
        }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            TestKit.shutdownActorSystem(testKit.system)
        }
    }

    data class Read(val key: String)
    data class Write(val key: String, val value: String)
    data class RegisterUser(val username: String, val password: String)
    data class Authenticate(val username: String, val password: String)
    data class AuthFailure(val message: String)
    object AuthSuccess

    // this code is somewhere else in your app
    class KVActor : AbstractLoggingActor() {
        override fun createReceive(): Receive = online(mutableMapOf())

        private fun online(kv: Map<String, String>): Receive {
            return receiveBuilder()
                .match(Read::class.java) {
                    log().info("Trying to read the value at the key ${it.key}")
                    val password = kv.getOrDefault(it.key, "")
                    sender.tell(password, self)
                }
                .match(Write::class.java) {
                    log().info("Writing the value ${it.value} for the key ${it.key}")
                    context.become(online(kv + (it.key to it.value)))
                }
                .build()
        }

    }

    open class AuthManager : AbstractLoggingActor() {

        companion object {
            const val AUTH_FAILURE_NOT_FOUND = "username not found"
            const val AUTH_FAILURE_PASSWORD_INCORRECT = "password incorrect"
        }

        protected val authDb = context.actorOf(Props.create(KVActor::class.java))
        override fun createReceive(): Receive {
            return receiveBuilder()
                .match(RegisterUser::class.java) {
                    authDb.tell(Write(it.username, it.password), self)
                }
                .match(Authenticate::class.java) { handleAuthentication(it.username, it.password) }
                .build()
        }

        protected open fun handleAuthentication(username: String, password: String) {
            log().info(sender.path().toString())
            val a = ask(authDb, Read(username), Duration.ofSeconds(2)).toCompletableFuture()
            val dbPassword = a.get() as String?
            if (dbPassword.isNullOrBlank()) sender.tell(AuthFailure(AUTH_FAILURE_NOT_FOUND), self)
            else {
                if (dbPassword == password)
                    sender.tell(AuthSuccess, self)
                else
                    sender.tell(AuthFailure(AUTH_FAILURE_PASSWORD_INCORRECT), self)
            }
        }

    }

    class PipedAuthManager : AuthManager() {
        override fun handleAuthentication(username: String, password: String) {
            // preparing result as a future:
            // 1 - we ask the actor which returns a CompletionStage - we can convert to CompletableFuture with toCompletableFuture()
            val askResult = ask(authDb, Read(username), Duration.ofSeconds(2))/*.toCompletableFuture()*/
            // we define our logic on authentication on response of ask method with .thenApply
            val authManagerResult = askResult.thenApply { dbPassword ->
                if ((dbPassword as String).isNullOrBlank()) AuthFailure(AUTH_FAILURE_NOT_FOUND)
                else {
                    if (dbPassword == password) AuthSuccess else AuthFailure(AUTH_FAILURE_PASSWORD_INCORRECT)
                }
            }

            // another way to define our logic
            /*val authManagerResult2 = ask(authDb, Read(username), Duration.ofSeconds(2))
                .thenApply {
                    val dbPassword = it as String
                    if (dbPassword.isNullOrBlank())
                        AuthFailure(AUTH_FAILURE_NOT_FOUND)
                    else {
                        if (dbPassword == password)
                            AuthSuccess
                        else
                            AuthFailure(AUTH_FAILURE_PASSWORD_INCORRECT)
                    }
                }*/
            /*
                Here we give the resulted future to pipe which means:
                When the future completes, send the response to the actor ref in the arg list
             */
            pipe(authManagerResult, context.dispatcher).to(sender)
        }
    }

    @Test
    fun `An authenticator fail to authenticate a non-registered user`() {
        testKit.run {
            val authManager = system.actorOf(Props.create(AskSpec.AuthManager::class.java))
            authManager.tell(Authenticate("daniel", "rtjvm"), testActor)
            expectMsg(AuthFailure(AuthManager.AUTH_FAILURE_NOT_FOUND))
        }
    }

    @Test
    fun `A piped authenticator fail to authenticate a non-registered user`() {
        testKit.run {
            val authManager = system.actorOf(Props.create(PipedAuthManager::class.java))
            authManager.tell(Authenticate("daniel", "rtjvm"), testActor)
            expectMsg(AuthFailure(AuthManager.AUTH_FAILURE_NOT_FOUND))
        }
    }

    @Test
    fun `An authenticator fail to authenticate if invalid password`() {
        testKit.run {
            val authManager = system.actorOf(Props.create(AskSpec.AuthManager::class.java))
            authManager.tell(RegisterUser("daniel", "rtjvm"), testActor)
            authManager.tell(Authenticate("daniel", "rtjvm2"), testActor)
            expectMsg(AuthFailure(AuthManager.AUTH_FAILURE_PASSWORD_INCORRECT))
        }
    }

    @Test
    fun `A piped authenticator fail to authenticate if invalid password`() {
        testKit.run {
            val authManager = system.actorOf(Props.create(PipedAuthManager::class.java))
            authManager.tell(RegisterUser("daniel", "rtjvm"), testActor)
            authManager.tell(Authenticate("daniel", "rtjvm2"), testActor)
            expectMsg(AuthFailure(AuthManager.AUTH_FAILURE_PASSWORD_INCORRECT))
        }
    }


}

