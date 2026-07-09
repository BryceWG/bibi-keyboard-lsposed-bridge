package com.brycewg.asrkb.imebridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

public class BridgeCaptureCoordinatorTest {
    private final Executor directExecutor = new Executor() {
        @Override
        public void execute(Runnable command) {
            command.run();
        }
    };

    @Test
    public void startWritesFramesAndReleaseFinishes() {
        FakeEnvironment environment = new FakeEnvironment();
        FakeSessionClient client = new FakeSessionClient();
        FakeRecorder recorder = new FakeRecorder(true);
        Statuses statuses = new Statuses();
        BridgeCaptureCoordinator coordinator = newCoordinator(environment, client, recorder, statuses);

        coordinator.startCapture();
        recorder.emit(new byte[] {1, 0, 2, 0}, 42);
        coordinator.finishCapture();

        assertEquals(1, client.beginCalls);
        assertEquals(1, client.writeCalls);
        assertEquals(1, client.finishCalls);
        assertEquals(0, client.cancelCalls);
        assertEquals(1, recorder.stopCalls);
        assertFalse(recorder.recording);
        assertEquals(BridgeCaptureStatus.State.READY, statuses.last().state);
    }

    @Test
    public void cancelStopsRecorderCancelsSessionAndRejectsLateFrames() {
        FakeEnvironment environment = new FakeEnvironment();
        FakeSessionClient client = new FakeSessionClient();
        FakeRecorder recorder = new FakeRecorder(true);
        Statuses statuses = new Statuses();
        BridgeCaptureCoordinator coordinator = newCoordinator(environment, client, recorder, statuses);

        coordinator.startCapture();
        coordinator.cancelActiveCapture("window hidden");
        recorder.emit(new byte[] {1, 0}, 12);

        assertEquals(1, client.beginCalls);
        assertEquals(0, client.writeCalls);
        assertEquals(0, client.finishCalls);
        assertEquals(1, client.cancelCalls);
        assertEquals(1, recorder.cancelCalls);
        assertFalse(recorder.recording);
        assertEquals(BridgeCaptureStatus.State.READY, statuses.last().state);
    }

    @Test
    public void repeatedCancelDoesNotDuplicateCancel() {
        FakeEnvironment environment = new FakeEnvironment();
        FakeSessionClient client = new FakeSessionClient();
        FakeRecorder recorder = new FakeRecorder(true);
        Statuses statuses = new Statuses();
        BridgeCaptureCoordinator coordinator = newCoordinator(environment, client, recorder, statuses);

        coordinator.startCapture();
        coordinator.cancelActiveCapture("window hidden");
        coordinator.cancelActiveCapture("finish input");
        coordinator.destroy();

        assertEquals(1, client.cancelCalls);
        assertEquals(1, recorder.cancelCalls);
        assertEquals(0, client.finishCalls);
    }

    @Test
    public void releaseWhileStartingFinishesAfterRecorderStarts() {
        FakeEnvironment environment = new FakeEnvironment();
        FakeSessionClient client = new FakeSessionClient();
        FakeRecorder recorder = new FakeRecorder(true);
        Statuses statuses = new Statuses();
        QueuedExecutor executor = new QueuedExecutor();
        BridgeCaptureCoordinator coordinator = new BridgeCaptureCoordinator(
            environment,
            client,
            recorder,
            statuses,
            executor
        );

        coordinator.startCapture();
        coordinator.finishCapture();
        executor.runNext();

        assertEquals(1, client.beginCalls);
        assertEquals(1, client.finishCalls);
        assertEquals(0, client.cancelCalls);
        assertEquals(1, recorder.stopCalls);
        assertFalse(recorder.recording);
        assertEquals(BridgeCaptureStatus.State.READY, statuses.last().state);
    }

    @Test
    public void frameWritesAreQueuedOffRecorderCallback() {
        FakeEnvironment environment = new FakeEnvironment();
        FakeSessionClient client = new FakeSessionClient();
        FakeRecorder recorder = new FakeRecorder(true);
        Statuses statuses = new Statuses();
        QueuedExecutor executor = new QueuedExecutor();
        BridgeCaptureCoordinator coordinator = new BridgeCaptureCoordinator(
            environment,
            client,
            recorder,
            statuses,
            executor
        );

        coordinator.startCapture();
        executor.runNext();
        recorder.emit(new byte[] {1, 0}, 12);

        assertEquals(0, client.writeCalls);
        assertEquals(BridgeCaptureStatus.State.RECORDING, statuses.last().state);

        executor.runNext();

        assertEquals(1, client.writeCalls);
    }

    @Test
    public void cancelWhileStartingCancelsOnceAndIgnoresLateFrames() {
        FakeEnvironment environment = new FakeEnvironment();
        FakeSessionClient client = new FakeSessionClient();
        FakeRecorder recorder = new FakeRecorder(true);
        Statuses statuses = new Statuses();
        QueuedExecutor executor = new QueuedExecutor();
        BridgeCaptureCoordinator coordinator = new BridgeCaptureCoordinator(
            environment,
            client,
            recorder,
            statuses,
            executor
        );

        coordinator.startCapture();
        coordinator.cancelActiveCapture("window hidden");
        coordinator.cancelActiveCapture("finish input");
        executor.runNext();
        recorder.emit(new byte[] {1, 0}, 12);

        assertEquals(1, client.beginCalls);
        assertEquals(0, client.writeCalls);
        assertEquals(0, client.finishCalls);
        assertEquals(1, client.cancelCalls);
        assertEquals(1, recorder.cancelCalls);
        assertFalse(recorder.recording);
    }

    @Test
    public void repeatedRecorderErrorCancelsOnlyOnce() {
        FakeEnvironment environment = new FakeEnvironment();
        FakeSessionClient client = new FakeSessionClient();
        FakeRecorder recorder = new FakeRecorder(true);
        Statuses statuses = new Statuses();
        BridgeCaptureCoordinator coordinator = newCoordinator(environment, client, recorder, statuses);

        coordinator.startCapture();
        recorder.error("read failed");
        recorder.error("read failed again");

        assertEquals(1, client.cancelCalls);
        assertEquals(1, recorder.cancelCalls);
        assertEquals(0, client.finishCalls);
    }

    @Test
    public void recorderStartFailureCancelsBegunSession() {
        FakeEnvironment environment = new FakeEnvironment();
        FakeSessionClient client = new FakeSessionClient();
        FakeRecorder recorder = new FakeRecorder(false);
        Statuses statuses = new Statuses();
        BridgeCaptureCoordinator coordinator = newCoordinator(environment, client, recorder, statuses);

        coordinator.startCapture();

        assertEquals(1, client.beginCalls);
        assertEquals(1, client.cancelCalls);
        assertEquals(BridgeCaptureStatus.State.FAILED, statuses.last().state);
        assertEquals("audio record failed", statuses.last().message);
    }

    @Test
    public void sensitiveFieldFailsBeforeBinding() {
        FakeEnvironment environment = new FakeEnvironment();
        environment.sensitive = true;
        FakeSessionClient client = new FakeSessionClient();
        FakeRecorder recorder = new FakeRecorder(true);
        Statuses statuses = new Statuses();
        BridgeCaptureCoordinator coordinator = newCoordinator(environment, client, recorder, statuses);

        coordinator.startCapture();

        assertEquals(0, client.beginCalls);
        assertEquals(0, recorder.startCalls);
        assertEquals(BridgeCaptureStatus.State.FAILED, statuses.last().state);
        assertEquals("sensitive field", statuses.last().message);
    }

    private BridgeCaptureCoordinator newCoordinator(
        FakeEnvironment environment,
        FakeSessionClient client,
        FakeRecorder recorder,
        Statuses statuses
    ) {
        return new BridgeCaptureCoordinator(environment, client, recorder, statuses, directExecutor);
    }

    private static final class FakeEnvironment implements BridgeCaptureCoordinator.CaptureEnvironment {
        boolean hasInputConnection = true;
        boolean sensitive;

        @Override
        public boolean hasInputConnection() {
            return hasInputConnection;
        }

        @Override
        public boolean isSensitiveField() {
            return sensitive;
        }
    }

    private static final class FakeSessionClient implements BridgeCaptureCoordinator.SessionClient {
        int beginCalls;
        int writeCalls;
        int finishCalls;
        int cancelCalls;
        String activeSessionId;

        @Override
        public BridgeCaptureCoordinator.OperationResult begin(String sessionId) {
            beginCalls++;
            activeSessionId = sessionId;
            return BridgeCaptureCoordinator.OperationResult.ok("ok");
        }

        @Override
        public BridgeCaptureCoordinator.OperationResult writeFrame(
            String sessionId,
            byte[] pcm,
            int sampleRate,
            int channels
        ) {
            if (activeSessionId == null || !activeSessionId.equals(sessionId)) {
                return BridgeCaptureCoordinator.OperationResult.error(
                    BridgeContract.PCM_RESULT_STALE_SESSION,
                    "stale session"
                );
            }
            writeCalls++;
            return BridgeCaptureCoordinator.OperationResult.ok("ok");
        }

        @Override
        public BridgeCaptureCoordinator.OperationResult finish(String sessionId) {
            finishCalls++;
            activeSessionId = null;
            return BridgeCaptureCoordinator.OperationResult.ok("ok");
        }

        @Override
        public BridgeCaptureCoordinator.OperationResult cancel(String sessionId) {
            cancelCalls++;
            activeSessionId = null;
            return BridgeCaptureCoordinator.OperationResult.ok("ok");
        }

        @Override
        public void close() {
            activeSessionId = null;
        }
    }

    private static final class QueuedExecutor implements Executor {
        final List<Runnable> tasks = new ArrayList<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        void runNext() {
            assertTrue("expected queued task", !tasks.isEmpty());
            tasks.remove(0).run();
        }
    }

    private static final class FakeRecorder implements BridgeCaptureCoordinator.AudioRecorder {
        final boolean startResult;
        int startCalls;
        int stopCalls;
        int cancelCalls;
        boolean recording;
        String sessionId;
        BridgeCaptureCoordinator.AudioRecorderCallback callback;

        FakeRecorder(boolean startResult) {
            this.startResult = startResult;
        }

        @Override
        public boolean start(String sessionId, BridgeCaptureCoordinator.AudioRecorderCallback callback) {
            startCalls++;
            if (!startResult) return false;
            this.recording = true;
            this.sessionId = sessionId;
            this.callback = callback;
            return true;
        }

        @Override
        public void stop() {
            stopCalls++;
            recording = false;
        }

        @Override
        public void cancel() {
            cancelCalls++;
            recording = false;
        }

        @Override
        public boolean isRecording() {
            return recording;
        }

        void emit(byte[] pcm, int amplitude) {
            if (callback != null && sessionId != null) {
                callback.onPcmFrame(sessionId, pcm, PcmAudioRecorder.SAMPLE_RATE, PcmAudioRecorder.CHANNELS, amplitude);
            }
        }

        void error(String message) {
            if (callback != null && sessionId != null) {
                callback.onRecorderError(sessionId, message);
            }
        }
    }

    private static final class Statuses implements BridgeCaptureCoordinator.StatusListener {
        final List<BridgeCaptureStatus> values = new ArrayList<>();

        @Override
        public void onCaptureStatusChanged(BridgeCaptureStatus status) {
            values.add(status);
        }

        BridgeCaptureStatus last() {
            assertTrue("expected at least one status", !values.isEmpty());
            return values.get(values.size() - 1);
        }
    }
}
