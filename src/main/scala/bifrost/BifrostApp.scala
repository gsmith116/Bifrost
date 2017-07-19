package bifrost

import java.time.Instant
import java.util.Timer
import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, Props}
import bifrost.api.http._
import bifrost.blocks.BifrostBlock
import bifrost.forging.{Forger, ForgingSettings}
import bifrost.history.{BifrostSyncInfo, BifrostSyncInfoMessageSpec}
import bifrost.network.{BifrostNodeViewSynchronizer, ProducerNotifySpec}
import bifrost.scorexMod.{GenericApplication, GenericNodeViewSynchronizer}
import bifrost.scorexMod.api.http.GenericNodeViewApiRoute
import bifrost.transaction.BifrostTransaction
import bifrost.transaction.box.BifrostBox
import com.google.protobuf.ByteString
import io.circe
import scorex.core.api.http.{ApiRoute, PeersApiRoute, UtilsApiRoute}
import scorex.core.network.NetworkController
import scorex.core.network.message.{Message, MessageSpec}
import scorex.core.transaction.box.proposition.{ProofOfKnowledgeProposition, PublicKey25519Proposition}
import scorex.core.transaction.state.PrivateKey25519
import serializer.ProducerProposal
import serializer.ProducerProposal.ProposalDetails
import serializer.ProducerProposal.ProposalDetails.{Location, ProjectDescription}

import scala.concurrent.duration.Duration
import scala.reflect.runtime.universe._

class BifrostApp(val settingsFilename: String) extends GenericApplication with Runnable {
  // use for debug only
//  val path: Path = Path ("/tmp")
//  Try(path.deleteRecursively())

  override type P = ProofOfKnowledgeProposition[PrivateKey25519]
  override type BX = BifrostBox
  override type TX = BifrostTransaction
  override type PMOD = BifrostBlock
  override type NVHT = BifrostNodeViewHolder

  implicit lazy val settings = new ForgingSettings {
    override val settingsJSON: Map[String, circe.Json] = settingsFromFile(settingsFilename)
  }
  log.debug(s"Starting application with settings \n$settings")

  override protected lazy val additionalMessageSpecs: Seq[MessageSpec[_]] = Seq(BifrostSyncInfoMessageSpec, ProducerNotifySpec)

  override val nodeViewHolderRef: ActorRef = actorSystem.actorOf(Props(new NVHT(settings)))

  override val apiRoutes: Seq[ApiRoute] = Seq(
    DebugApiRoute(settings, nodeViewHolderRef),
    WalletApiRoute(settings, nodeViewHolderRef),
    ContractApiRoute(settings, nodeViewHolderRef, networkController),
    AssetApiRoute(settings, nodeViewHolderRef),
    UtilsApiRoute(settings),
    GenericNodeViewApiRoute[P, TX](settings, nodeViewHolderRef),
    PeersApiRoute(peerManagerRef, networkController, settings)
  )

  override val apiTypes: Seq[Type] = Seq(typeOf[UtilsApiRoute], typeOf[DebugApiRoute], typeOf[WalletApiRoute],
    typeOf[ContractApiRoute], typeOf[AssetApiRoute], typeOf[GenericNodeViewApiRoute[P, TX]], typeOf[PeersApiRoute])

  val forger: ActorRef = actorSystem.actorOf(Props(classOf[Forger], settings, nodeViewHolderRef))

  override val localInterface: ActorRef = actorSystem.actorOf(
    Props(classOf[BifrostLocalInterface], nodeViewHolderRef, forger, settings)
  )

  override val nodeViewSynchronizer: ActorRef = actorSystem.actorOf(
    Props(classOf[BifrostNodeViewSynchronizer], networkController, nodeViewHolderRef, localInterface, BifrostSyncInfoMessageSpec)
  )

  //touching lazy vals
  forger
  localInterface
  nodeViewSynchronizer

  /*val scheduler = actorSystem.scheduler
  val task = new Runnable {
    def run(): Unit = {
      networkController ! Message(ProducerNotifySpec, Left(
        ProducerProposal(
          ByteString.copyFrom("testProducer".getBytes),
          ProposalDetails(assetCode = "assetCode"),
          ByteString.copyFrom("signature".getBytes),
          Instant.now.toEpochMilli
        ).toByteArray
      ), Some(null))
    }
  }
  implicit val executor = actorSystem.dispatcher

  scheduler.schedule(initialDelay = Duration(10000, TimeUnit.MILLISECONDS), interval = Duration(7000, TimeUnit.MILLISECONDS), task)*/


//  if (settings.nodeName == "node1") {
//    log.info("Starting transactions generation")
//    val generator: ActorRef = actorSystem.actorOf(Props(classOf[PolyTransferGenerator], nodeViewHolderRef))
//    generator ! StartGeneration(FiniteDuration(5, SECONDS))
//  }
}

object BifrostApp extends App {
  val settingsFilename = args.headOption.getOrElse("testnet-bifrost.json")
  new BifrostApp(settingsFilename).run()
}