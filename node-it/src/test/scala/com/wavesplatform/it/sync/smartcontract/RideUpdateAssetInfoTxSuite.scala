package com.wavesplatform.it.sync.smartcontract

import com.typesafe.config.Config
import com.wavesplatform.api.http.ApiError.ScriptCompilerError
import com.wavesplatform.common.state.ByteStr
import com.wavesplatform.common.utils.EitherExt2
import com.wavesplatform.it.NodeConfigs
import com.wavesplatform.it.NodeConfigs.Default
import com.wavesplatform.it.api.SyncHttpApi._
import com.wavesplatform.it.sync._
import com.wavesplatform.it.transactions.BaseTransactionSuite
import com.wavesplatform.lang.v1.estimator.v2.ScriptEstimatorV2
import com.wavesplatform.transaction.Asset.IssuedAsset
import com.wavesplatform.transaction.smart.script.ScriptCompiler
import org.scalatest.CancelAfterFailure

class RideUpdateAssetInfoTxSuite extends BaseTransactionSuite with CancelAfterFailure {

  override protected def nodeConfigs: Seq[Config] =
    NodeConfigs.Builder(Default, 1, Seq.empty)
      .overrideBase(_.quorum(0))
      .overrideBase(_.minAssetInfoUpdateInterval(1))
      .buildNonConflicting()

  private val dApp = pkByAddress(firstAddress).stringRepr
  private val smartAcc = pkByAddress(secondAddress).stringRepr

  private var asset1: IssuedAsset = _
  private var asset2: IssuedAsset = _

  private val name: String = "MyAsset"
  private val description: String = "Some description"
  private val fee = issueFee + smartFee * 2

  def sourceDApp(version: Int): String =
    s"""
       |{-# STDLIB_VERSION $version #-}
       |{-# CONTENT_TYPE DAPP #-}
       |{-# SCRIPT_TYPE ACCOUNT #-}
       |
       |@Verifier(tx)
       |func verify() = {
       |  match tx {
       |    case uai: UpdateAssetInfoTransaction =>
       |      uai.id.size() == 32
       |      && uai.version == 1
       |      && uai.sender == this
       |      && uai.senderPublicKey == base58'${pkByAddress(firstAddress).publicKey.toString}'
       |      && uai.name == "$name"
       |      && uai.description == "$description"
       |      && uai.fee == $fee
       |      && uai.timestamp > 0
       |      && sigVerify(uai.bodyBytes, uai.proofs[0], uai.senderPublicKey)
       |    case _ => throw("UpdateAssetInfoTx was not matched")
       |  }
       |}
        """.stripMargin

  def sourceAcc(version: Int): String =
    s"""
       |{-# STDLIB_VERSION $version #-}
       |{-# CONTENT_TYPE EXPRESSION #-}
       |{-# SCRIPT_TYPE ACCOUNT #-}
       |
       |match tx {
       |  case uai: UpdateAssetInfoTransaction =>
       |    uai.id.size() == 32
       |    && uai.version == 1
       |    && uai.sender == this
       |    && uai.senderPublicKey == base58'${pkByAddress(secondAddress).publicKey.toString}'
       |    && uai.name == "$name"
       |    && uai.description == "$description"
       |    && uai.fee == $fee
       |    && uai.timestamp > 0
       |    && sigVerify(uai.bodyBytes, uai.proofs[0], uai.senderPublicKey)
       |  case _ => throw("UpdateAssetInfoTx was not matched")
       |}""".stripMargin

  def sourceAsset(version: Int): String =
    s"""
       |{-# STDLIB_VERSION $version #-}
       |{-# CONTENT_TYPE EXPRESSION #-}
       |{-# SCRIPT_TYPE ASSET #-}
       |
       |match tx {
       |  case uai: UpdateAssetInfoTransaction =>
       |    uai.id.size() == 32
       |    && uai.version == 1
       |    && uai.sender == this.issuer
       |    && uai.senderPublicKey == this.issuerPublicKey
       |    && uai.name == "$name"
       |    && uai.description == "$description"
       |    && uai.fee == $fee
       |    && uai.timestamp > 0
       |  case _ => throw("UpdateAssetInfoTx was not matched")
       |}""".stripMargin

  test("can't compile V3 with UpdateAssetInfoTransaction") {
    Seq(sourceDApp(3), sourceAcc(3), sourceAsset(3))
      .foreach { scriptV3 =>
        assertApiError(sender.scriptCompile(scriptV3)) { error =>
          error.statusCode shouldBe 400
          error.id shouldBe ScriptCompilerError.Id
          error.message should include("Undefined type: `UpdateAssetInfoTransaction`")
        }
      }
  }

  test("can issue assets and set contracts with check of UpdateAssetInfoTransaction") {
    val scriptDApp = ScriptCompiler.compile(sourceDApp(4), ScriptEstimatorV2).explicitGet()._1.bytes().base64
    val scriptAcc = ScriptCompiler.compile(sourceAcc(4), ScriptEstimatorV2).explicitGet()._1.bytes().base64
    val scriptAsset = ScriptCompiler.compile(sourceAsset(4), ScriptEstimatorV2).explicitGet()._1.bytes().base64

    asset1 = IssuedAsset(ByteStr.decodeBase58(sender.issue(dApp, script = Some(scriptAsset), waitForTx = true).id).get)
    asset2 = IssuedAsset(ByteStr.decodeBase58(sender.issue(smartAcc, script = Some(scriptAsset), waitForTx = true).id).get)

    sender.setScript(dApp, Some(scriptDApp), waitForTx = true)
    sender.setScript(smartAcc, Some(scriptAcc), waitForTx = true)
  }

  test("can check UpdateAssetInfo tx from contracts") {
    nodes.waitForHeight(sender.height + 2)

    sender.updateAssetInfo(dApp, asset1.id.toString, "test", "", fee, waitForTx = true)
    sender.updateAssetInfo(smartAcc, asset2.id.toString, "test", "", fee, waitForTx = true)
  }

}


