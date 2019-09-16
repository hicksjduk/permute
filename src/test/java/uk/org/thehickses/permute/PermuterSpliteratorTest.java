package uk.org.thehickses.permute;

import static org.assertj.core.api.Assertions.*;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.PrimitiveIterator.OfInt;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Disabled;
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
                .map(str -> IntStream
                        .range(0, str.length())
                        .mapToObj(i -> str.substring(i, i + 1))
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
        PartialResultValidator<IntStream> rejectIfSecondIndexTwoMoreThanTheFirst = str -> {
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
        PartialResultValidator<IntStream> rejectIfAnyIndexOneMoreThanThePrevious = str -> {
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

    @Disabled("Until I work out how to make the method work properly")
    void testEstimateSize()
    {
        int maxIndex = 5;
        PermuterSpliterator spl = new PermuterSpliterator(maxIndex);
        AtomicLong maxSize = new AtomicLong(IntStream
                .rangeClosed(2, maxIndex)
                .mapToLong(i -> i)
                .reduce((a, b) -> a * b)
                .getAsLong());
        spl
                .forEachRemaining(comb -> assertThat(spl.estimateSize())
                        .isEqualTo(maxSize.getAndDecrement()));
        assertThat(maxSize).isEqualTo(0);
    }
}
