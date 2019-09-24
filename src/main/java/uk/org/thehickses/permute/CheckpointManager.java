package uk.org.thehickses.permute;

import java.util.Collection;
import java.util.List;
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
    private SortedMap<Integer, String> currentCheckpoints = new TreeMap<>();
    private final List<String> initStrings;

    CheckpointManager(String... initStrings)
    {
        this(null, null, Stream.of(initStrings));
    }

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
        this(new Integer(outputIntervalInSeconds), outputHandler, initStrings);
    }

    private CheckpointManager(Integer outputIntervalInSeconds,
            CheckpointOutputHandler outputHandler, Stream<String> initStrings)
    {
        this.initStrings = initStrings.collect(Collectors.toList());
        if (outputIntervalInSeconds == null)
            return;
        if (outputIntervalInSeconds <= 0)
            throw new IllegalArgumentException("Output interval must be at least one second");
        scheduleCheckpointTask(outputIntervalInSeconds, outputHandler);
    }

    public int getInitStringCount()
    {
        return initStrings.size();
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
        String initString = id >= initStrings.size() ? null : initStrings.get(id);
        return new Checkpointer(id, initString);
    }

    void deregister(int id)
    {
        synchronized (currentCheckpoints)
        {
            currentCheckpoints.remove(id);
        }
    }

    void checkpoint(int id, String cpString)
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

        Checkpointer(int id, String initString)
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
