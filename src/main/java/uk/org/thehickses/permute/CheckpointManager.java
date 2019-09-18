package uk.org.thehickses.permute;

import java.util.Collection;
import java.util.Objects;
import java.util.SortedMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CheckpointManager
{
    private final static Logger CHECKPOINT_LOGGER = LoggerFactory.getLogger("checkpoint");

    private final AtomicInteger idGenerator = new AtomicInteger();
    private final SortedMap<Integer, String> currentCheckpoints;

    public CheckpointManager(int outputIntervalInSeconds, String... initStrings)
    {
        this(outputIntervalInSeconds, null, Stream.of(initStrings));
    }

    public CheckpointManager(int outputIntervalInSeconds, CheckpointOutputHandler outputHandler,
            String... initStrings)
    {
        this(outputIntervalInSeconds, outputHandler, Stream.of(initStrings));
    }

    public CheckpointManager(int outputIntervalInSeconds, Collection<String> initStrings)
    {
        this(outputIntervalInSeconds, null, initStrings.stream());
    }

    public CheckpointManager(int outputIntervalInSeconds, CheckpointOutputHandler outputHandler,
            Collection<String> initStrings)
    {
        this(outputIntervalInSeconds, outputHandler, initStrings.stream());
    }

    public CheckpointManager(int outputIntervalInSeconds, Stream<String> initStrings)
    {
        this(outputIntervalInSeconds, null, initStrings);
    }

    public CheckpointManager(int outputIntervalInSeconds, CheckpointOutputHandler outputHandler,
            Stream<String> initStrings)
    {
        AtomicInteger id = new AtomicInteger();
        this.currentCheckpoints = new TreeMap<>(
                initStrings.collect(Collectors.toMap(str -> id.getAndIncrement(), str -> str)));
        scheduleCheckpointTask(outputIntervalInSeconds, outputHandler);
    }

    private void scheduleCheckpointTask(int outputIntervalInSeconds,
            CheckpointOutputHandler outputHandler)
    {
        CheckpointOutputHandler handler = outputHandler != null ? outputHandler
                : this::writeCheckpointToLog;
        AtomicReference<String> lastData = new AtomicReference<>();
        TimerTask checkpointTask = new RunnableTimerTask(() -> {
            String data = checkpointData();
            if (!Objects.equals(lastData.getAndSet(data), data))
                handler.handleOutput(data);
        });
        long interval = TimeUnit.SECONDS.toMillis(outputIntervalInSeconds);
        new Timer().schedule(checkpointTask, interval, interval);
    }

    public Checkpointer register()
    {
        int id = idGenerator.getAndIncrement();
        String initString;
        synchronized (currentCheckpoints)
        {
            initString = currentCheckpoints.get(id);
        }
        return new Checkpointer(id, initString);
    }

    private void deregister(int id)
    {
        synchronized (currentCheckpoints)
        {
            currentCheckpoints.remove(id);
        }
    }

    private void checkpoint(int id, String cpString)
    {
        synchronized (currentCheckpoints)
        {
            currentCheckpoints.put(id, cpString);
        }
    }

    private void writeCheckpointToLog(String data)
    {
        if (data.equals(""))
            CHECKPOINT_LOGGER.info("Checkpoint: No checkpoints created");
        else
            CHECKPOINT_LOGGER.info("Checkpoint:\n{}", data);
    }

    private String checkpointData()
    {
        synchronized (currentCheckpoints)
        {
            return currentCheckpoints.values().stream().collect(Collectors.joining("\n"));
        }
    }

    public class Checkpointer
    {
        private final int id;
        public final String initString;

        private Checkpointer(int id, String initString)
        {
            this.id = id;
            this.initString = initString;
        }

        public CheckpointManager manager()
        {
            return CheckpointManager.this;
        }

        public void deregister()
        {
            CheckpointManager.this.deregister(id);
        }

        public void checkpoint(String cpString)
        {
            CheckpointManager.this.checkpoint(id, cpString);
        }
    }

    @FunctionalInterface
    public static interface CheckpointOutputHandler
    {
        void handleOutput(String data);
    }

    private static class RunnableTimerTask extends TimerTask
    {
        private final Runnable whatToDo;

        public RunnableTimerTask(Runnable whatToDo)
        {
            this.whatToDo = whatToDo;
        }

        @Override
        public void run()
        {
            whatToDo.run();
        }
    }
}
