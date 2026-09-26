/*
 * This file is part of 12pit.
 *
 * Copyright (C) 2026 The 12pit Authors and contributors <https://github.com/12src/12pit>
 *
 * 12pit is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * 12pit is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with 12pit. If not, see <https://www.gnu.org/licenses/>.
 */
package pit12.feature.sync;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Date;
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.bcpg.sig.KeyFlags;
import org.bouncycastle.openpgp.PGPKeyRingGenerator;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureGenerator;
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPKeyPair;

final class SyncIdentity {
    private final PGPPrivateKey privateKey;
    private final PGPPublicKey publicKey;
    private final String publicKeyText;

    private SyncIdentity(PGPPrivateKey privateKey, PGPPublicKey publicKey, String publicKeyText) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
        this.publicKeyText = publicKeyText;
    }

    static SyncIdentity loadOrCreate(SyncConfig config) throws GeneralSecurityException {
        try {
            if (!config.privateKey().isEmpty()) {
                PGPSecretKeyRingCollection rings = new PGPSecretKeyRingCollection(
                        PGPUtil.getDecoderStream(new ByteArrayInputStream(
                                Base64.getDecoder().decode(config.privateKey()))),
                        new JcaKeyFingerprintCalculator());
                PGPSecretKey secret = rings.getKeyRings().next().getSecretKey();
                if (config.publicKey().isEmpty()) {
                    config.publicKey(encode(secret.getPublicKey()));
                }
                return new SyncIdentity(secret.extractPrivateKey(null), secret.getPublicKey(),
                        config.publicKey().isEmpty() ? armorText(secret.getPublicKey())
                                : decode(config.publicKey()));
            }
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(3072);
            KeyPair pair = generator.generateKeyPair();
            Date createdAt = new Date();
            JcaPGPKeyPair keyPair = new JcaPGPKeyPair(PGPPublicKey.RSA_SIGN, pair, createdAt);
            PGPSignatureSubpacketGenerator subpackets = new PGPSignatureSubpacketGenerator();
            subpackets.setKeyFlags(false, KeyFlags.CERTIFY_OTHER | KeyFlags.SIGN_DATA);
            PGPKeyRingGenerator ringGenerator = new PGPKeyRingGenerator(
                    PGPSignature.POSITIVE_CERTIFICATION, keyPair, "12pit",
                    new JcaPGPDigestCalculatorProviderBuilder().build().get(HashAlgorithmTags.SHA1),
                    subpackets.generate(), null,
                    new JcaPGPContentSignerBuilder(PGPPublicKey.RSA_SIGN, HashAlgorithmTags.SHA256),
                    null);
            PGPSecretKeyRing ring = ringGenerator.generateSecretKeyRing();
            PGPSecretKey secret = ring.getSecretKey();
            config.privateKey(encode(ring));
            config.publicKey(encode(secret.getPublicKey()));
            return new SyncIdentity(secret.extractPrivateKey(null), secret.getPublicKey(),
                    decode(config.publicKey()));
        } catch (Exception failure) {
            throw new GeneralSecurityException("Unable to load PGP identity", failure);
        }
    }

    String fingerprint() {
        byte[] fingerprint = publicKey.getFingerprint();
        StringBuilder result = new StringBuilder(fingerprint.length * 2);
        for (byte value : fingerprint) {
            result.append(Character.forDigit((value >>> 4) & 0x0f, 16));
            result.append(Character.forDigit(value & 0x0f, 16));
        }
        return result.toString();
    }

    String publicKey() {
        return publicKeyText;
    }

    String sign(String message) throws GeneralSecurityException {
        try {
            PGPSignatureGenerator generator =
                    new PGPSignatureGenerator(new JcaPGPContentSignerBuilder(
                            publicKey.getAlgorithm(), HashAlgorithmTags.SHA256));
            generator.init(PGPSignature.BINARY_DOCUMENT, privateKey);
            generator.update(message.getBytes(StandardCharsets.UTF_8));
            return armorText(generator.generate());
        } catch (Exception failure) {
            throw new GeneralSecurityException("Unable to sign sync message", failure);
        }
    }

    private static String encode(Object value) throws Exception {
        return Base64.getEncoder().encodeToString(armor(value));
    }

    private static String armorText(Object value) throws Exception {
        return new String(armor(value), StandardCharsets.UTF_8);
    }

    private static byte[] armor(Object value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ArmoredOutputStream armor = new ArmoredOutputStream(bytes);
        if (value instanceof PGPSecretKeyRing) {
            ((PGPSecretKeyRing) value).encode(armor);
        } else if (value instanceof PGPPublicKey) {
            ((PGPPublicKey) value).encode(armor);
        } else {
            ((PGPSignature) value).encode(armor);
        }
        armor.close();
        return bytes.toByteArray();
    }

    private static String decode(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
