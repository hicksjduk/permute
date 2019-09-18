package uk.org.thehickses.permute;

import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import uk.org.thehickses.permute.CheckpointManager.CheckpointOutputHandler;
import uk.org.thehickses.permute.CheckpointManager.Checkpointer;

public class CheckpointManagerTest
{
    private static final int CHECKPOINT_INTERVAL = 1;
    private static final int ITERATION_COUNT = 3;
    private static final int CHECKPOINTER_COUNT = 5;

    private final CheckpointOutputHandler handler = mock(CheckpointOutputHandler.class);

    @Test
    void testNoInitStrings() throws Exception
    {
        test(new CheckpointManager(CHECKPOINT_INTERVAL, handler), Stream.empty());
    }

    @Test
    void testWithInitStringsInStream() throws Exception
    {
        test(new CheckpointManager(CHECKPOINT_INTERVAL, handler, prefixes()), prefixes());
    }

    @Test
    void testWithInitStringsInCollection() throws Exception
    {
        test(new CheckpointManager(CHECKPOINT_INTERVAL, handler,
                prefixes().limit(3).collect(Collectors.toList())), prefixes().limit(3));
    }

    @Test
    void testWithInitStringsInArray() throws Exception
    {
        test(new CheckpointManager(CHECKPOINT_INTERVAL, handler, prefixes().toArray(String[]::new)),
                prefixes());
    }

    private Stream<String> prefixes()
    {
        return IntStream
                .range(0, CHECKPOINTER_COUNT)
                .map(i -> 'a' + i)
                .mapToObj(i -> "" + (char) i);
    }

    private void test(CheckpointManager mgr, Stream<String> expectedPrefixes) throws Exception
    {
        String[] iPrefixes = expectedPrefixes.toArray(String[]::new);
        Stream.Builder<ForkJoinTask<?>> sb = Stream.builder();
        IntStream
                .range(0, CHECKPOINTER_COUNT)
                .mapToObj(i -> checkpointingProcess(mgr))
                .map(ForkJoinPool.commonPool()::submit)
                .forEach(sb);
        sb.build().forEach(ForkJoinTask::join);
        Thread.sleep(TimeUnit.SECONDS.toMillis(CHECKPOINT_INTERVAL));
        IntStream
                .range(0, ITERATION_COUNT)
                .mapToObj(i -> IntStream
                        .range(0, CHECKPOINTER_COUNT)
                        .mapToObj(j -> j < iPrefixes.length ? iPrefixes[j] : "")
                        .map(p -> p + i)
                        .collect(Collectors.joining("\n")))
                .forEach(cp -> verify(handler).handleOutput(cp));
        verify(handler).handleOutput("");
        verifyNoMoreInteractions(handler);
    }

    private Runnable checkpointingProcess(CheckpointManager mgr)
    {
        long sleepTime = TimeUnit.SECONDS.toMillis(CHECKPOINT_INTERVAL);
        return () -> {
            Checkpointer checkpointer = mgr.register();
            String initString = Optional.ofNullable(checkpointer.initString).orElse("");
            sleep(sleepTime / 5);
            IntStream.range(0, ITERATION_COUNT).forEach(i -> {
                checkpointer.checkpoint(initString + i);
                sleep(sleepTime);
            });
            checkpointer.deregister();
        };
    }

    private void sleep(long interval)
    {
        try
        {
            Thread.sleep(interval);
        }
        catch (InterruptedException ex)
        {
            throw new RuntimeException("Thread was interrupted", ex);
        }
    }
}
