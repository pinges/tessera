package com.quorum.tessera.q2t;

import com.quorum.tessera.enclave.EncodedPayload;
import com.quorum.tessera.enclave.PayloadEncoder;
import com.quorum.tessera.encryption.KeyNotFoundException;
import com.quorum.tessera.encryption.PublicKey;
import com.quorum.tessera.transaction.publish.AsyncPublishPayloadException;
import com.quorum.tessera.transaction.publish.PayloadPublisher;
import com.quorum.tessera.transaction.publish.PublishPayloadException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.*;

public class RestAsyncPayloadPublisherTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(RestAsyncPayloadPublisherTest.class);

    private RestAsyncPayloadPublisher asyncPublisher;

    private PayloadPublisher publisher;

    private PayloadEncoder encoder;

    @Before
    public void onSetup() {
        Executor executor = Executors.newCachedThreadPool();
        CompletionService<Void> completionService = new ExecutorCompletionService<>(executor);
        this.publisher = mock(PayloadPublisher.class);
        this.encoder = mock(PayloadEncoder.class);
        this.asyncPublisher = new RestAsyncPayloadPublisher(completionService, publisher, encoder);
    }

    @After
    public void onTeardown() {
        verifyNoMoreInteractions(publisher, encoder);
    }

    @Test
    public void publishPayload() {
        EncodedPayload encodedPayload = mock(EncodedPayload.class);

        PublicKey recipient = PublicKey.from("RECIPIENT".getBytes());
        PublicKey otherRecipient = PublicKey.from("OTHERRECIPIENT".getBytes());

        List<PublicKey> recipients = List.of(recipient, otherRecipient);

        when(encoder.forRecipient(any(EncodedPayload.class), any(PublicKey.class)))
                .thenReturn(mock(EncodedPayload.class));

        asyncPublisher.publishPayload(encodedPayload, recipients);

        verify(encoder).forRecipient(any(EncodedPayload.class), eq(recipient));
        verify(encoder).forRecipient(any(EncodedPayload.class), eq(otherRecipient));
        verify(publisher).publishPayload(any(EncodedPayload.class), eq(recipient));
        verify(publisher).publishPayload(any(EncodedPayload.class), eq(otherRecipient));
    }

    @Test
    public void publishPayloadStopsIfOneFails() {
        EncodedPayload encodedPayload = mock(EncodedPayload.class);

        PublicKey recipient = PublicKey.from("RECIPIENT".getBytes());
        PublicKey otherRecipient = PublicKey.from("OTHERRECIPIENT".getBytes());
        PublicKey anotherRecipient = PublicKey.from("ANOTHERRECIPIENT".getBytes());

        List<PublicKey> recipients = List.of(recipient, otherRecipient, anotherRecipient);

        when(encoder.forRecipient(any(EncodedPayload.class), any(PublicKey.class)))
                .thenReturn(mock(EncodedPayload.class));

        final AtomicInteger atomicInt = new AtomicInteger();

        doAnswer(
                        invocation -> {
                            longRunningTaskThenIncrement(atomicInt);
                            return null;
                        })
                .doAnswer(
                        invocation -> {
                            longRunningTaskThenIncrement(atomicInt);
                            return null;
                        })
                .doThrow(new PublishPayloadException("some publisher exception"))
                .when(publisher)
                .publishPayload(any(EncodedPayload.class), any(PublicKey.class));

        Throwable ex = catchThrowable(() -> asyncPublisher.publishPayload(encodedPayload, recipients));

        assertThat(ex).isExactlyInstanceOf(AsyncPublishPayloadException.class);
        assertThat(ex.getCause()).isExactlyInstanceOf(PublishPayloadException.class);
        assertThat(ex.getCause()).hasMessage("some publisher exception");

        assertThat(atomicInt.get())
                .withFailMessage("publish should have failed-fast and not waited for completion of all tasks")
                .isEqualTo(0);

        verify(encoder, times(3)).forRecipient(any(EncodedPayload.class), any(PublicKey.class));
        verify(publisher, times(3)).publishPayload(any(EncodedPayload.class), any(PublicKey.class));
    }

    @Test
    public void publishPayloadInterrupted() throws InterruptedException {
        CompletionService<Void> completionService = mock(CompletionService.class);
        asyncPublisher = new RestAsyncPayloadPublisher(completionService, publisher, encoder);

        EncodedPayload encodedPayload = mock(EncodedPayload.class);

        PublicKey recipient = PublicKey.from("RECIPIENT".getBytes());

        List<PublicKey> recipients = List.of(recipient);

        when(completionService.submit(any(Callable.class))).thenReturn(mock(Future.class));

        when(encoder.forRecipient(any(EncodedPayload.class), any(PublicKey.class)))
                .thenReturn(mock(EncodedPayload.class));

        when(completionService.take()).thenThrow(new InterruptedException("some publish interrupted error"));

        Throwable ex = catchThrowable(() -> asyncPublisher.publishPayload(encodedPayload, recipients));

        assertThat(ex).isExactlyInstanceOf(AsyncPublishPayloadException.class);
        assertThat(ex.getCause()).isExactlyInstanceOf(InterruptedException.class);
        assertThat(ex.getCause()).hasMessage("some publish interrupted error");

        verify(completionService).submit(any(Callable.class));
        verify(completionService).take();
    }

    @Test
    public void publishPayloadKeyNotFound() {
        EncodedPayload encodedPayload = mock(EncodedPayload.class);

        PublicKey recipient = PublicKey.from("RECIPIENT".getBytes());

        List<PublicKey> recipients = List.of(recipient);

        when(encoder.forRecipient(any(EncodedPayload.class), any(PublicKey.class)))
                .thenReturn(mock(EncodedPayload.class));

        final AtomicInteger atomicInt = new AtomicInteger();

        doThrow(new KeyNotFoundException("some error"))
                .when(publisher)
                .publishPayload(any(EncodedPayload.class), any(PublicKey.class));

        Throwable ex = catchThrowable(() -> asyncPublisher.publishPayload(encodedPayload, recipients));

        assertThat(ex).isExactlyInstanceOf(KeyNotFoundException.class);
        assertThat(ex).hasMessage("some error");

        assertThat(atomicInt.get())
                .withFailMessage("publish should have failed-fast and not waited for completion of all tasks")
                .isEqualTo(0);

        verify(encoder).forRecipient(any(EncodedPayload.class), any(PublicKey.class));
        verify(publisher).publishPayload(any(EncodedPayload.class), any(PublicKey.class));
    }

    void longRunningTaskThenIncrement(AtomicInteger atomicInteger) {
        int id = new Random().nextInt();

        LOGGER.debug("long running task " + id + " started");
        long startTime = System.nanoTime();
        new BigInteger(5000, 9, new Random());
        long stopTime = System.nanoTime();
        LOGGER.debug("long running task " + id + " completed: " + ((double) (stopTime - startTime) / 1000000000));

        atomicInteger.incrementAndGet();
    }
}
