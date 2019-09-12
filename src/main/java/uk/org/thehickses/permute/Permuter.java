package uk.org.thehickses.permute;

import java.util.Collection;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class Permuter<T>
{
    private final T[] items;

    @SafeVarargs
    public Permuter(T... items)
    {
        this.items = items;
    }

    @SuppressWarnings("unchecked")
    public Permuter(Stream<T> items)
    {
        this((T[]) items.toArray());
    }

    public Permuter(Collection<T> items)
    {
        this(items.stream());
    }

    public Stream<Stream<T>> permute()
    {
        return StreamSupport
                .stream(new PermuterSpliterator(items.length), true)
                .map(comb -> comb.mapToObj(i -> items[i]));
    }
}
