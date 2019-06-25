package bifrost

import bifrost.blocks.{BifrostBlock, BifrostBlockCompanion}
import bifrost.forging.ForgingSettings
import bifrost.history.{BifrostHistory, BifrostSyncInfo}
import bifrost.mempool.BifrostMemPool
import bifrost.scorexMod.GenericNodeViewHolder
import bifrost.state.BifrostState
import bifrost.transaction.box.{ArbitBox, BifrostBox}
import bifrost.wallet.BWallet
import bifrost.NodeViewModifier
import bifrost.NodeViewModifier.ModifierTypeId
import bifrost.crypto.hash.FastCryptographicHash
import bifrost.serialization.Serializer
import bifrost.transaction.Transaction
import bifrost.transaction.bifrostTransaction.{ArbitTransfer, BifrostTransaction, PolyTransfer}
import bifrost.transaction.box.proposition.{ProofOfKnowledgeProposition, PublicKey25519Proposition}
import bifrost.transaction.serialization.BifrostTransactionCompanion
import bifrost.transaction.state.{PrivateKey25519, PrivateKey25519Companion}
import bifrost.utils.ScorexLogging
import scorex.crypto.encode.Base58

class BifrostNodeViewHolder(settings: ForgingSettings)
  extends GenericNodeViewHolder[Any, ProofOfKnowledgeProposition[PrivateKey25519], BifrostTransaction, BifrostBox, BifrostBlock] {

  override val networkChunkSize: Int = settings.networkChunkSize
  override type SI = BifrostSyncInfo
  override type HIS = BifrostHistory
  override type MS = BifrostState
  override type VL = BWallet
  override type MP = BifrostMemPool

  override lazy val modifierCompanions: Map[ModifierTypeId, Serializer[_ <: NodeViewModifier]] =
    Map(BifrostBlock.ModifierTypeId -> BifrostBlockCompanion,
    Transaction.ModifierTypeId -> BifrostTransactionCompanion)

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    super.preRestart(reason, message)
    reason.printStackTrace()
    System.exit(100) // this actor shouldn't be restarted at all so kill the whole app if that happened
  }

  /**
    * Restore a local view during a node startup. If no any stored view found
    * (e.g. if it is a first launch of a node) None is to be returned
    */
  override def restoreState(): Option[NodeView] = {
    if (BWallet.exists(settings)) {
      val x = BifrostHistory.readOrGenerate(settings)
      Some(
        (
          x,
          BifrostState.readOrGenerate(settings, true, x),
          BWallet.readOrGenerate(settings, 1),
          BifrostMemPool.emptyPool
        )
      )
    } else None
  }

  //noinspection ScalaStyle
  override protected def genesisState: NodeView = {
    BifrostNodeViewHolder.initializeGenesis(settings)
  }
}

object BifrostNodeViewHolder extends ScorexLogging {
  type HIS = BifrostHistory
  type MS = BifrostState
  type VL = BWallet
  type MP = BifrostMemPool

  type NodeView = (HIS, MS, VL, MP)

  //noinspection ScalaStyle
  def initializeGenesis(settings: ForgingSettings): NodeView = {
    val GenesisAccountsNum = 45
    val GenesisBalance = 100000000L

    //propositions with wallet seed genesis-10, genesis-11, ..., genesis-14, genesis-21, genesis-22, ..., genesis-93, genesis-94
    // currently setup for up to 9 nodes controlling 5 keys each
    val icoMembers: IndexedSeq[PublicKey25519Proposition] =
      IndexedSeq(
        "FtEaU2vSnQ9js6sCuFe7jub5EdD12dtdnG5KfdZJVSKm", "AaSSiBekvtkQnr1tptbLEgXn66KbZErEwz3Athw7Pk5",
        "Ax5moP7rpdRzgRWqLVFtQUyXjMgSS8N3etgTrfcnpyKP", "GSBAUU8DpdvnefPkti5EjFCzpGFYGSgYBZyFkgCUuMG4",
        "CGSEPZDEkeFWXWt56f77bVxagKZwprwn4LH9y88YP7Tk",

        "BWyWwV1vX1UCuYUQQosrb6Ub49BTpK8ejV7s945bLaB2", "BwYvm5AM524thTKh4XkPeNj2Ahb4NtzUmtrjiLnB6sQc",
        "9HVWH1gm7mN985tPhRpTNA4iTCjp1WUsWXqaCD2z5Tzf", "DhJsgb6CMe22F1D7BG4NJHQ5i9bzhNcnpYp6HFsHpCbd",
        "J33zuAbfxPZNm8meMtqWAZxqHeVdvGAYQQ1YpgsmKQjs",

        "4cHsqa8i37vbtoq4BhBukG6WYL4z4iBqu8rexrorGJTX", "46nM2N6n6BA2zdvPBfMCh9deUjr3hF4STmyKHHdp9DE9",
        "GgRcvooDzFcWqMdTj9exG8T8THaBd6XRdyZEU8xc3eK9", "W44JCVdfTPuXVxkY3au7L5YzsQgdJeNyr1dohdXWLeS",
        "FEwtS5estvD1EmrbQBmunTU3aJoqEKbobymY17xKMbFS",

        "DDeY6fyBwbLqtFZ488jHzCrdVKRzXeqPia3ynnb1yaaf", "BbLjyu2NiYW9eA2HhbCHHbyR2M3rjgfY3AwcMZpDnp8T",
        "7MSmZF671NLXoXxgSWhpGiWGT4GNRqGuF3ohkGmSnBD8", "5xR78f8ivv7EUMxFhCeMVg3WusQrKRkRNPX8TLM22mwa",
        "4bZsyYzGL1XK4iezWUzEJR2QCbkFsJUpNtqT5KBcE1Tf",

        "7XHwFJ7XuYR8N4wTXGPvMPnHTtRF1zZXvTS7bEqiRr3k", "BRryEL7yh52CaG5eN5JmT7eYru8esfXAwzQME2wu7VWX",
        "9iqJLgmX1BhqWj964HTMAtZwsB6fFRPcFaJps1QZBJNk", "DYqwKuNark9yHAW1n5894GXh9WBRueXjdL7xMGFPvNKq",
        "6QEafzbzWcbPb7mr1ETGRvXB62UkzijFhLGa5zTxu454",

        "EeaHMMvkPaW1RYgwXXJhWD4PusyhUTi8dRdV6F273aG5", "DP9TA8Yt1dzHDaBemNqNPyR6bocySnUzAHmacWpvH5fu",
        "BYRqTQt6W2Jy6Rmd47qZ7Wg8kNi6q6bHdUDJ4E6wQH1o", "5EqdXo7gatWbuAdbLAAjCATs6AWXtSGR1PXUdRGoFekH",
        "Eyh7yLza3uP8LDn6sj8DArwroxxqm48hMHEbrAFw7mx8",

        "BXFKnUWBaPpkryWVX94hqYLkn281bpXNdv6RNWMDPhPT", "FBgcmnqbwsUL2TaUSdSKSfMoGr6BwDzPQjwnEuqGqud8",
        "FRunL2tdFv87jQRq5oKJnRLbvmRZSFrZELXYDUqGEnEo", "Dhuo1QyyJs8EyMsxWdCJ5C91A8eyzmGUdxKhULbBaqEC",
        "7vKzg6jBK4JchztSe1Rpaat53F96iAWx4HZVkzaqMhyy",


        "354znUmGvxrBVYpK1A6fnYCbvE7YEEx6pX8vUoTJCkF7", "8JsqPNscxpBwo4kLLMKT7zUSGpcCdRFs2QwGqFjJxsbR",
        "CAscbeozBbqW8wFmHmAZgZZhc5xAeSekJQzyW1JNpWBR", "5L2ezVgJuqHZ493J1LMGbiZ1fCMHm8pHBAdN8a6JeCnA",
        "59RJMuE1w9ZhXvzutVXK9kRA4ywRKSh4WrGEKoYVsuCg",


        "3Y3oarwodEovcy118GdTh7HmgiXKSs7e8UhexnVTWFzJ", "SAkudmz7K6biwzZRd6BeDiwYyfr6nw6Ss792Jbu5ECB",
        "7iVyAwixFjHAVAeUutXbYREqeVDt2uzZyF8zSRHUr8mx", "5qwCY6oXA5JsBrg16xeMwZ92MzCbxihNk5gFAAgY1AP7",
        "EJGjHZNtBBWTvMfQdCwtzEkcCayPZM7xQEKBz1pJtMar"
      ).map(s => PublicKey25519Proposition(Base58.decode(s).get))

    val genesisAccount = PrivateKey25519Companion.generateKeys("genesis".getBytes)
    val genesisAccountPriv = genesisAccount._1

    val genesisTxs = Seq(ArbitTransfer(
      IndexedSeq(genesisAccountPriv -> 0),
      icoMembers.map(_ -> GenesisBalance),
      0L,
      0L,
      "")) ++ Seq(PolyTransfer(
      IndexedSeq(genesisAccountPriv -> 0),
      icoMembers.map(_ -> GenesisBalance),
      0L,
      0L,
      ""))
    //log.debug(s"Initialize state with transaction ${genesisTxs.head} with boxes ${genesisTxs.head.newBoxes}")
    assert(icoMembers.length == GenesisAccountsNum)
    assert(Base58.encode(genesisTxs.head.id) == "2rKoaQ8iooCbi9Xpx3inThvZchKB9knWSKRZwDo26y7Y", Base58.encode(genesisTxs.head.id))

    val genesisBox = ArbitBox(genesisAccountPriv.publicImage, 0, GenesisBalance)

    val genesisBlock = BifrostBlock.create(settings.GenesisParentId, 0L, genesisTxs, genesisBox, genesisAccountPriv, 10L, settings.version) // arbitrary inflation for first block of 10 Arbits

    var history = BifrostHistory.readOrGenerate(settings)
    history = history.append(genesisBlock).get._1

    val gs = BifrostState.genesisState(settings, Seq(genesisBlock), history)
    val gw = BWallet.genesisWallet(settings, Seq(genesisBlock))

    assert(!settings.walletSeed.startsWith("genesis") || gw.boxes().flatMap(_.box match {
      case ab: ArbitBox => Some(ab.value)
      case _ => None
    }).sum >= GenesisBalance)

    gw.boxes().foreach(b => assert(gs.closedBox(b.box.id).isDefined))

    (history, gs, gw, BifrostMemPool.emptyPool)
  }
}