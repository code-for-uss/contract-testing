import Configuration.{FEE, ergoClient}
import DexyContracts.dexyAddresses
import org.ergoplatform.appkit.{Address, BlockchainContext, ErgoToken, ErgoValue, JavaHelpers, SignedTransaction}
import org.ergoplatform.appkit.impl.ErgoTreeContract

import java.math.BigInteger
import scorex.util.encode.Base16
import sigmastate.basics.DLogProtocol.DLogProverInput

import scala.collection.JavaConverters._

object DexyTokenObj {
  val oracleNFT = new ErgoToken(DexyToken.oracleNFT, 1) // TODO replace with actual
  val interventionNFT = new ErgoToken(DexyToken.interventionNFT, 1) // TODO replace with actual
  val freeMintNFT = new ErgoToken(DexyToken.freeMintNFT, 1) // TODO replace with actual
  val lpNFT = new ErgoToken(DexyToken.lpNFT, 1) // TODO replace with actual
  val bankNFT = new ErgoToken(DexyToken.bankNFT, 1) // TODO replace with actual
  val arbitrageMintNFT = new ErgoToken(DexyToken.arbitrageMintNFT, 1) // TODO replace with actual

  val dexyUSDToken = new ErgoToken(DexyToken.dexyUSDToken, 1e15.toLong) // TODO replace with actual
  val LPToken = new ErgoToken(DexyToken.LPToken, 1e15.toLong) // TODO replace with actual
}

object ContractTesting {

  def main(args: Array[String]): Unit = {
    testMint()
  }

  /**
   * Generate a secure random bigint
   * @return BigInt
   */
  def randBigInt: BigInt = {
    val secureRandom = new java.security.SecureRandom
    new BigInteger(256, secureRandom)
  }

  def toHexString(array: Array[Byte]): String = Base16.encode(array)

  def getAddressFromSk(sk: BigInteger) = new Address(JavaHelpers.createP2PKAddress(DLogProverInput(sk).publicImage, Configuration.addressEncoder.networkPrefix))

  def getPkFromSk(sk: String): (Address, String, Array[Byte]) = {
    val skBig = BigInt(sk, 16)
    val address: Address = getAddressFromSk(skBig.bigInteger)
    (address, address.toString, address.getPublicKey.pkBytes)
  }


  def testMint(): Unit = {
    // ALL TX AND TOKEN IDs ARE FAKE
    ergoClient.execute(ctx => {
      println(s"The contract's Bank address is: ${dexyAddresses.bankAddress.toString}")
      println(s"The contract's FreeMint address is: ${dexyAddresses.freeMintAddress.toString}")
      println(s"The contract's ArbitrageMint address is: ${dexyAddresses.arbitrageMintAddress.toString}")
      freeMint(ctx)
      arbitrageMint(ctx)
    })
  }

  def freeMint(ctx: BlockchainContext): Unit = {
    println("---- Start FreeMint scenario !! ----")
    val ORACLE_ERG_USD_PRICE = 584663187L
    val RESET_HEIGHT: Int = 1000000
    val REMAINING_DEXY_USD: Long = 1e15.toLong
    val WANT_DEXY = 2000L
    val feeNum = 10
    val feeDenom = 1000
    val NEEDED_ERG = WANT_DEXY * ORACLE_ERG_USD_PRICE * (feeNum + feeDenom) / feeDenom
    val DEXY_RESERVES = 18508L
    val USER_FOUND: Long = 4000e9.toLong
    val BASE_FREE_MINT_VALUE: Long = 2e9.toLong
    val BASE_LP_BOX_VALUE: Long = 10806126325195L
    val DEXY_FEE: Long = 1e9.toLong // TODO: This value is just for testing purpose and isn't true

    // dummy prover with dummy secret
    val prover = ctx.newProverBuilder()
      .withDLogSecret(BigInt.apply(0).bigInteger)
      .build()

    val tb = ctx.newTxBuilder()
    val lpBox = tb.outBoxBuilder()
      .contract(new ErgoTreeContract(dexyAddresses.lpAddress.script))
      .value(BASE_LP_BOX_VALUE)
      .tokens(DexyTokenObj.lpNFT, DexyTokenObj.LPToken, new ErgoToken(DexyToken.dexyUSDToken, DEXY_RESERVES))
      .build()
      .convertToInputWith("adfd114c8b145dd758248c9dadf818927da3a7cd56bbe4129ba053d1ce0cbc30", 0)

    val oracleBox = tb.outBoxBuilder()
      .contract(new ErgoTreeContract(Address.create("4MQyML64GnzMxZgm").getErgoAddress.script))
      .value(2e9.toLong)
      .tokens(DexyTokenObj.oracleNFT)
      .registers(ErgoValue.of(ORACLE_ERG_USD_PRICE))
      .build()
      .convertToInputWith("96be1e2f79bfb4ef587b4480966efe9e90f0dddd89c616a1f65222ea2ac4e351", 0)

    val bankBox = tb.outBoxBuilder()
      .contract(new ErgoTreeContract(dexyAddresses.bankAddress.script))
      .value((11610).toLong)
      .tokens(DexyTokenObj.bankNFT, DexyTokenObj.dexyUSDToken)
      .build()
      .convertToInputWith("ce552663312afc2379a91f803c93e2b10b424f176fbc930055c10def2fd88a5d", 0)

    val freeMintBox = tb.outBoxBuilder()
      .contract(new ErgoTreeContract(dexyAddresses.freeMintAddress.script))
      .value(BASE_FREE_MINT_VALUE)
      .tokens(DexyTokenObj.freeMintNFT)
      .registers(ErgoValue.of(RESET_HEIGHT), ErgoValue.of(REMAINING_DEXY_USD))
      .build()
      .convertToInputWith("55c0537aafa6932c9a25c856fcd064d317af96a4fa733012c26d836bbaf5689a", 1)

    val userInBox = tb.outBoxBuilder()
      .contract(new ErgoTreeContract(Address.create("4MQyML64GnzMxZgm").getErgoAddress.script))
      .value(USER_FOUND)
      .build()
      .convertToInputWith("b84e555e05181c1c8cf661535bb197853fe4ec2299f2fd399ba7bb1e73f5e69b", 0)

    val newBankBox = tb.outBoxBuilder()
      .contract(new ErgoTreeContract(dexyAddresses.bankAddress.script))
      .value(bankBox.getValue + NEEDED_ERG)
      .tokens(DexyTokenObj.bankNFT, new ErgoToken(DexyToken.dexyUSDToken, bankBox.getTokens.get(1).getValue - WANT_DEXY))
      .build()

    val newFreeMintBox = tb.outBoxBuilder()
      .contract(new ErgoTreeContract(dexyAddresses.freeMintAddress.script))
      .value(BASE_FREE_MINT_VALUE + DEXY_FEE)
      .tokens(DexyTokenObj.freeMintNFT)
      .registers(ErgoValue.of(RESET_HEIGHT), ErgoValue.of(REMAINING_DEXY_USD - WANT_DEXY))
      .build()

    val userOutBox = tb.outBoxBuilder()
      .contract(new ErgoTreeContract(Address.create("4MQyML64GnzMxZgm").getErgoAddress.script))
      .value(USER_FOUND - NEEDED_ERG - FEE - DEXY_FEE)
      .tokens(new ErgoToken(DexyToken.dexyUSDToken, WANT_DEXY))
      .build()

    val tx = tb.boxesToSpend(Seq(
      freeMintBox,
      bankBox,
      userInBox
    ).asJava)
      .fee(FEE)
      .outputs(newFreeMintBox, newBankBox, userOutBox)
      .withDataInputs(List(oracleBox, lpBox).asJava)
      .sendChangeTo(Address.create("4MQyML64GnzMxZgm").getErgoAddress)
      .build()

    var signed: SignedTransaction = null
    try {
      signed = prover.sign(tx)
    } catch {
      case e: Exception => {
        println("User could not sing tx due to:")
        println(e)
        return
      }
    }
    println(s"signed tx: ${signed.toJson(false)}")
    println("---- FreeMint scenario Done!! ----")
  }

  def arbitrageMint(ctx: BlockchainContext): Unit = {
    println("---- Start ArbitrageMint scenario !! ----")
    val ORACLE_ERG_USD_PRICE = 584663187L
    val RESET_HEIGHT: Int = 1000000
    val REMAINING_DEXY_USD: Long = 1e15.toLong
    val WANT_DEXY = 2000L
    val feeNum = 5
    val feeDenom = 1000
    val NEEDED_ERG: Long = WANT_DEXY*ORACLE_ERG_USD_PRICE * (feeDenom+feeNum+1)/feeDenom
    val DEXY_RESERVES = 16000L
    val USER_FOUND: Long = 4000e9.toLong
    val BASE_FREE_MINT_VALUE: Long = 2e9.toLong
    val BASE_LP_BOX_VALUE: Long = 10806126325195L
    val DEXY_FEE: Long = 1e9.toLong // TODO: This value is just for testing purpose and isn't true
    val LB_BOX_R5: Int = 859700
    // dummy prover with dummy secret
    val prover = ctx.newProverBuilder()
      .withDLogSecret(BigInt.apply(0).bigInteger)
      .build()

    val tb = ctx.newTxBuilder()
    val lpBox = tb.outBoxBuilder()
      .contract(new ErgoTreeContract(dexyAddresses.lpAddress.script))
      .value(BASE_LP_BOX_VALUE)
      .tokens(DexyTokenObj.lpNFT, DexyTokenObj.LPToken, new ErgoToken(DexyToken.dexyUSDToken, DEXY_RESERVES))
      .registers(ErgoValue.of(ORACLE_ERG_USD_PRICE),ErgoValue.of(LB_BOX_R5))
      .build()
      .convertToInputWith("adfd114c8b145dd758248c9dadf818927da3a7cd56bbe4129ba053d1ce0cbc30", 0)

    val oracleBox = tb.outBoxBuilder()
      .contract(new ErgoTreeContract(Address.create("4MQyML64GnzMxZgm").getErgoAddress.script))
      .value(2e9.toLong)
      .tokens(DexyTokenObj.oracleNFT)
      .registers(ErgoValue.of(ORACLE_ERG_USD_PRICE))
      .build()
      .convertToInputWith("96be1e2f79bfb4ef587b4480966efe9e90f0dddd89c616a1f65222ea2ac4e351", 0)

    val bankBox = tb.outBoxBuilder()
      .contract(new ErgoTreeContract(dexyAddresses.bankAddress.script))
      .value((11610).toLong)
      .tokens(DexyTokenObj.bankNFT, DexyTokenObj.dexyUSDToken)
      .build()
      .convertToInputWith("ce552663312afc2379a91f803c93e2b10b424f176fbc930055c10def2fd88a5d", 0)

    val arbitrageMintBox = tb.outBoxBuilder()
      .contract(new ErgoTreeContract(dexyAddresses.arbitrageMintAddress.script))
      .value(BASE_FREE_MINT_VALUE)
      .tokens(DexyTokenObj.arbitrageMintNFT)
      .registers(ErgoValue.of(RESET_HEIGHT), ErgoValue.of(REMAINING_DEXY_USD))
      .build()
      .convertToInputWith("55c0537aafa6932c9a25c856fcd064d317af96a4fa733012c26d836bbaf5689a", 1)

    val userInBox = tb.outBoxBuilder()
      .contract(new ErgoTreeContract(Address.create("4MQyML64GnzMxZgm").getErgoAddress.script))
      .value(USER_FOUND)
      .build()
      .convertToInputWith("b84e555e05181c1c8cf661535bb197853fe4ec2299f2fd399ba7bb1e73f5e69b", 0)

    val newBankBox = tb.outBoxBuilder()
      .contract(new ErgoTreeContract(dexyAddresses.bankAddress.script))
      .value(bankBox.getValue + NEEDED_ERG)
      .tokens(DexyTokenObj.bankNFT, new ErgoToken(DexyToken.dexyUSDToken, bankBox.getTokens.get(1).getValue - WANT_DEXY))
      .build()

    val newArbitrageMintBox = tb.outBoxBuilder()
      .contract(new ErgoTreeContract(dexyAddresses.arbitrageMintAddress.script))
      .value(BASE_FREE_MINT_VALUE + DEXY_FEE)
      .tokens(DexyTokenObj.arbitrageMintNFT)
      .registers(ErgoValue.of(RESET_HEIGHT), ErgoValue.of(REMAINING_DEXY_USD - WANT_DEXY))
      .build()

    val userOutBox = tb.outBoxBuilder()
      .contract(new ErgoTreeContract(Address.create("4MQyML64GnzMxZgm").getErgoAddress.script))
      .value(USER_FOUND - NEEDED_ERG - FEE - DEXY_FEE)
      .tokens(new ErgoToken(DexyToken.dexyUSDToken, WANT_DEXY))
      .build()

    val tx = tb.boxesToSpend(Seq(
      arbitrageMintBox,
      bankBox,
      userInBox
    ).asJava)
      .fee(FEE)
      .outputs(newArbitrageMintBox, newBankBox, userOutBox)
      .withDataInputs(List(oracleBox, lpBox).asJava)
      .sendChangeTo(Address.create("4MQyML64GnzMxZgm").getErgoAddress)
      .build()

    var signed: SignedTransaction = null
    try {
      signed = prover.sign(tx)
    } catch {
      case e: Exception => {
        println("User could not sing tx due to:")
        println(e)
        return
      }
    }
    println(s"signed tx: ${signed.toJson(false)}")
    println("---- ArbitrageMint scenario Done !! ----")
  }
}
