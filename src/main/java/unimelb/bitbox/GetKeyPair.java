package unimelb.bitbox;

import sun.security.util.DerInputStream;
import sun.security.util.DerValue;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;

public class GetKeyPair {
    //    http://magnus-k-karlsson.blogspot.com/2018/05/how-to-read-pem-pkcs1-or-pkcs8-encoded.html
    static RSAPrivateKey getPrifromFile(String path) throws Exception {
        String content = new String(Files.readAllBytes(Paths.get(path)));
        content = content.replaceAll("\r|\n", "").replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "");
        //System.out.println("'" + content + "'");
        byte[] bytes = Base64.getDecoder().decode(content);

        DerInputStream derReader = new DerInputStream(bytes);
        DerValue[] seq = derReader.getSequence(0);
        BigInteger modulus = seq[1].getBigInteger();
        BigInteger privateExp = seq[3].getBigInteger();


        RSAPrivateKeySpec keySpec =
                new RSAPrivateKeySpec(modulus, privateExp);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        //System.out.println((RSAPrivateKey)keyFactory.generatePrivate(keySpec));
        return (RSAPrivateKey) keyFactory.generatePrivate(keySpec);
    }

    //    https://blog.csdn.net/hzzhoushaoyu/article/details/8627952
    static RSAPublicKey getPubfromString(String content) throws NoSuchAlgorithmException, InvalidKeySpecException {
        content = content.split(" ")[1];
        //System.out.println("'" + content + "'");
        byte[] key = Base64.getDecoder().decode(content);

        byte[] sshrsa = new byte[]{0, 0, 0, 7, 's', 's', 'h', '-', 'r', 's',
                'a'};
        int start_index = sshrsa.length;
        /* Decode the public exponent */
        int len = decodeUInt32(key, start_index);
        start_index += 4;
        byte[] pe_b = new byte[len];
        for (int i = 0; i < len; i++) {
            pe_b[i] = key[start_index++];
        }
        BigInteger pe = new BigInteger(pe_b);
        /* Decode the modulus */
        len = decodeUInt32(key, start_index);
        start_index += 4;
        byte[] md_b = new byte[len];
        for (int i = 0; i < len; i++) {
            md_b[i] = key[start_index++];
        }
        BigInteger md = new BigInteger(md_b);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        KeySpec ks = new RSAPublicKeySpec(md, pe);
        //System.out.println((RSAPublicKey) keyFactory.generatePublic(ks));
        return (RSAPublicKey) keyFactory.generatePublic(ks);
    }


    public static byte[] encryptByPub(RSAPublicKey publicKey, byte[] plainTextData)
            throws Exception {
        if (publicKey == null) {
            throw new Exception("No Public Key");
        }
        Cipher cipher = null;
        try {

            cipher = Cipher.getInstance("RSA");
            // cipher= Cipher.getInstance("RSA", new BouncyCastleProvider());
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] output = cipher.doFinal(plainTextData);
            return output;
        } catch (NoSuchAlgorithmException e) {
            throw new Exception("No such algorithm");
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
            return null;
        } catch (InvalidKeyException e) {
            throw new Exception("Invalid public key");
        } catch (IllegalBlockSizeException e) {
            throw new Exception("Illegal blockSize");
        } catch (BadPaddingException e) {
            throw new Exception("Bad Padding");
        }
    }


    public static byte[] decryptByPri(RSAPrivateKey privateKey, byte[] cipherData) {
        if (privateKey == null) {
            System.out.println("No Private Key");
        }
        Cipher cipher = null;
        try {

            cipher = Cipher.getInstance("RSA");
            // cipher= Cipher.getInstance("RSA", new BouncyCastleProvider());
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] output = cipher.doFinal(cipherData);
            return output;
        } catch (NoSuchAlgorithmException e) {
            System.out.println("No such algorithm");
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
            return null;
        } catch (InvalidKeyException e) {
            System.out.println("Invalid public key");
        } catch (IllegalBlockSizeException e) {
            System.out.println("Illegal blockSize");
        } catch (BadPaddingException e) {
            System.out.println("Bad Padding");
        }
        return null;
    }

    private static int decodeUInt32(byte[] key, int start_index) {
        byte[] test = Arrays.copyOfRange(key, start_index, start_index + 4);
        return new BigInteger(test).intValue();
//		int int_24 = (key[start_index++] << 24) & 0xff;
//		int int_16 = (key[start_index++] << 16) & 0xff;
//		int int_8 = (key[start_index++] << 8) & 0xff;
//		int int_0 = key[start_index++] & 0xff;
//		return int_24 + int_16 + int_8 + int_0;
    }
}