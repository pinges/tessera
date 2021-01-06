package com.quorum.tessera.enclave.encoder;

import com.quorum.tessera.enclave.*;
import com.quorum.tessera.encryption.Nonce;
import com.quorum.tessera.encryption.PublicKey;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Collections.*;
import static org.assertj.core.api.Assertions.assertThat;

public class EncoderCompatibilityTest {

    private final LegacyPayloadEncoder legacyEncoder = new LegacyPayloadEncoder();

    private final V2PayloadEncoder v2Encoder = new V2PayloadEncoder();

    private final PayloadEncoder v3Encoder = new PayloadEncoderImpl();

    @Test
    public void legacyToV2() {

        final LegacyEncodedPayload legacyPayload =
                new LegacyEncodedPayload(
                        PublicKey.from("SENDER".getBytes()),
                        "cipherText".getBytes(),
                        new Nonce("cipherTextNonce".getBytes()),
                        List.of("box".getBytes()),
                        new Nonce("recipientNonce".getBytes()),
                        List.of(PublicKey.from("RECIPIENT".getBytes())));

        final byte[] encoded = legacyEncoder.encode(legacyPayload);

        final V2EncodedPayload v2Payload = v2Encoder.decode(encoded);

        final List<RecipientBox> boxes =
                legacyPayload.getRecipientBoxes().stream().map(RecipientBox::from).collect(Collectors.toList());

        assertThat(v2Payload.getSenderKey()).isEqualTo(legacyPayload.getSenderKey());
        assertThat(v2Payload.getCipherText()).isEqualTo(legacyPayload.getCipherText());
        assertThat(v2Payload.getCipherTextNonce()).isEqualTo(legacyPayload.getCipherTextNonce());
        assertThat(v2Payload.getRecipientBoxes()).isEqualTo(boxes);
        assertThat(v2Payload.getRecipientNonce()).isEqualTo(legacyPayload.getRecipientNonce());
        assertThat(v2Payload.getRecipientKeys()).isEqualTo(legacyPayload.getRecipientKeys());

        // Default enhanced privacy values
        assertThat(v2Payload.getPrivacyMode()).isEqualTo(PrivacyMode.STANDARD_PRIVATE);
        assertThat(v2Payload.getAffectedContractTransactions()).isEmpty();
        assertThat(v2Payload.getExecHash()).isNullOrEmpty();
    }

    @Test
    public void v2ToLegacy() {

        final V2EncodedPayload v2Payload =
                V2EncodedPayload.Builder.create()
                        .withSenderKey(PublicKey.from("SENDER".getBytes()))
                        .withCipherText("CIPHER_TEXT".getBytes())
                        .withCipherTextNonce(new Nonce("NONCE".getBytes()))
                        .withRecipientBoxes(singletonList("recipientBox".getBytes()))
                        .withRecipientNonce(new Nonce("recipientNonce".getBytes()))
                        .withRecipientKeys(List.of(PublicKey.from("KEY1".getBytes())))
                        .withPrivacyMode(PrivacyMode.STANDARD_PRIVATE)
                        .withAffectedContractTransactions(emptyMap())
                        .build();

        final byte[] encoded = v2Encoder.encode(v2Payload);

        final LegacyEncodedPayload legacyPayload = legacyEncoder.decode(encoded);

        final List<RecipientBox> boxes =
                legacyPayload.getRecipientBoxes().stream().map(RecipientBox::from).collect(Collectors.toList());

        assertThat(legacyPayload.getSenderKey()).isEqualTo(v2Payload.getSenderKey());
        assertThat(legacyPayload.getCipherText()).isEqualTo(v2Payload.getCipherText());
        assertThat(legacyPayload.getCipherTextNonce()).isEqualTo(v2Payload.getCipherTextNonce());
        assertThat(boxes).isEqualTo(v2Payload.getRecipientBoxes());
        assertThat(legacyPayload.getRecipientNonce()).isEqualTo(v2Payload.getRecipientNonce());
        assertThat(legacyPayload.getRecipientKeys()).isEqualTo(v2Payload.getRecipientKeys());
    }

    @Test
    public void v2toV3NonPsv() {

        final V2EncodedPayload v2Payload =
            V2EncodedPayload.Builder.create()
                .withSenderKey(PublicKey.from("SENDER".getBytes()))
                .withCipherText("CIPHER_TEXT".getBytes())
                .withCipherTextNonce(new Nonce("NONCE".getBytes()))
                .withRecipientBoxes(singletonList("recipientBox".getBytes()))
                .withRecipientNonce(new Nonce("recipientNonce".getBytes()))
                .withRecipientKeys(List.of(PublicKey.from("KEY1".getBytes())))
                .withPrivacyMode(PrivacyMode.PARTY_PROTECTION)
                .withAffectedContractTransactions(Map.of(TxHash.from("hash".getBytes()), "hash".getBytes()))
                .build();

        final byte[] encoded = v2Encoder.encode(v2Payload);

        final EncodedPayload encodedPayload = v3Encoder.decode(encoded);

        assertThat(encodedPayload.getSenderKey()).isEqualTo(v2Payload.getSenderKey());
        assertThat(encodedPayload.getCipherText()).isEqualTo(v2Payload.getCipherText());
        assertThat(encodedPayload.getCipherTextNonce()).isEqualTo(v2Payload.getCipherTextNonce());
        assertThat(encodedPayload.getRecipientBoxes()).isEqualTo(v2Payload.getRecipientBoxes());
        assertThat(encodedPayload.getRecipientNonce()).isEqualTo(v2Payload.getRecipientNonce());
        assertThat(encodedPayload.getRecipientKeys()).isEqualTo(v2Payload.getRecipientKeys());

        //Enhanced privacy values
        assertThat(encodedPayload.getPrivacyMode()).isEqualTo(v2Payload.getPrivacyMode());
        assertThat(encodedPayload.getAffectedContractTransactions()).isEqualTo(v2Payload.getAffectedContractTransactions());
        assertThat(encodedPayload.getExecHash()).isNullOrEmpty();

        assertThat(encodedPayload.getPrivacyGroupId()).isEmpty();
    }

    @Test
    public void v2toV3Psv() {

        final V2EncodedPayload v2Payload =
            V2EncodedPayload.Builder.create()
                .withSenderKey(PublicKey.from("SENDER".getBytes()))
                .withCipherText("CIPHER_TEXT".getBytes())
                .withCipherTextNonce(new Nonce("NONCE".getBytes()))
                .withRecipientBoxes(singletonList("recipientBox".getBytes()))
                .withRecipientNonce(new Nonce("recipientNonce".getBytes()))
                .withRecipientKeys(List.of(PublicKey.from("KEY1".getBytes())))
                .withPrivacyMode(PrivacyMode.PRIVATE_STATE_VALIDATION)
                .withAffectedContractTransactions(Map.of(TxHash.from("hash".getBytes()), "hash".getBytes()))
                .withExecHash("execHash".getBytes())
                .build();

        final byte[] encoded = v2Encoder.encode(v2Payload);

        final EncodedPayload encodedPayload = v3Encoder.decode(encoded);

        assertThat(encodedPayload.getSenderKey()).isEqualTo(v2Payload.getSenderKey());
        assertThat(encodedPayload.getCipherText()).isEqualTo(v2Payload.getCipherText());
        assertThat(encodedPayload.getCipherTextNonce()).isEqualTo(v2Payload.getCipherTextNonce());
        assertThat(encodedPayload.getRecipientBoxes()).isEqualTo(v2Payload.getRecipientBoxes());
        assertThat(encodedPayload.getRecipientNonce()).isEqualTo(v2Payload.getRecipientNonce());
        assertThat(encodedPayload.getRecipientKeys()).isEqualTo(v2Payload.getRecipientKeys());

        //Enhanced privacy values
        assertThat(encodedPayload.getPrivacyMode()).isEqualTo(v2Payload.getPrivacyMode());
        assertThat(encodedPayload.getAffectedContractTransactions()).isEqualTo(v2Payload.getAffectedContractTransactions());
        assertThat(encodedPayload.getExecHash()).isEqualTo(v2Payload.getExecHash());

        assertThat(encodedPayload.getPrivacyGroupId()).isEmpty();
    }

    @Test
    public void v3ToV2NonPsv() {

        // Payload to a v2 node should not have privacyGroupId
        final EncodedPayload payload =
            EncodedPayload.Builder.create()
                .withSenderKey(PublicKey.from("SENDER".getBytes()))
                .withCipherText("CIPHER_TEXT".getBytes())
                .withCipherTextNonce(new Nonce("NONCE".getBytes()))
                .withRecipientBoxes(singletonList("recipientBox".getBytes()))
                .withRecipientNonce(new Nonce("recipientNonce".getBytes()))
                .withRecipientKeys(List.of(PublicKey.from("KEY1".getBytes())))
                .withPrivacyMode(PrivacyMode.PARTY_PROTECTION)
                .withAffectedContractTransactions(Map.of(TxHash.from("hash".getBytes()), "hash".getBytes()))
//                .withPrivacyGroupId(PublicKey.from("GROUP_ID".getBytes()))
                .build();

        final byte[] encoded = v3Encoder.encode(payload);

        final V2EncodedPayload v2Payload = v2Encoder.decode(encoded);

        assertThat(v2Payload.getSenderKey()).isEqualTo(payload.getSenderKey());
        assertThat(v2Payload.getCipherText()).isEqualTo(payload.getCipherText());
        assertThat(v2Payload.getCipherTextNonce()).isEqualTo(payload.getCipherTextNonce());
        assertThat(v2Payload.getRecipientBoxes()).isEqualTo(payload.getRecipientBoxes());
        assertThat(v2Payload.getRecipientNonce()).isEqualTo(payload.getRecipientNonce());
        assertThat(v2Payload.getRecipientKeys()).isEqualTo(payload.getRecipientKeys());

        //Enhanced privacy values
        assertThat(v2Payload.getPrivacyMode()).isEqualTo(payload.getPrivacyMode());
        assertThat(v2Payload.getAffectedContractTransactions()).isEqualTo(payload.getAffectedContractTransactions());
        assertThat(v2Payload.getExecHash()).isNullOrEmpty();

    }

    @Test
    public void v3ToV2Psv() {

        // Payload to a v2 node should not have privacyGroupId
        final EncodedPayload payload =
            EncodedPayload.Builder.create()
                .withSenderKey(PublicKey.from("SENDER".getBytes()))
                .withCipherText("CIPHER_TEXT".getBytes())
                .withCipherTextNonce(new Nonce("NONCE".getBytes()))
                .withRecipientBoxes(singletonList("recipientBox".getBytes()))
                .withRecipientNonce(new Nonce("recipientNonce".getBytes()))
                .withRecipientKeys(List.of(PublicKey.from("KEY1".getBytes())))
                .withPrivacyMode(PrivacyMode.PRIVATE_STATE_VALIDATION)
                .withAffectedContractTransactions(Map.of(TxHash.from("hash".getBytes()), "hash".getBytes()))
                .withExecHash("EXEC_HASH".getBytes())
//                .withPrivacyGroupId(PublicKey.from("GROUP_ID".getBytes()))
                .build();

        final byte[] encoded = v3Encoder.encode(payload);

        final V2EncodedPayload v2Payload = v2Encoder.decode(encoded);

        assertThat(v2Payload.getSenderKey()).isEqualTo(payload.getSenderKey());
        assertThat(v2Payload.getCipherText()).isEqualTo(payload.getCipherText());
        assertThat(v2Payload.getCipherTextNonce()).isEqualTo(payload.getCipherTextNonce());
        assertThat(v2Payload.getRecipientBoxes()).isEqualTo(payload.getRecipientBoxes());
        assertThat(v2Payload.getRecipientNonce()).isEqualTo(payload.getRecipientNonce());
        assertThat(v2Payload.getRecipientKeys()).isEqualTo(payload.getRecipientKeys());

        //Enhanced privacy values
        assertThat(v2Payload.getPrivacyMode()).isEqualTo(payload.getPrivacyMode());
        assertThat(v2Payload.getAffectedContractTransactions()).isEqualTo(payload.getAffectedContractTransactions());
        assertThat(v2Payload.getExecHash()).isEqualTo(payload.getExecHash());
    }

    @Test
    public void legacyToV3() {

        final LegacyEncodedPayload legacyPayload =
            new LegacyEncodedPayload(
                PublicKey.from("SENDER".getBytes()),
                "cipherText".getBytes(),
                new Nonce("cipherTextNonce".getBytes()),
                List.of("box".getBytes()),
                new Nonce("recipientNonce".getBytes()),
                List.of(PublicKey.from("RECIPIENT".getBytes())));

        final byte[] encoded = legacyEncoder.encode(legacyPayload);

        final EncodedPayload payload = v3Encoder.decode(encoded);

        final List<RecipientBox> boxes =
            legacyPayload.getRecipientBoxes().stream().map(RecipientBox::from).collect(Collectors.toList());

        assertThat(payload.getSenderKey()).isEqualTo(legacyPayload.getSenderKey());
        assertThat(payload.getCipherText()).isEqualTo(legacyPayload.getCipherText());
        assertThat(payload.getCipherTextNonce()).isEqualTo(legacyPayload.getCipherTextNonce());
        assertThat(payload.getRecipientBoxes()).isEqualTo(boxes);
        assertThat(payload.getRecipientNonce()).isEqualTo(legacyPayload.getRecipientNonce());
        assertThat(payload.getRecipientKeys()).isEqualTo(legacyPayload.getRecipientKeys());

        // Default enhanced privacy values
        assertThat(payload.getPrivacyMode()).isEqualTo(PrivacyMode.STANDARD_PRIVATE);
        assertThat(payload.getAffectedContractTransactions()).isEmpty();
        assertThat(payload.getExecHash()).isNullOrEmpty();

        assertThat(payload.getPrivacyGroupId()).isEmpty();

    }

    @Test
    public void v3ToLegacy() {

        final EncodedPayload payload =
            EncodedPayload.Builder.create()
                .withSenderKey(PublicKey.from("SENDER".getBytes()))
                .withCipherText("CIPHER_TEXT".getBytes())
                .withCipherTextNonce(new Nonce("NONCE".getBytes()))
                .withRecipientBoxes(singletonList("recipientBox".getBytes()))
                .withRecipientNonce(new Nonce("recipientNonce".getBytes()))
                .withRecipientKeys(List.of(PublicKey.from("KEY1".getBytes())))
                .withPrivacyMode(PrivacyMode.STANDARD_PRIVATE)
                .withAffectedContractTransactions(emptyMap())
                .build();

        final byte[] encoded = v3Encoder.encode(payload);

        final LegacyEncodedPayload legacy = legacyEncoder.decode(encoded);

        final List<RecipientBox> boxes =
            legacy.getRecipientBoxes().stream().map(RecipientBox::from).collect(Collectors.toList());

        assertThat(legacy.getSenderKey()).isEqualTo(payload.getSenderKey());
        assertThat(legacy.getCipherText()).isEqualTo(payload.getCipherText());
        assertThat(legacy.getCipherTextNonce()).isEqualTo(payload.getCipherTextNonce());
        assertThat(boxes).isEqualTo(payload.getRecipientBoxes());
        assertThat(legacy.getRecipientNonce()).isEqualTo(payload.getRecipientNonce());
        assertThat(legacy.getRecipientKeys()).isEqualTo(payload.getRecipientKeys());
    }

    @Test
    public void encodeDecodeV3StandardPrivate() {

        final EncodedPayload payload =
            EncodedPayload.Builder.create()
                .withSenderKey(PublicKey.from("SENDER".getBytes()))
                .withCipherText("CIPHER_TEXT".getBytes())
                .withCipherTextNonce(new Nonce("NONCE".getBytes()))
                .withRecipientBoxes(singletonList("recipientBox".getBytes()))
                .withRecipientNonce(new Nonce("recipientNonce".getBytes()))
                .withRecipientKeys(List.of(PublicKey.from("KEY1".getBytes())))
                .withPrivacyMode(PrivacyMode.STANDARD_PRIVATE)
                .withAffectedContractTransactions(emptyMap())
                .withPrivacyGroupId(PublicKey.from("GROUP_ID".getBytes()))
                .build();

        final byte[] encoded = v3Encoder.encode(payload);

        final EncodedPayload result = v3Encoder.decode(encoded);

        assertThat(result.getSenderKey()).isEqualTo(payload.getSenderKey());
        assertThat(result.getCipherText()).isEqualTo(payload.getCipherText());
        assertThat(result.getCipherTextNonce()).isEqualTo(payload.getCipherTextNonce());
        assertThat(result.getRecipientBoxes()).isEqualTo(payload.getRecipientBoxes());
        assertThat(result.getRecipientNonce()).isEqualTo(payload.getRecipientNonce());
        assertThat(result.getRecipientKeys()).isEqualTo(payload.getRecipientKeys());

        assertThat(result.getPrivacyMode()).isEqualTo(PrivacyMode.STANDARD_PRIVATE);
        assertThat(result.getAffectedContractTransactions()).isEmpty();
        assertThat(result.getExecHash()).isNullOrEmpty();

        assertThat(result.getPrivacyGroupId()).isEqualTo(payload.getPrivacyGroupId());
    }

    @Test
    public void encodeDecodeV3PartyProtection() {

        final EncodedPayload payload =
            EncodedPayload.Builder.create()
                .withSenderKey(PublicKey.from("SENDER".getBytes()))
                .withCipherText("CIPHER_TEXT".getBytes())
                .withCipherTextNonce(new Nonce("NONCE".getBytes()))
                .withRecipientBoxes(singletonList("recipientBox".getBytes()))
                .withRecipientNonce(new Nonce("recipientNonce".getBytes()))
                .withRecipientKeys(List.of(PublicKey.from("KEY1".getBytes())))
                .withPrivacyMode(PrivacyMode.PARTY_PROTECTION)
                .withAffectedContractTransactions(
                    Map.of(TxHash.from("hash1".getBytes()), "1".getBytes(), TxHash.from("hash2".getBytes()), "2".getBytes()))
                .withPrivacyGroupId(PublicKey.from("GROUP_ID".getBytes()))
                .build();

        final byte[] encoded = v3Encoder.encode(payload);

        final EncodedPayload result = v3Encoder.decode(encoded);

        assertThat(result.getSenderKey()).isEqualTo(payload.getSenderKey());
        assertThat(result.getCipherText()).isEqualTo(payload.getCipherText());
        assertThat(result.getCipherTextNonce()).isEqualTo(payload.getCipherTextNonce());
        assertThat(result.getRecipientBoxes()).isEqualTo(payload.getRecipientBoxes());
        assertThat(result.getRecipientNonce()).isEqualTo(payload.getRecipientNonce());
        assertThat(result.getRecipientKeys()).isEqualTo(payload.getRecipientKeys());

        assertThat(result.getPrivacyMode()).isEqualTo(PrivacyMode.PARTY_PROTECTION);
        assertThat(result.getAffectedContractTransactions()).isEqualTo(payload.getAffectedContractTransactions());
        assertThat(result.getExecHash()).isNullOrEmpty();

        assertThat(result.getPrivacyGroupId()).isEqualTo(payload.getPrivacyGroupId());
    }

    @Test
    public void encodeDecodeV3PrivateStateValidation() {

        final EncodedPayload payload =
            EncodedPayload.Builder.create()
                .withSenderKey(PublicKey.from("SENDER".getBytes()))
                .withCipherText("CIPHER_TEXT".getBytes())
                .withCipherTextNonce(new Nonce("NONCE".getBytes()))
                .withRecipientBoxes(singletonList("recipientBox".getBytes()))
                .withRecipientNonce(new Nonce("recipientNonce".getBytes()))
                .withRecipientKeys(List.of(PublicKey.from("KEY1".getBytes())))
                .withPrivacyMode(PrivacyMode.PRIVATE_STATE_VALIDATION)
                .withAffectedContractTransactions(
                    Map.of(TxHash.from("hash1".getBytes()), "1".getBytes(), TxHash.from("hash2".getBytes()), "2".getBytes()))
                .withExecHash("EXEC_HASH".getBytes())
                .withPrivacyGroupId(PublicKey.from("GROUP_ID".getBytes()))
                .build();

        final byte[] encoded = v3Encoder.encode(payload);

        final EncodedPayload result = v3Encoder.decode(encoded);

        assertThat(result.getSenderKey()).isEqualTo(payload.getSenderKey());
        assertThat(result.getCipherText()).isEqualTo(payload.getCipherText());
        assertThat(result.getCipherTextNonce()).isEqualTo(payload.getCipherTextNonce());
        assertThat(result.getRecipientBoxes()).isEqualTo(payload.getRecipientBoxes());
        assertThat(result.getRecipientNonce()).isEqualTo(payload.getRecipientNonce());
        assertThat(result.getRecipientKeys()).isEqualTo(payload.getRecipientKeys());

        assertThat(result.getPrivacyMode()).isEqualTo(PrivacyMode.PRIVATE_STATE_VALIDATION);
        assertThat(result.getAffectedContractTransactions()).isEqualTo(payload.getAffectedContractTransactions());
        assertThat(result.getExecHash()).isEqualTo(payload.getExecHash());

        assertThat(result.getPrivacyGroupId()).isEqualTo(payload.getPrivacyGroupId());
    }
}
