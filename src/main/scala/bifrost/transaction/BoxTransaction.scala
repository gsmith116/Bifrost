package bifrost.transaction

import com.google.common.primitives.Longs
import bifrost.transaction.box.{Box, BoxUnlocker}
import bifrost.transaction.box.proposition.Proposition


abstract class BoxTransaction[P <: Proposition, BX <: Box[P]] extends Transaction[P] {

  val unlockers: Traversable[BoxUnlocker[P]]
  val newBoxes: Traversable[BX]

  override lazy val messageToSign: Array[Byte] =
    (if(newBoxes.nonEmpty) newBoxes.map(_.bytes).reduce(_ ++ _) else Array[Byte]()) ++
      unlockers.map(_.closedBoxId).reduce(_ ++ _) ++
      Longs.toByteArray(timestamp) ++
      Longs.toByteArray(fee)
}
