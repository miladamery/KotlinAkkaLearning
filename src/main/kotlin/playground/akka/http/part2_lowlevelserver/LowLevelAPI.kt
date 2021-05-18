package playground.akka.http.part2_lowlevelserver

import akka.actor.ActorSystem
import akka.http.javadsl.ConnectHttp
import akka.http.javadsl.Http
import akka.http.javadsl.IncomingConnection
import akka.http.javadsl.model.ContentTypes
import akka.http.javadsl.model.HttpMethods
import akka.http.javadsl.model.HttpRequest
import akka.http.javadsl.model.HttpResponse
import akka.stream.ActorMaterializer
import akka.stream.javadsl.Sink
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

class LowLevelAPI {
}

fun main() {
    val system = ActorSystem.create("LowLevelAPI")
    val materializer = ActorMaterializer.create(system)
    /*val serverSource = Http.get(system).bind(ConnectHttp.toHost("localhost", 8000))
    val connectionSink = Sink.foreach<IncomingConnection> {
        println("Accepted incoming connection from: ${it.remoteAddress()}")
    }
    val serverBindingFuture = serverSource.to(connectionSink).run(system)
    val s = (serverBindingFuture as CompletableFuture).get()
    s.terminate(Duration.ofSeconds(2))
    println("Server binding successful.")*/


    /*
        Method 1: synchronously server HTTP responses
     */
    val requestHandler = { request: HttpRequest ->
        if (request.method() == HttpMethods.GET)
            HttpResponse.create().withStatus(200).withEntity(
                ContentTypes.TEXT_HTML_UTF8, """
                <html><body>Hello from Akka HTTP!</body></html>
            """.trimIndent()
            )
        else {
            request.discardEntityBytes(system)
            HttpResponse.create().withStatus(404).withEntity(
                ContentTypes.TEXT_HTML_UTF8, """
                <html><body>OOPS! the resource can't be found.</body></html>
            """.trimIndent()
            )
        }
    }

    val httpSyncConnectionHandler = Sink.foreach<IncomingConnection> {
        it.handleWithSyncHandler(requestHandler, materializer)
    }
    /*Http
        .get(system)
        .bind(ConnectHttp.toHost("localhost", 8000))
        .runWith(httpSyncConnectionHandler, system)*/

    /*
        Method 2: serve back HTTP response ASYNC
     */
    val asyncRequestHandler: (HttpRequest) -> CompletionStage<HttpResponse> = { request: HttpRequest ->
        if (request.method() == HttpMethods.GET)
            CompletableFuture.completedStage(
                HttpResponse.create().withStatus(200).withEntity(
                    ContentTypes.TEXT_HTML_UTF8, """
                <html><body>Hello from Akka HTTP!</body></html>
            """.trimIndent()
                )
            )
        else {
            request.discardEntityBytes(system)
            CompletableFuture.completedStage(
                HttpResponse.create().withStatus(404).withEntity(
                    ContentTypes.TEXT_HTML_UTF8, """
                <html><body>OOPS! the resource can't be found.</body></html>
            """.trimIndent()
                )
            )
        }
    }
    val httpAsyncConnectionHandler = Sink.foreach<IncomingConnection> {
        it.handleWithAsyncHandler(asyncRequestHandler, materializer)
    }
    Http.get(system).bind(ConnectHttp.toHost("localhost", 8000)).runWith(httpAsyncConnectionHandler, system)

}