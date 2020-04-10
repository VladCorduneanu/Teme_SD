import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.subscribeBy
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.time.LocalDateTime
import java.util.*
import kotlin.system.exitProcess

class BiddingProcessorMicroservice {
    private var biddingProcessorSocket: ServerSocket
    private lateinit var auctioneerSocket: Socket
    private var receiveProcessedBidsObservable: Observable<String>
    private val subscriptions = CompositeDisposable()
    private val processedBidsQueue: Queue<Message> = LinkedList<Message>()
    private var taskManagerSocket: Socket

    companion object Constants {
        const val BIDDING_PROCESSOR_PORT = 1700
        const val AUCTIONEER_PORT = 1500
        const val AUCTIONEER_HOST = "localhost"
        const val TASK_PORT = 2100
    }

    init {
        biddingProcessorSocket = ServerSocket(BIDDING_PROCESSOR_PORT)
        println("BiddingProcessorMicroservice se executa pe portul: ${biddingProcessorSocket.localPort}")
        println("Se asteapta ofertele pentru finalizarea licitatiei...")


        val timestamp = LocalDateTime.now()
        taskManagerSocket = Socket(AUCTIONEER_HOST, TASK_PORT)
        taskManagerSocket.getOutputStream().write(Message.create("${taskManagerSocket.localSocketAddress}","am pornit BidderProccesor",timestamp.toString()).serialize())
        taskManagerSocket.close()

        // se asteapta mesaje primite de la MessageProcessorMicroservice
        val messageProcessorConnection = biddingProcessorSocket.accept()
        val bufferReader = BufferedReader(InputStreamReader(messageProcessorConnection.inputStream))



        // se creeaza obiectul Observable cu care se captureaza mesajele de la MessageProcessorMicroservice
        receiveProcessedBidsObservable = Observable.create<String> { emitter ->
            while (true) {
                // se citeste mesajul de la MessageProcessorMicroservice de pe socketul TCP
                val receivedMessage = bufferReader.readLine()

                // daca se primeste un mesaj gol (NULL), atunci inseamna ca cealalta parte a socket-ului a fost inchisa
                if (receivedMessage == null) {
                    // deci MessageProcessorMicroservice a fost deconectat
                    bufferReader.close()
                    messageProcessorConnection.close()

                    emitter.onError(Exception("Eroare: MessageProcessorMicroservice ${messageProcessorConnection.port} a fost deconectat."))
                    break
                }

                // daca mesajul este cel de tip „FINAL DE LISTA DE MESAJE” (avand corpul "final"), atunci se emite semnalul Complete
                if (Message.deserialize(receivedMessage.toByteArray()).body == "final") {
                    emitter.onComplete()

                    // s-au primit toate mesajele de la MessageProcessorMicroservice, i se trimite un mesaj pentru a semnala
                    // acest lucru
                    val finishedBidsMessage = Message.create(
                        "${messageProcessorConnection.localAddress}:${messageProcessorConnection.localPort}",
                        "am primit tot"
                    )

                    messageProcessorConnection.getOutputStream().write(finishedBidsMessage.serialize())
                    messageProcessorConnection.close()

                    break
                } else {
                    // se emite ce s-a citit ca si element in fluxul de mesaje
                    emitter.onNext(receivedMessage)
                }
            }
        }
    }

    private fun receiveProcessedBids() {
        // se primesc si se adauga in coada ofertele procesate de la MessageProcessorMicroservice
        val receiveProcessedBidsSubscription = receiveProcessedBidsObservable
            .subscribeBy(
                onNext = {
                    val message = Message.deserialize(it.toByteArray())
                    println(message)
                    processedBidsQueue.add(message)
                },
                onComplete = {
                    // s-a incheiat primirea tuturor mesajelor
                    // se decide castigatorul licitatiei
                    decideAuctionWinner()
                },
                onError = { println("Eroare: $it") }
            )
        subscriptions.add(receiveProcessedBidsSubscription)
    }

    private fun decideAuctionWinner() {
        // se calculeaza castigatorul ca fiind cel care a ofertat cel mai mult
        val winner: Message? = processedBidsQueue.toList().maxBy {
            // corpul mesajului e de forma "licitez <SUMA_LICITATA>"
            // se preia a doua parte, separata de spatiu
            it.body.split(" ")[1].toInt()
        }

        println("Castigatorul este: ${winner?.sender}")

        try {
            auctioneerSocket = Socket(AUCTIONEER_HOST, AUCTIONEER_PORT)

            // se trimite castigatorul catre AuctioneerMicroservice
            auctioneerSocket.getOutputStream().write(winner!!.serialize())
            auctioneerSocket.close()

            println("Am anuntat castigatorul catre AuctioneerMicroservice.")
            transmitToTaskManager("BiddingProcessor-Am terminat")
        } catch (e: Exception) {
            println("Nu ma pot conecta la Auctioneer!")
            biddingProcessorSocket.close()
            exitProcess(1)
        }
    }

    fun run() {
        receiveProcessedBids()

        // se elibereaza memoria din multimea de Subscriptions
        subscriptions.dispose()
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
    val biddingProcessorMicroservice = BiddingProcessorMicroservice()
    biddingProcessorMicroservice.run()
}