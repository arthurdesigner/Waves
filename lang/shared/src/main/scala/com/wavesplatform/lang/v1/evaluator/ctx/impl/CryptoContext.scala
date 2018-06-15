package com.wavesplatform.lang.v1.evaluator.ctx.impl

import com.wavesplatform.lang.v1.compiler.CompilerContext
import com.wavesplatform.lang.v1.compiler.Types.{BOOLEAN, BYTEVECTOR, STRING}
import com.wavesplatform.lang.v1.evaluator.FunctionIds._
import com.wavesplatform.lang.v1.evaluator.ctx.{BaseFunction, EvaluationContext, NativeFunction}
import com.wavesplatform.lang.v1.{BaseGlobal, CTX}
import scodec.bits.ByteVector

object CryptoContext {

  def build(global: BaseGlobal): CTX = {
    def hashFunction(name: String, internalName: Short, cost: Long)(h: Array[Byte] => Array[Byte]): BaseFunction =
      NativeFunction(name, cost, internalName, BYTEVECTOR, "bytes" -> BYTEVECTOR) {
        case (m: ByteVector) :: Nil => Right(ByteVector(h(m.toArray)))
        case _                      => ???
      }

    val keccak256F: BaseFunction  = hashFunction("keccak256", KECCAK256, 10)(global.keccak256)
    val blake2b256F: BaseFunction = hashFunction("blake2b256", BLAKE256, 10)(global.blake2b256)
    val sha256F: BaseFunction     = hashFunction("sha256", SHA256, 10)(global.sha256)

    val sigVerifyF: BaseFunction =
      NativeFunction("sigVerify", 100, SIGVERIFY, BOOLEAN, "message" -> BYTEVECTOR, "sig" -> BYTEVECTOR, "pub" -> BYTEVECTOR) {
        case (m: ByteVector) :: (s: ByteVector) :: (p: ByteVector) :: Nil =>
          Right(global.curve25519verify(m.toArray, s.toArray, p.toArray))
        case _ => ???
      }

    def toBase58StringF: BaseFunction = NativeFunction("toBase58String", 10, TOBASE58, STRING, "bytes" -> BYTEVECTOR) {
      case (bytes: ByteVector) :: Nil => global.base58Encode(bytes.toArray)
      case _                          => ???
    }

    def toBase64StringF: BaseFunction = NativeFunction("toBase64String", 10, TOBASE64, STRING, "bytes" -> BYTEVECTOR) {
      case (bytes: ByteVector) :: Nil => global.base64Encode(bytes.toArray)
      case _                          => ???
    }

    CTX(Seq.empty, Map.empty, Seq(keccak256F, blake2b256F, sha256F, sigVerifyF, toBase58StringF, toBase64StringF))
  }

  def evalContext(global: BaseGlobal): EvaluationContext   = build(global).evaluationContext
  def compilerContext(global: BaseGlobal): CompilerContext = build(global).compilerContext
}
