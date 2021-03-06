package donank.amoveowallet.Repositories

import android.util.Log
import donank.amoveowallet.Data.AppPref
import donank.amoveowallet.Utility.serialize
import org.spongycastle.asn1.ASN1Integer
import org.spongycastle.asn1.DERSequenceGenerator
import org.spongycastle.asn1.sec.SECNamedCurves
import org.spongycastle.crypto.digests.SHA1Digest
import org.spongycastle.crypto.generators.ECKeyPairGenerator
import org.spongycastle.crypto.params.*
import org.spongycastle.crypto.signers.ECDSASigner
import org.spongycastle.jce.provider.BouncyCastleProvider
import java.math.BigInteger
import java.security.*
import org.spongycastle.util.encoders.Base64
import org.spongycastle.util.encoders.Hex
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import org.spongycastle.crypto.digests.SHA256Digest
import org.spongycastle.crypto.signers.HMacDSAKCalculator




class CryptoRepository {


    init {
        Security.insertProviderAt(BouncyCastleProvider() as Provider, 1)
    }

    private val EC_GEN_PARAM_SPEC = "secp256k1"
    private val HEX_RADIX = 16
    private val MIN_S_VALUE = BigInteger("1", HEX_RADIX)
    private val MAX_S_VALUE = BigInteger("7FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF5D576E7357A4501DDFE92F46681B20A0", HEX_RADIX)
    private val N = BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", HEX_RADIX)

    val curve = SECNamedCurves.getByName(EC_GEN_PARAM_SPEC)
    val domain = ECDomainParameters(curve.curve, curve.g, curve.n, curve.h)
    val generator = ECKeyPairGenerator()
    val keygenParams = ECKeyGenerationParameters(domain, SecureRandom())

    val hexArray = "0123456789ABCDEF".toCharArray()

    val signer = ECDSASigner(HMacDSAKCalculator(SHA256Digest()))

    fun toHex(data: ByteArray): String {
        return Hex.toHexString(data)
    }

    fun generatePubKey(privateKey: String): String {
        val privKey = BigInteger(privateKey, HEX_RADIX)
        val curvePt = curve.g.multiply(privKey)
        val x = curvePt.x.toBigInteger()
        val y = curvePt.y.toBigInteger()
        val xBytes = removeSignByte(x.toByteArray())
        val yBytes = removeSignByte(y.toByteArray())
        val pubKeyBytes = ByteArray(65)
        pubKeyBytes[0] = "04".toByte()
        System.arraycopy(xBytes, 0, pubKeyBytes, 1, xBytes.size)
        System.arraycopy(yBytes, 0, pubKeyBytes, 33, yBytes.size)
        return Base64.toBase64String(pubKeyBytes)
    }

    private fun removeSignByte(arr: ByteArray): ByteArray {
        if (arr.size == 33) {
            val newArr = ByteArray(32)
            System.arraycopy(arr, 1, newArr, 0, newArr.size)
            return newArr
        }
        return arr
    }


    //https://stackoverflow.com/questions/8571501/how-to-check-whether-the-string-is-base64-encoded-or-not
    fun validateAddress(data: String): Boolean {
        if (!data.isEmpty()) {
            try {
                Base64.decode(data)
            } catch (e: Exception) {
                return false
            }

        }
        return true
    }

    fun genKeyPair(): Pair<ECPrivateKeyParameters, ECPublicKeyParameters> {
        generator.init(keygenParams)
        val keyPair = generator.generateKeyPair()
        val privParams = keyPair.private as ECPrivateKeyParameters
        val pubParams = keyPair.public as ECPublicKeyParameters
        return Pair(privParams, pubParams)
    }


    fun generateTransaction(tx: List<Any>, privateKey: String): String {
        val sign = sign(tx, privateKey)

        sign.forEach {
            Log.d("$it","b")
        }

        sign.toTypedArray().forEach {
            Log.d("$it","+")
        }

        return ""
    }

    fun sign(data: List<Any>, pKey: String): ByteArray {
        Log.d("sign", "$data -- $pKey")
        Log.d("pkey - BigInteger", "${BigInteger(pKey, HEX_RADIX)}")
        val key = ECPrivateKeyParameters(BigInteger(pKey, HEX_RADIX), domain)
        val signatureBytes = byteArrayOf()
        try {
            Log.d("data", "$data")
            Log.d("Key", key.toString())

            val serializedData = serialize(data) as List<Byte>

            Log.d("serializedData", "$serializedData")


            Log.d("serializedData", "tobarr")
            val baos1 = ByteArrayOutputStream()
            serializedData.toByteArray().forEach{
                if(it < 0){
                    baos1.write(it + 256)
                }else baos1.write(it.toInt())
            }

            val hash = hash(baos1.toByteArray())
            Log.d("baos1-size","${baos1.size()}")
            baos1.reset()
            baos1.close()
            Log.d("baos1","close")
            Log.d("baos1-size","${baos1.size()}")

            hash.forEach {
                if(it < 0){
                    baos1.write(it + 256)
                    Log.d("$it", "${it + 256}")
                }else {
                    Log.d("$it", " + ")
                    baos1.write(it.toInt())
                }
            }

            val finaltxHash = baos1.toByteArray()

            Log.d("baos1-size","${baos1.size()}")
            baos1.reset()
            baos1.close()
            Log.d("baos1","close")
            Log.d("baos1-size","${baos1.size()}")

            signer.init(true, ECPrivateKeyParameters(key.d, domain))

            val signature = signer.generateSignature(finaltxHash)
            val r = signature[0]
            Log.d("r","$r")
            val s = signature[1]
            Log.d("s","$s")
            val lowS = getLowValue(s)
            Log.d("lowS","$lowS")
            val derSequenceGenerator = DERSequenceGenerator(baos1)
            val as1nr = ASN1Integer(r)
            Log.d("as1nr","$as1nr")
            val asn1LowS = ASN1Integer(s)
            Log.d("asn1LowS","$asn1LowS")
            val asn2LowS = ASN1Integer(lowS)
            Log.d("asn2LowS","$asn2LowS")
            derSequenceGenerator.addObject(as1nr)
            Log.d("addObject(as1nr)","${derSequenceGenerator.rawOutputStream}")
            derSequenceGenerator.addObject(asn2LowS)
            Log.d("addObject(asn1LowS)","${derSequenceGenerator.rawOutputStream}")
            derSequenceGenerator.close()

            Log.d("baos1","forEach")
            baos1.toByteArray().forEach {
                Log.d("$it","b")
            }
            baos1.write(signatureBytes)
            Log.d("signatureBytes","forEach")
            Log.d("signatureBytes","$signatureBytes")
            Log.d("signatureBytesbaos","${baos1.toByteArray()}")

            baos1.reset()
            baos1.close()
        } catch (e: Exception) {
            Log.d("EXCEPTION WHILE SIGNING", e.message)
        }
        return signatureBytes
    }

    private fun getLowValue(s: BigInteger): BigInteger {
        val lowerThanMin = s.compareTo(MIN_S_VALUE)
        val higherThanMax = s.compareTo(MAX_S_VALUE)
        if (lowerThanMin < 0) {
            throw IllegalArgumentException(String.format("S value must be equal or greater than: %s", MIN_S_VALUE))
        } else if (higherThanMax > 0) {
            return N.subtract(s)
        }
        return s
    }

    fun hash(data: ByteArray): ByteArray {
        Log.d("hash - input", "$data")

        var hash = byteArrayOf()
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            hash = digest.digest(data)
        } catch (e: Exception) {
            Log.d("Exception while hashing", e.message)
        }
        Log.d("hash - output", "$hash")
        return hash
    }

    fun verify(message: Any, signature: String, pubkey: String): Boolean {
        val sig = bin2rs(Base64.decode(signature))
        val d2 = serialize(message) as List<Int>
        val baos = ByteArrayOutputStream()
        val dout = DataOutputStream(baos)
        d2.forEach {
            dout.write(it)
        }
        Log.d("baos1", "$baos")
        Log.d("baos1String", String(baos.toByteArray()))
        val bArr = byteArrayOf()
        baos.write(bArr)
        val h = hash(bArr)
        Log.d("pubkey",pubkey)
        Log.d("pubkey-bytearr","${pubkey.toByteArray()}")
        Log.d("pubkey-bytearr-utf","${pubkey.toByteArray(Charsets.UTF_8)}")
        signer.init(false, ECPublicKeyParameters(curve.curve.decodePoint(pubkey.toByteArray(Charsets.UTF_8)), domain))
        Log.d("h","$h")
        Log.d("BigInteger(sig.first)","${BigInteger(sig.first)}")
        Log.d("BigInteger(sig.second)","${BigInteger(sig.second)}")
        val verif = signer.verifySignature(h, BigInteger(sig.first), BigInteger(sig.second))
        Log.d("verif","$verif")
        return verif
    }

    fun bin2rs(data: ByteArray): Pair<String, String> {
        val h = toHex(data)
        val a2 = data[3].toInt()
        val r = h.slice(8..8 + (a2 * 2))
        val s = h.slice(12 + (a2 * 2)..12 + (a2 * 2))
        return Pair(r, s)
    }

    fun encrypt(data: String, userKey: String = AppPref.passcode): String {
        val key = generateKey(userKey.toByteArray(Charsets.UTF_8))
        val cipher = Cipher.getInstance("AES")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
        val encryptedValue = Base64.encode(encrypted)
        return String(encryptedValue)
    }

    fun decrypt(data: String, userKey: String = AppPref.passcode): String {
        val key = generateKey(userKey.toByteArray(Charsets.UTF_8))
        val cipher = Cipher.getInstance("AES")
        cipher.init(Cipher.DECRYPT_MODE, key)
        val decodeBytes = Base64.decode(data.toByteArray(Charsets.UTF_8))
        val original = cipher.doFinal(decodeBytes)
        Log.d("decrypt|d - $data p - $userKey",String(original))
        return String(original)
    }

    fun generateKey(key: ByteArray): Key {
        val hashedKey = hash(key)
        val keyArr = Arrays.copyOf(hashedKey, 16)
        return SecretKeySpec(keyArr, "AES")
    }

}