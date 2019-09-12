package uk.org.thehickses.permute;

import static org.assertj.core.api.Assertions.*;

import java.util.ArrayDeque;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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
    void test(Integer maxNum, Stream<String> expectedResults)
    {
        PermuterSpliterator spl = new PermuterSpliterator(maxNum);
        ArrayDeque<ArrayDeque<Integer>> expected = expectedResults
                .map(str -> IntStream
                        .range(0, str.length())
                        .mapToObj(i -> str.substring(i, i + 1))
                        .map(Integer::parseInt)
                        .collect(Collectors.toCollection(ArrayDeque::new)))
                .collect(Collectors.toCollection(ArrayDeque::new));
        spl.forEachRemaining(str -> {
            ArrayDeque<Integer> exp = expected.pop();
            printIfEnabled(str);
            str.peek(this::printIfEnabled).forEach(i -> assertThat(i).isEqualTo(exp.pop()));
        });
    }

    static Stream<Arguments> testValues()
    {
        return Stream
                .of(Arguments.of(3, Stream.of("012", "021", "102", "120", "201", "210")),
                        Arguments
                                .of(4, Stream
                                        .of("0123", "0132", "0213", "0231", "0312", "0321", "1023",
                                                "1032", "1203", "1230", "1302", "1320", "2013",
                                                "2031", "2103", "2130", "2301", "2310", "3012",
                                                "3021", "3102", "3120", "3201", "3210")));
    }

}
