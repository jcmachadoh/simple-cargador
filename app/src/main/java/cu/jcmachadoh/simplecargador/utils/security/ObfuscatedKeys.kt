package cu.jcmachadoh.simplecargador.utils.security

/**
 * RNF-05: ofuscación disuasoria de credenciales Enzona (sin backend).
 * Los literales están cifrados con XOR rotativo y guardados como HEX,
 * decodificados en memoria solo en tiempo de uso (100% JVM, testeable).
 *
 * NOTA: es una medida de disuasión, no criptografía inviolable (riesgo documentado en TASKS.md).
 * Para rotar claves: regenerar los HEX con la misma XOR_KEY.
 */
object ObfuscatedKeys {

    // Fragmentos de la clave XOR, divididos para dificultar su localización en el binario.
    private val kA = "Simple".toByteArray()
    private val kB = "Cargador".toByteArray()
    private val kC = "2026!".toByteArray()
    private val kD = "KeyRotate#1".toByteArray()

    private val xorKey: ByteArray by lazy { kA + kB + kC + kD }

    // Valores codificados (placeholders; reemplazar por credenciales reales de producción)
    private const val CONSUMER_KEY_ENC =
        "002A3233232B10343F22333B24376B6F627A600820311D23302426"
    private const val CONSUMER_SECRET_ENC =
        "002A3233232B10343F22333B3C37716277627E1B2938112A3C2E38216663"
    private const val MERCHANT_UUID_ENC =
        "002A323D293700293329353B3A277B746D666D0A263C1A2038253137"
    private const val OFFLINE_SECRET_ENC =
        "002A323F2A230F283C223E3726357C797C717E18203A002A203E242962721621223C282011"

    fun consumerKey(): String = decode(CONSUMER_KEY_ENC)

    fun consumerSecret(): String = decode(CONSUMER_SECRET_ENC)

    fun merchantUuid(): String = decode(MERCHANT_UUID_ENC)

    fun offlineSecret(): String = decode(OFFLINE_SECRET_ENC)

    internal fun decode(hex: String): String {
        val data = ByteArray(hex.length / 2)
        for (i in data.indices) {
            val hi = Character.digit(hex[i * 2], 16)
            val lo = Character.digit(hex[i * 2 + 1], 16)
            data[i] = ((hi shl 4) or lo).toByte()
        }
        val key = xorKey
        val out = ByteArray(data.size)
        for (i in data.indices) {
            out[i] = (data[i].toInt() xor key[i % key.size].toInt()).toByte()
        }
        return String(out, Charsets.UTF_8)
    }
}
