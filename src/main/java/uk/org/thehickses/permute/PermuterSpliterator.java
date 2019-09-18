package uk.org.thehickses.permute;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.LongBinaryOperator;
import java.util.function.LongSupplier;
import java.util.function.ToLongBiFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

public class PermuterSpliterator implements Spliterator<IntStream>
{
    // private static final Logger LOG = LoggerFactory.getLogger(PermuterSpliterator.class);

    private final int maxIndex;
    private final Deque<Deque<Integer>> indices;
    private final IntPartialResultValidator partialResultValidator;

    private static ToLongBiFunction<Long, LongSupplier> increaserWithMaximum(
            LongBinaryOperator increaser, LongBinaryOperator inverse, long maximum)
    {
        return (a, bGetter) -> {
            if (a >= maximum)
                return maximum;
            long b = bGetter.getAsLong();
            if (b >= maximum)
                return maximum;
            return inverse.applyAsLong(maximum, a) < b ? maximum : increaser.applyAsLong(a, b);
        };
    }

    private static final ToLongBiFunction<Long, LongSupplier> ADDER = increaserWithMaximum(
            (a, b) -> a + b, (a, b) -> a - b, Long.MAX_VALUE);
    private static final ToLongBiFunction<Long, LongSupplier> MULTIPLIER = increaserWithMaximum(
            (a, b) -> a * b, (a, b) -> a / b, Long.MAX_VALUE);

    private long add(long a, LongSupplier b)
    {
        return ADDER.applyAsLong(a, b);
    }

    private long multiply(long a, long b)
    {
        return MULTIPLIER.applyAsLong(a, () -> b);
    }

    public PermuterSpliterator(int maxIndex)
    {
        this(0, maxIndex, null);
    }

    public PermuterSpliterator(int maxIndex, IntPartialResultValidator partialResultValidator)
    {
        this(0, maxIndex, partialResultValidator);
    }

    private PermuterSpliterator(int minIndex, int maxIndex,
            IntPartialResultValidator partialResultValidator)
    {
        this(maxIndex,
                Stream
                        .of(IntStream
                                .range(minIndex, maxIndex)
                                .boxed()
                                .collect(Collectors.toCollection(ArrayDeque::new))),
                partialResultValidator);
    }

    private PermuterSpliterator(int maxIndex, Stream<Deque<Integer>> indices,
            IntPartialResultValidator partialResultValidator)
    {
        this.maxIndex = maxIndex;
        this.indices = maxIndex == 0 ? new ArrayDeque<>()
                : indices.collect(Collectors.toCollection(ArrayDeque::new));
        this.partialResultValidator = partialResultValidator;
        boolean alreadyValidated = false;
        while (!this.indices.isEmpty())
            try
            {
                if (!alreadyValidated)
                    validate();
                fillUp();
                break;
            }
            catch (ValidationException ex)
            {
                calculateNext();
                alreadyValidated = true;
            }
    }

    private void calculateNext()
    {
        while (true)
            try
            {
                incrementLast();
                if (indices.isEmpty())
                    return;
                fillUp();
                break;
            }
            catch (ValidationException ex)
            {
                continue;
            }

    }

    private void incrementLast() throws ValidationException
    {
        while (true)
        {
            Deque<Integer> lastQueue = indices.peekLast();
            lastQueue.pop();
            if (!lastQueue.isEmpty())
                break;
            indices.removeLast();
            if (indices.isEmpty())
                return;
        }
        validate();
    }

    private void fillUp() throws ValidationException
    {
        Set<Integer> usedIndices = indices
                .stream()
                .map(Deque::peekFirst)
                .collect(Collectors.toSet());
        for (int s = indices.size(); s < maxIndex; s++)
        {
            Deque<Integer> nextEntry = IntStream
                    .range(0, maxIndex)
                    .boxed()
                    .filter(i -> !usedIndices.contains(i))
                    .collect(Collectors.toCollection(ArrayDeque::new));
            indices.add(nextEntry);
            usedIndices.add(nextEntry.peekFirst());
            validate();
        }
    }

    private void validate() throws ValidationException
    {
        if (partialResultValidator != null)
            partialResultValidator.validate(currentIndices());
    }

    @Override
    public boolean tryAdvance(Consumer<? super IntStream> action)
    {
        IntStream current;
        synchronized (indices)
        {
            if (indices.isEmpty())
                return false;
            current = currentIndices();
            calculateNext();
        }
        action.accept(current);
        return true;
    }

    private IntStream currentIndices()
    {
        IntStream.Builder builder = IntStream.builder();
        synchronized (indices)
        {
            indices.stream().map(Deque::peekFirst).forEach(builder::add);
        }
        return builder.build();
    }

    @Override
    public Spliterator<IntStream> trySplit()
    {
        Integer currentStart = null;
        synchronized (indices)
        {
            Deque<Integer> firstIndices = indices.peekFirst();
            if (firstIndices.size() > 1)
            {
                currentStart = firstIndices.peekFirst();
                indices.removeFirst();
                indices
                        .push(Stream
                                .of(currentStart)
                                .collect(Collectors.toCollection(ArrayDeque::new)));
            }
        }
        return currentStart == null ? null
                : new PermuterSpliterator(currentStart + 1, maxIndex, partialResultValidator);
    }

    @Override
    public long estimateSize()
    {
        int[] queueSizes;
        synchronized (indices)
        {
            if (indices.isEmpty())
                return 0;
            queueSizes = indices.stream().mapToInt(Deque::size).toArray();
        }
        return estimateSize(0, queueSizes);
    }

    private long estimateSize(int startIndex, int[] queueSizes)
    {
        if (queueSizes.length <= startIndex)
            return 0;
        if (queueSizes.length - startIndex == 1)
            return queueSizes[startIndex];
        long answer = estimateSize(startIndex + 1, queueSizes);
        if (queueSizes[startIndex] > 1)
            answer = add(answer, () -> multiply(queueSizes[startIndex] - 1,
                    LongStream.range(2, maxIndex - startIndex).reduce(this::multiply).orElse(1)));
        return answer;
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

    String toCheckpointString()
    {
        synchronized (indices)
        {
            return indices
                    .stream()
                    .map(q -> q.stream().map(Object::toString).collect(Collectors.joining(",")))
                    .collect(Collectors.joining("/"));
        }
    }

    static Stream<Deque<Integer>> fromCheckpointString(String str)
    {
        return Stream
                .of(str.split("/"))
                .map(s -> Stream
                        .of(s.split(","))
                        .map(Integer::valueOf)
                        .collect(Collectors.toCollection(ArrayDeque::new)));
    }
}
