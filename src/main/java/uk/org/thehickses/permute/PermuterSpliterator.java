package uk.org.thehickses.permute;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class PermuterSpliterator implements Spliterator<IntStream>
{
    // private static final Logger LOG = LoggerFactory.getLogger(PermuterSpliterator.class);

    private final int maxIndex;
    private final Deque<Deque<Integer>> indices;

    public PermuterSpliterator(int maxIndex)
    {
        this(0, maxIndex);
    }

    public PermuterSpliterator(int minIndex, int maxIndex)
    {
        this.maxIndex = maxIndex;
        (indices = new ArrayDeque<>())
                .add(IntStream
                        .range(minIndex, maxIndex)
                        .boxed()
                        .collect(Collectors.toCollection(ArrayDeque::new)));
    }

    private void calculateNext()
    {
        if (indices.size() == maxIndex)
        {
            while (true)
            {
                Deque<Integer> lastQueue = indices.peekLast();
                lastQueue.pop();
                if (lastQueue.isEmpty())
                {
                    indices.removeLast();
                    if (indices.isEmpty())
                        return;
                }
                else
                    break;
            }
        }
        while (indices.size() < maxIndex)
        {
            Set<Integer> usedIndices = indices
                    .stream()
                    .map(Deque::peekFirst)
                    .collect(Collectors.toSet());
            Deque<Integer> nextEntry = IntStream
                    .range(0, maxIndex)
                    .boxed()
                    .filter(i -> !usedIndices.contains(i))
                    .collect(Collectors.toCollection(ArrayDeque::new));
            indices.add(nextEntry);
        }
    }

    @Override
    public boolean tryAdvance(Consumer<? super IntStream> action)
    {
        IntStream next;
        synchronized (indices)
        {
            calculateNext();
            if (indices.isEmpty())
                return false;
            IntStream.Builder builder = IntStream.builder();
            indices.stream().map(Deque::peekFirst).forEach(builder::add);
            next = builder.build();
        }
        action.accept(next);
        return true;
    }

    @Override
    public Spliterator<IntStream> trySplit()
    {
        synchronized (indices)
        {
            Deque<Integer> firstIndices = indices.peekFirst();
            if (firstIndices.size() > 1)
            {
                Integer currentStart = firstIndices.peekFirst();
                indices.removeFirst();
                indices
                        .push(IntStream
                                .range(currentStart, currentStart + 1)
                                .boxed()
                                .collect(Collectors.toCollection(ArrayDeque::new)));
                PermuterSpliterator answer = new PermuterSpliterator(currentStart + 1, maxIndex);
                return answer;
            }
        }
        return null;
    }

    @Override
    public long estimateSize()
    {
        return Long.MAX_VALUE;
    }

    @Override
    public int characteristics()
    {
        return DISTINCT | IMMUTABLE | NONNULL;
    }

    @Override
    public String toString()
    {
        return toString(indices.stream().map(Deque::stream).map(this::toString));
    }

    private <T> String toString(Stream<T> str)
    {
        return "[" + str.map(Object::toString).collect(Collectors.joining(", ")) + "]";
    }
}
