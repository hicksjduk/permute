package uk.org.thehickses.permute;

import java.util.Collection;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class Permuter<T>
{
    private final T[] items;
    private final PartialResultValidator<T> partialResultValidator;

    @SafeVarargs
    public Permuter(T... items)
    {
        this(null, items);
    }

    @SafeVarargs
    public Permuter(PartialResultValidator<T> partialResultValidator, T... items)
    {
        this.items = items;
        this.partialResultValidator = partialResultValidator;
    }

    @SuppressWarnings("unchecked")
    public Permuter(Stream<T> items)
    {
        this(null, (T[]) items.toArray());
    }

    @SuppressWarnings("unchecked")
    public Permuter(PartialResultValidator<T> partialResultValidator, Stream<T> items)
    {
        this(partialResultValidator, (T[]) items.toArray());
    }

    public Permuter(Collection<T> items)
    {
        this(null, items.stream());
    }

    public Permuter(PartialResultValidator<T> partialResultValidator, Collection<T> items)
    {
        this(partialResultValidator, items.stream());
    }

    public Stream<Stream<T>> permute()
    {
        return permute(null);
    }

    public Stream<Stream<T>> permute(CheckpointManager checkpointManager)
    {
        IntPartialResultValidator validator = partialResultValidator == null ? null
                : str -> partialResultValidator.validate(objectsAtIndices(str));
        int spliteratorCount = checkpointManager == null ? 1
                : Math.max(1, checkpointManager.getInitStringCount());
        PermuterSpliterator[] spliterators = IntStream
                .range(0, spliteratorCount)
                .mapToObj(i -> new PermuterSpliterator(items.length, validator, checkpointManager))
                .toArray(PermuterSpliterator[]::new);
        return Stream
                .of(spliterators)
                .parallel()
                .flatMap(spl -> StreamSupport.stream(spl, true))
                .map(this::objectsAtIndices);
    }

    private Stream<T> objectsAtIndices(IntStream indices)
    {
        return indices.mapToObj(i -> items[i]);
    }
}
