package bifrost.api

import java.io.File

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{HttpEntity, HttpMethods, HttpRequest, MediaTypes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.pattern.ask
import akka.util.{ByteString, Timeout}
import bifrost.api.http.WalletApiRoute
import bifrost.history.BifrostHistory
import bifrost.mempool.BifrostMemPool
import bifrost.scorexMod.GenericNodeViewHolder.{CurrentView, GetCurrentView}
import bifrost.state.BifrostState
import bifrost.transaction.bifrostTransaction.BifrostTransaction
import bifrost.wallet.BWallet
import bifrost.{BifrostGenerators, BifrostNodeViewHolder}
import io.circe.parser.parse
import org.scalatest.{Matchers, WordSpec}
import scorex.crypto.encode.Base58

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.reflect.io.Path
import scala.util.Try


class WalletRPCSpec extends WordSpec
  with Matchers
  with ScalatestRouteTest
  with BifrostGenerators {

  val path: Path = Path("/tmp/bifrost/test-data")
  Try(path.deleteRecursively())

  val actorSystem = ActorSystem(settings.agentName)
  val nodeViewHolderRef: ActorRef = actorSystem.actorOf(Props(new BifrostNodeViewHolder(settings)))
  nodeViewHolderRef
  val route = WalletApiRoute(settings, nodeViewHolderRef).route

  def httpPOST(jsonRequest: ByteString): HttpRequest = {
    HttpRequest(
      HttpMethods.POST,
      uri = "/wallet/",
      entity = HttpEntity(MediaTypes.`application/json`, jsonRequest)
    ).withHeaders(RawHeader("api_key", "test_key"))
  }

  implicit val timeout = Timeout(10.seconds)

  private def view() = Await.result((nodeViewHolderRef ? GetCurrentView)
    .mapTo[CurrentView[BifrostHistory, BifrostState, BWallet, BifrostMemPool]], 10.seconds)

  val publicKeys = Map(
    "investor" -> "6sYyiTguyQ455w2dGEaNbrwkAWAEYV1Zk6FtZMknWDKQ",
    "producer" -> "A9vRt6hw7w4c7b4qEkQHYptpqBGpKM5MGoXyrkGCbrfb",
    "hub" -> "F6ABtYMsJABDLH2aj7XVPwQr5mH7ycsCE4QGQrLeB3xU"
  )

  var newPubKey: String = ""

  // Unlock Secrets
  val gw: BWallet = view().vault
  gw.unlockKeyFile(publicKeys("investor"), "genesis")
  gw.unlockKeyFile(publicKeys("producer"), "genesis")
  gw.unlockKeyFile(publicKeys("hub"), "genesis")

  "Wallet RPC" should {
    "Get balances" in {
      val requestBody = ByteString(
        s"""
           |{
           |   "jsonrpc": "2.0",
           |   "id": "1",
           |   "method": "balances",
           |   "params": [{}]
           |}
        """.stripMargin)

      httpPOST(requestBody) ~> route ~> check {
        val res = parse(responseAs[String]).right.get
        (res \\ "error").isEmpty shouldBe true
        (res \\ "result").head.asObject.isDefined shouldBe true
      }
    }

    "Get balances by public key" in {
      val requestBody = ByteString(
        s"""
           |{
           |   "jsonrpc": "2.0",
           |   "id": "1",
           |   "method": "balances",
           |   "params": [{"publicKey": "${publicKeys("hub")}"}]
           |}
        """.stripMargin)

      httpPOST(requestBody) ~> route ~> check {
        val res = parse(responseAs[String]).right.get
        (res \\ "error").isEmpty shouldBe true
        (res \\ "result").head.asObject.isDefined shouldBe true
        val boxes = ((res \\ "result").head \\ "boxes").head.asArray
        boxes.get.foreach(b => (b \\ "proposition").head.asString.get shouldEqual publicKeys("investor"))
      }
    }

    "Transfer some arbits" in {
      val requestBody = ByteString(
        s"""
           |{
           |   "jsonrpc": "2.0",
           |   "id": "1",
           |   "method": "transferArbits",
           |   "params": [{
           |      "sender": ["${publicKeys("investor")}", "${publicKeys("hub")}", "${publicKeys("producer")}"],
           |     "recipient": "${publicKeys("producer")}",
           |     "amount": 5,
           |     "fee": 0,
           |     "data": ""
           |   }]
           |}
        """.stripMargin)

      httpPOST(requestBody) ~> route ~> check {
        val res = parse(responseAs[String]).right.get
        (res \\ "error").isEmpty shouldBe true
        (res \\ "result").head.asObject.isDefined shouldBe true
        //Removing transaction from mempool so as not to affect ProgramRPC tests
        val txHash = ((res \\ "result").head \\ "txHash").head.asString.get
        val txInstance: BifrostTransaction = view().pool.getById(Base58.decode(txHash).get).get
        view().pool.remove(txInstance)
      }
    }

    "Create transfer arbits prototype" in {
      val requestBody = ByteString(
        s"""
           |{
           |   "jsonrpc": "2.0",
           |   "id": "1",
           |   "method": "transferArbitsPrototype",
           |   "params": [{
           |   "sender": ["${publicKeys("investor")}", "${publicKeys("hub")}", "${publicKeys("producer")}"],
           |     "recipient": "${publicKeys("producer")}",
           |     "amount": 5,
           |     "fee": 0,
           |     "data": ""
           |   }]
           |}
        """.stripMargin)

      httpPOST(requestBody) ~> route ~> check {
        val res = parse(responseAs[String]).right.get
        (res \\ "error").isEmpty shouldBe true
        (res \\ "result").head.asObject.isDefined shouldBe true
      }
    }

    "Transfer some polys" in {
      val requestBody = ByteString(
        s"""
           |{
           |   "jsonrpc": "2.0",
           |   "id": "1",
           |   "method": "transferPolys",
           |   "params": [{
           |      "sender": ["${publicKeys("investor")}", "${publicKeys("hub")}", "${publicKeys("producer")}"],
           |     "recipient": "${publicKeys("investor")}",
           |     "amount": 5,
           |     "fee": 0,
           |     "data": ""
           |   }]
           |}
        """.stripMargin)

      httpPOST(requestBody) ~> route ~> check {
        val res = parse(responseAs[String]).right.get
        (res \\ "error").isEmpty shouldBe true
        (res \\ "result").head.asObject.isDefined shouldBe true
        val txHash = ((res \\ "result").head \\ "txHash").head.asString.get
        val txInstance: BifrostTransaction = view().pool.getById(Base58.decode(txHash).get).get
        view().pool.remove(txInstance)
      }
    }

    "Create transfer polys prototype" in {
      val requestBody = ByteString(
        s"""
           |{
           |   "jsonrpc": "2.0",
           |   "id": "1",
           |   "method": "transferPolysPrototype",
           |   "params": [{
           |   "sender": ["${publicKeys("investor")}", "${publicKeys("hub")}", "${publicKeys("producer")}"],
           |     "recipient": "${publicKeys("producer")}",
           |     "amount": 5,
           |     "fee": 0,
           |     "data": ""
           |   }]
           |}
        """.stripMargin)

      httpPOST(requestBody) ~> route ~> check {
        val res = parse(responseAs[String]).right.get
        (res \\ "error").isEmpty shouldBe true
        (res \\ "result").head.asObject.isDefined shouldBe true
      }
    }

    "Get open keyfiles" in {
      val requestBody = ByteString(
        s"""
           |{
           |   "jsonrpc": "2.0",
           |   "id": "1",
           |   "method": "listOpenKeyfiles",
           |   "params": [{}]
           |}
        """.stripMargin)

      httpPOST(requestBody) ~> route ~> check {
        val res = parse(responseAs[String]).right.get
        (res \\ "error").isEmpty shouldBe true
        (res \\ "result").head.asArray.isDefined shouldBe true
        (res \\ "result").head.as[List[String]].right.get == publicKeys.values.toList shouldBe true
      }
    }

    "Generate a keyfile" in {
      val requestBody = ByteString(
        s"""
           |{
           |   "jsonrpc": "2.0",
           |   "id": "1",
           |   "method": "generateKeyfile",
           |   "params": [{
           |     "password": "testpassword"
           |   }]
           |}
        """.stripMargin)

      httpPOST(requestBody) ~> route ~> check {
        val res = parse(responseAs[String]).right.get
        (res \\ "error").isEmpty shouldBe true
        (res \\ "result").head.asObject.isDefined shouldBe true
        newPubKey = ((res \\ "result").head \\ "publicKey").head.asString.get
      }
    }

    "Lock a keyfile" in {
      val requestBody = ByteString(
        s"""
           |{
           |   "jsonrpc": "2.0",
           |   "id": "1",
           |   "method": "lockKeyfile",
           |   "params": [{
           |     "publicKey": "${newPubKey}",
           |     "password": "testpassword"
           |   }]
           |}
        """.stripMargin)

      httpPOST(requestBody) ~> route ~> check {
        val res = parse(responseAs[String]).right.get
        (res \\ "error").isEmpty shouldBe true
        (res \\ "result").head.asObject.isDefined shouldBe true
      }
    }

    "Unlock a keyfile" in {
      val requestBody = ByteString(
        s"""
           |{
           |   "jsonrpc": "2.0",
           |   "id": "1",
           |   "method": "unlockKeyfile",
           |   "params": [{
           |     "publicKey": "${newPubKey}",
           |     "password": "testpassword"
           |   }]
           |}
        """.stripMargin)

      httpPOST(requestBody) ~> route ~> check {
        val res = parse(responseAs[String]).right.get
        (res \\ "error").isEmpty shouldBe true
        (res \\ "result").head.asObject.isDefined shouldBe true

        //Manually deleting any newly created keyfiles from test keyfile directory (keyfiles/node1) except for the
        //investor, producer and hub keyfiles
        var d = new File("keyfiles/node1")
        d.listFiles.foreach(x =>
          if(x.toString != "keyfiles/node1/2018-07-06T15-51-30Z-6sYyiTguyQ455w2dGEaNbrwkAWAEYV1Zk6FtZMknWDKQ.json" &&
          x.toString != "keyfiles/node1/2018-07-06T15-51-35Z-F6ABtYMsJABDLH2aj7XVPwQr5mH7ycsCE4QGQrLeB3xU.json" &&
          x.toString != "keyfiles/node1/2018-07-06T15-51-33Z-A9vRt6hw7w4c7b4qEkQHYptpqBGpKM5MGoXyrkGCbrfb.json") {
            val tempFile = new File(x.toString)
            tempFile.delete()
          }
        )
      }
    }
  }


  object WalletRPCSpec {
    val path: Path = Path("/tmp/bifrost/test-data")
    Try(path.deleteRecursively())
  }
}