package uk.org.thehickses.permute;

import static org.assertj.core.api.Assertions.*;

import java.util.Iterator;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class PermuterTest
{
    @Test
    void test5()
    {
        assertThat(new Permuter<>("a", "b", "c", "d", "e").permute().count())
                .isEqualTo(LongStream.rangeClosed(1, 5).reduce((a, b) -> a * b).getAsLong());
    }

    @Test
    void test10()
    {
        assertThat(
                new Permuter<>("a", "b", "c", "d", "e", "f", "g", "h", "i", "j").permute().count())
                        .isEqualTo(
                                LongStream.rangeClosed(1, 10).reduce((a, b) -> a * b).getAsLong());
    }

    @Test
    void testWithValidator()
    {
        PartialResultValidator<Stream<String>> rejectIfIncludesBImmediatelyFollowedByC = str -> {
            String string = str.collect(Collectors.joining());
            boolean invalid = string.indexOf("bc") != -1;
            if (invalid)
                throw new ValidationException();
        };
        Iterator<String> expected = Stream
                .of("abdc", "acbd", "acdb", "adcb", "bacd", "badc", "bdac", "bdca", "cabd", "cadb",
                        "cbad", "cbda", "cdab", "cdba", "dacb", "dbac", "dcab", "dcba")
                .collect(Collectors.toCollection(TreeSet::new))
                // .sorted()
                .iterator();
        new Permuter<>(rejectIfIncludesBImmediatelyFollowedByC, "a", "b", "c", "d")
                .permute()
                .map(comb -> comb.collect(Collectors.joining()))
                .collect(Collectors.toCollection(TreeSet::new))
                .stream()
                // .sorted()
                .forEach(str -> assertThat(str).isEqualTo(expected.next()));
        assertThat(expected.hasNext())
                .describedAs("Not enough results, there are expected results left")
                .isFalse();
    }
}
