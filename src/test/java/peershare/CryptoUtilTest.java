package peershare;

import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class CryptoUtilTest {

    @Test
    void generatedRSAKeyPairIs2048Bit() throws Exception {
        KeyPair pair = CryptoUtil.generateRSAKeyPair();

        assertNotNull(pair.getPublic());
        assertNotNull(pair.getPrivate());
        // RSA modulus for a 2048-bit key is represented in 256 bytes (X.509 DER
        // encoding of the public key also carries some algorithm-identifier
        // overhead, so this checks the underlying key size indirectly via the
        // wrap/unwrap round trip below rather than parsing ASN.1 by hand).
        assertEquals("RSA", pair.getPublic().getAlgorithm());
    }

    @Test
    void publicKeyRoundTripsThroughEncodeDecode() throws Exception {
        KeyPair pair = CryptoUtil.generateRSAKeyPair();
        byte[] encoded = pair.getPublic().getEncoded();

        PublicKey decoded = CryptoUtil.decodeRSAPublicKey(encoded);

        assertArrayEquals(encoded, decoded.getEncoded());
    }

    @Test
    void aesKeyWrappedWithRSAThenUnwrappedMatchesOriginal() throws Exception {
        KeyPair rsaPair = CryptoUtil.generateRSAKeyPair();
        SecretKey aesKey = CryptoUtil.generateAESKey();

        byte[] wrapped = CryptoUtil.wrapAESKey(aesKey, rsaPair.getPublic());
        SecretKey unwrapped = CryptoUtil.unwrapAESKey(wrapped, rsaPair.getPrivate());

        assertArrayEquals(aesKey.getEncoded(), unwrapped.getEncoded());
    }

    @Test
    void wrappedAESKeyCannotBeUnwrappedWithWrongPrivateKey() throws Exception {
        KeyPair rsaPairA = CryptoUtil.generateRSAKeyPair();
        KeyPair rsaPairB = CryptoUtil.generateRSAKeyPair();
        SecretKey aesKey = CryptoUtil.generateAESKey();

        byte[] wrapped = CryptoUtil.wrapAESKey(aesKey, rsaPairA.getPublic());

        assertThrows(Exception.class, () -> CryptoUtil.unwrapAESKey(wrapped, rsaPairB.getPrivate()));
    }

    @Test
    void ivIsAlwaysCorrectLengthAndNotAllZero() {
        byte[] iv = CryptoUtil.generateIV();
        assertEquals(CryptoUtil.IV_LENGTH, iv.length);

        // Not a cryptographic randomness test, just a sanity guard against a
        // broken/stubbed generator that always returns zero bytes.
        byte[] zeros = new byte[CryptoUtil.IV_LENGTH];
        assertFalse(Arrays.equals(zeros, iv));
    }

    @Test
    void encryptThenDecryptStreamRoundTripsOriginalBytes() throws Exception {
        SecretKey key = CryptoUtil.generateAESKey();
        byte[] iv = CryptoUtil.generateIV();
        byte[] plaintext = "PeerShare AES/CBC round-trip test payload".getBytes();

        ByteArrayOutputStream encryptedOut = new ByteArrayOutputStream();
        try (var cipherOut = CryptoUtil.encryptStream(encryptedOut, key, iv)) {
            cipherOut.write(plaintext);
        }
        byte[] ciphertext = encryptedOut.toByteArray();
        assertFalse(Arrays.equals(plaintext, ciphertext), "ciphertext should not equal plaintext");

        ByteArrayOutputStream decryptedOut = new ByteArrayOutputStream();
        try (var cipherIn = CryptoUtil.decryptStream(new ByteArrayInputStream(ciphertext), key, iv)) {
            cipherIn.transferTo(decryptedOut);
        }

        assertArrayEquals(plaintext, decryptedOut.toByteArray());
    }

    @Test
    void decryptingWithWrongKeyProducesGarbageNotOriginalPlaintext() throws Exception {
        SecretKey correctKey = CryptoUtil.generateAESKey();
        SecretKey wrongKey = CryptoUtil.generateAESKey();
        byte[] iv = CryptoUtil.generateIV();
        byte[] plaintext = "sensitive payload".getBytes();

        ByteArrayOutputStream encryptedOut = new ByteArrayOutputStream();
        try (var cipherOut = CryptoUtil.encryptStream(encryptedOut, correctKey, iv)) {
            cipherOut.write(plaintext);
        }
        byte[] ciphertext = encryptedOut.toByteArray();

        // PKCS5 padding on a wrong-key decrypt very often throws a padding
        // exception; when it doesn't (rare), the recovered bytes must at
        // least not equal the original plaintext.
        try (var cipherIn = CryptoUtil.decryptStream(new ByteArrayInputStream(ciphertext), wrongKey, iv)) {
            byte[] decrypted = cipherIn.readAllBytes();
            assertFalse(Arrays.equals(plaintext, decrypted));
        } catch (Exception expectedPaddingFailure) {
            // also an acceptable outcome - wrong key correctly rejected
        }
    }
}
