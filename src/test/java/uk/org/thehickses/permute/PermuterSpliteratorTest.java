package uk.org.thehickses.permute;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.PrimitiveIterator.OfInt;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PermuterSpliteratorTest
{
    private static final boolean printEnabled = false;

    void printIfEnabled(Object obj)
    {
        if (printEnabled)
            System.out.println(obj);
    }

    @ParameterizedTest
    @MethodSource("testValues")
    void test(Integer maxNum, Deque<Deque<Integer>> expected)
    {
        PermuterSpliterator spl = new PermuterSpliterator(maxNum);
        checkResults(spl, expected);
    }

    private static Deque<Deque<Integer>> dequeOfDeques(String... expectedResults)
    {
        return Stream
                .of(expectedResults)
                .map(str -> str
                        .chars()
                        .mapToObj(i -> "" + (char) i)
                        .map(Integer::parseInt)
                        .collect(Collectors.toCollection(ArrayDeque::new)))
                .collect(Collectors.toCollection(ArrayDeque::new));
    }

    private void checkResults(PermuterSpliterator spl, Deque<Deque<Integer>> expected)
    {
        spl.forEachRemaining(str -> {
            Deque<Integer> exp = expected.pop();
            printIfEnabled(str);
            str.peek(this::printIfEnabled).forEach(i -> assertThat(i).isEqualTo(exp.pop()));
        });
        assertThat(expected).isEmpty();
    }

    static Stream<Arguments> testValues()
    {
        return Stream
                .of(Arguments.of(3, dequeOfDeques("012", "021", "102", "120", "201", "210")),
                        Arguments
                                .of(4, dequeOfDeques("0123", "0132", "0213", "0231", "0312", "0321",
                                        "1023", "1032", "1203", "1230", "1302", "1320", "2013",
                                        "2031", "2103", "2130", "2301", "2310", "3012", "3021",
                                        "3102", "3120", "3201", "3210")));
    }

    @Test
    void testWithValidator1()
    {
        IntPartialResultValidator rejectIfSecondIndexTwoMoreThanTheFirst = str -> {
            OfInt it = str.limit(2).iterator();
            if (it.hasNext())
            {
                int first = it.next();
                if (it.hasNext())
                    if (first - it.next() == -2)
                        throw new ValidationException();
            }
        };
        PermuterSpliterator spl = new PermuterSpliterator(4,
                rejectIfSecondIndexTwoMoreThanTheFirst);
        Deque<Deque<Integer>> expected = dequeOfDeques("0123", "0132", "0312", "0321", "1023",
                "1032", "1203", "1230", "2013", "2031", "2103", "2130", "2301", "2310", "3012",
                "3021", "3102", "3120", "3201", "3210");
        checkResults(spl, expected);
    }

    @Test
    void testWithValidator2()
    {
        IntPartialResultValidator rejectIfAnyIndexOneMoreThanThePrevious = str -> {
            OfInt it = str.iterator();
            int last = Integer.MIN_VALUE;
            while (it.hasNext())
            {
                int value = it.next();
                if (value - last == 1)
                    throw new ValidationException();
                last = value;
            }
        };
        PermuterSpliterator spl = new PermuterSpliterator(4,
                rejectIfAnyIndexOneMoreThanThePrevious);
        Deque<Deque<Integer>> expected = dequeOfDeques("0213", "0321", "1032", "1302", "1320",
                "2031", "2103", "2130", "3021", "3102", "3210");
        checkResults(spl, expected);
    }

    @Test
    void testEstimateSize()
    {
        int maxIndex = 5;
        PermuterSpliterator spl = new PermuterSpliterator(maxIndex);
        AtomicLong maxSize = new AtomicLong(
                LongStream.rangeClosed(2, maxIndex).reduce((a, b) -> a * b).getAsLong());
        assertThat(spl.estimateSize()).isEqualTo(maxSize.get());
        spl
                .forEachRemaining(
                        x -> assertThat(spl.estimateSize()).isEqualTo(maxSize.decrementAndGet()));
        assertThat(maxSize.get() == 0);
    }

    @Test
    void testEstimateSizeWithBigSpliterator()
    {
        PermuterSpliterator spl = new PermuterSpliterator(21);
        assertThat(spl.estimateSize()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void testToCheckpointString()
    {
        PermuterSpliterator spl = new PermuterSpliterator(5);
        assertThat(spl.toCheckpointString()).isEqualTo("0,1,2,3,4/1,2,3,4/2,3,4/3,4/4");
    }

    @Test
    void testFromCheckpointStringDataIsValid()
    {
        Deque<Deque<Integer>> expected = Stream
                .of("123", "4", "70")
                .map(String::chars)
                .map(str -> str
                        .mapToObj(ch -> "" + (char) ch)
                        .map(Integer::valueOf)
                        .collect(Collectors.toCollection(ArrayDeque::new)))
                .collect(Collectors.toCollection(ArrayDeque::new));
        Deque<Deque<Integer>> actual = PermuterSpliterator.fromCheckpointString("1,2,3/4/7,0");
        actual.forEach(q -> assertThat(q).containsExactlyElementsOf(expected.pop()));
        assertThat(expected.isEmpty()).describedAs("More expected results than actual").isTrue();
    }

    @Test
    void testFromCheckpointStringDataIsInvalid()
    {
        assertThrows(NumberFormatException.class,
                () -> PermuterSpliterator.fromCheckpointString("1,2,3/asda/7,0"));
    }

    void testWithCheckpointing(boolean parallel, int expectedSpliteratorCount,
            Stream<String> initStrings, Stream<String> expectedCheckpoints,
            Stream<String> expectedResults)
    {
        CheckpointManager mgr = mock(CheckpointManager.class);
        AtomicInteger id = new AtomicInteger();
        Iterator<String> inits = initStrings.iterator();
        when(mgr.register())
                .thenAnswer(ioc -> mgr.new Checkpointer(id.getAndIncrement(),
                        inits.hasNext() ? inits.next() : null));
        PermuterSpliterator spl = new PermuterSpliterator(3, mgr);
        String[] actual = StreamSupport
                .stream(spl, parallel)
                .map(res -> res.mapToObj(Integer::toString).collect(Collectors.joining()))
                .toArray(String[]::new);
        assertThat(actual).containsExactlyInAnyOrder(expectedResults.toArray(String[]::new));
        verify(mgr, times(expectedSpliteratorCount)).register();
        expectedCheckpoints
                .map(str -> str.split(":"))
                .forEach(args -> verify(mgr)
                        .checkpoint(Integer.parseInt(args[0]), args.length > 1 ? args[1] : ""));
        IntStream.range(0, expectedSpliteratorCount).forEach(i -> verify(mgr).deregister(i));
        verifyNoMoreInteractions(mgr);
    }

    @Test
    void testWithCheckpointingNoInitStringsNotParallel()
    {
        testWithCheckpointing(false, 1, Stream.empty(),
                Stream
                        .of("0,1,2/2/1", "1,2/0,2/2", "1,2/2/0", "2/0,1/1", "2/1/0", "")
                        .map("0:"::concat),
                Stream.of("012", "021", "102", "120", "201", "210"));
    }

    @Test
    void testWithCheckpointingNoInitStringsParallel()
    {
        testWithCheckpointing(true, 3, Stream.empty(),
                Stream.of("0:0/2/1", "0:", "1:1/2/0", "1:", "2:2/1/0", "2:"),
                Stream.of("012", "021", "102", "120", "201", "210"));
    }

    @Test
    void testWithCheckpointingOneInitStringNotParallel()
    {
        testWithCheckpointing(false, 1, Stream.of("1,2/0,2/2"),
                Stream.of("0:1,2/2/0", "0:2/0,1/1", "0:2/1/0", "0:"),
                Stream.of("102", "120", "201", "210"));
    }

    @Test
    void testWithCheckpointingOneInitStringParallel()
    {
        testWithCheckpointing(true, 2, Stream.of("1,2/0,2/2"),
                Stream.of("0:1/2/0", "0:", "1:2/1/0", "1:"),
                Stream.of("102", "120", "201", "210"));
    }

    @Test
    void testWithCheckpointingTwoInitStringsNotParallel()
    {
        testWithCheckpointing(false, 1, Stream.of("1,2/0,2/2", "2/1/0"),
                Stream.of("0:1,2/2/0", "0:2/0,1/1", "0:2/1/0", "0:"),
                Stream.of("102", "120", "201", "210"));
    }

    @Test
    void testWithCheckpointingTwoInitStringsParallel()
    {
        testWithCheckpointing(true, 2, Stream.of("1,2/0,2/2", "2/1/0"),
                Stream.of("0:1/2/0", "0:", "1:"),
                Stream.of("102", "120", "210"));
    }
}
