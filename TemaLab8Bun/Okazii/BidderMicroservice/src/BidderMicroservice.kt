import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.kotlin.subscribeBy
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Socket
import java.time.LocalDateTime
import kotlin.Exception
import kotlin.random.Random
import kotlin.system.exitProcess

class BidderMicroservice {
    private var auctioneerSocket: Socket
    private var auctionResultObservable: Observable<String>
    private var myIdentity: String = "[BIDDER_NECONECTAT]"
    private var taskManagerSocket: Socket

    companion object Constants {
        const val AUCTIONEER_HOST = "localhost"
        const val AUCTIONEER_PORT = 1500
        const val MAX_BID = 10_000
        const val MIN_BID = 1_000
        const val TASK_PORT = 2100
    }

    init {
        try {
            auctioneerSocket = Socket(AUCTIONEER_HOST, AUCTIONEER_PORT)
            println("M-am conectat la Auctioneer!")

            myIdentity = "[${auctioneerSocket.localPort}]"

            val timestamp = LocalDateTime.now()
            taskManagerSocket = Socket(AUCTIONEER_HOST, TASK_PORT)
            taskManagerSocket.getOutputStream().write(Message.create("${taskManagerSocket.localSocketAddress}","am pornit Bidder",timestamp.toString()).serialize())
            taskManagerSocket.close()

            // se creeaza un obiect Observable ce va emite mesaje primite printr-un TCP
            // fiecare mesaj primit reprezinta un element al fluxului de date reactiv
            auctionResultObservable = Observable.create<String> { emitter ->
                // se citeste raspunsul de pe socketul TCP
                val bufferReader = BufferedReader(InputStreamReader(auctioneerSocket.inputStream))
                val receivedMessage = bufferReader.readLine()

                // daca se primeste un mesaj gol (NULL), atunci inseamna ca cealalta parte a socket-ului a fost inchisa
                if (receivedMessage == null) {
                    bufferReader.close()
                    auctioneerSocket.close()

                    emitter.onError(Exception("AuctioneerMicroservice s-a deconectat."))
                    return@create
                }

                // mesajul primit este emis in flux
                emitter.onNext(receivedMessage)

                // deoarece se asteapta un singur mesaj, in continuare se emite semnalul de incheiere al fluxului
                emitter.onComplete()

                bufferReader.close()
                auctioneerSocket.close()
            }
        } catch (e: Exception) {
            println("$myIdentity Nu ma pot conecta la Auctioneer!")
            exitProcess(1)
        }
    }

    private fun bid() {
        // se genereaza o oferta aleatorie din partea bidderului curent
        val pret = Random.nextInt(MIN_BID, MAX_BID)

        println("nume-")
        val nume = readLine()!!

        println("telefon-")
        val telefon = readLine()!!

        println("email-")
        val email = readLine()!!

        // se creeaza mesajul care incapsuleaza oferta
        val biddingMessage = Message.create("${auctioneerSocket.localAddress}:${auctioneerSocket.localPort}",
            "licitez $pret",nume,telefon,email)

        // bidder-ul trimite pretul pentru care doreste sa liciteze
        val serializedMessage = biddingMessage.serialize()
        auctioneerSocket.getOutputStream().write(serializedMessage)

        // exista o sansa din 2 ca bidder-ul sa-si trimita oferta de 2 ori, eronat
        if (Random.nextBoolean()) {
            auctioneerSocket.getOutputStream().write(serializedMessage)
        }
    }

    private fun waitForResult() {
        println("$myIdentity Astept rezultatul licitatiei...")
        // bidder-ul se inscrie pentru primirea unui raspuns la oferta trimisa de acesta
        val auctionResultSubscription = auctionResultObservable.subscribeBy(
            // cand se primeste un mesaj in flux, inseamna ca a sosit rezultatul licitatiei
            onNext = {
                val resultMessage: Message = Message.deserialize(it.toByteArray())
                println("$myIdentity Rezultat licitatie: ${resultMessage.body}")
                transmitToTaskManager("Bidder-Am terminat licitatia")
            },
            onError = {
                println("$myIdentity Eroare: $it")
            }
        )

        // se elibereaza memoria obiectului Subscription
        auctionResultSubscription.dispose()
    }

    fun run() {
        bid()
        waitForResult()
    }

    fun transmitToTaskManager(string : String)
    {
        val timestamp = LocalDateTime.now()
        taskManagerSocket = Socket(AUCTIONEER_HOST, TASK_PORT)
        taskManagerSocket.getOutputStream().write(Message.create("${taskManagerSocket.localSocketAddress}",string ,timestamp.toString()).serialize())
        taskManagerSocket.close()
    }
}

fun main(args: Array<String>) {
    val bidderMicroservice = BidderMicroservice()
    bidderMicroservice.run()
}