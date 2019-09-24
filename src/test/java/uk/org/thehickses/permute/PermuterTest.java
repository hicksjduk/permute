package uk.org.thehickses.permute;

import static org.assertj.core.api.Assertions.*;

import java.util.Arrays;
import java.util.Iterator;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PermuterTest
{
    @ParameterizedTest
    @ValueSource(ints = { 0, 1, 2, 5, 10 })
    void testLength(int valueCount)
    {
        String[] values = IntStream
                .range('a', 'a' + valueCount)
                .mapToObj(i -> "" + (char) i)
                .toArray(String[]::new);
        long expectedCount = LongStream
                .rangeClosed(1, valueCount)
                .reduce((a, b) -> a * b)
                .orElse(0);
        assertThat(new Permuter<>(values).permute().count()).isEqualTo(expectedCount);
    }

    @Test
    void testWithValidator()
    {
        PartialResultValidator<String> rejectIfIncludesBImmediatelyFollowedByC = str -> {
            String string = str.collect(Collectors.joining());
            boolean invalid = string.indexOf("bc") != -1;
            if (invalid)
                throw new ValidationException();
        };
        Iterator<String> expected = Stream
                .of("abdc", "acbd", "acdb", "adcb", "bacd", "badc", "bdac", "bdca", "cabd", "cadb",
                        "cbad", "cbda", "cdab", "cdba", "dacb", "dbac", "dcab", "dcba")
                .sorted()
                .iterator();
        new Permuter<>(rejectIfIncludesBImmediatelyFollowedByC, "a", "b", "c", "d")
                .permute()
                .map(comb -> comb.collect(Collectors.joining()))
                .collect(Collectors.toCollection(TreeSet::new))
                .stream()
                .forEach(str -> assertThat(str).isEqualTo(expected.next()));
        assertThat(expected.hasNext())
                .describedAs("Not enough results, there are expected results left")
                .isFalse();
    }

    @Test
    void testWithInputStream()
    {
        assertThat(new Permuter<>(Stream.of("a", "b", "c")).permute().count()).isEqualTo(6);
    }

    @Test
    void testWithInputStreamAndValidator()
    {
        PartialResultValidator<String> validator = comb -> {
            if (comb.findFirst().get().equals("a"))
                throw new ValidationException();
        };
        assertThat(new Permuter<>(validator, Stream.of("a", "b", "c")).permute().count())
                .isEqualTo(4);
    }

    @Test
    void testWithInputCollection()
    {
        assertThat(new Permuter<>(Arrays.asList("a", "b", "c")).permute().count()).isEqualTo(6);
    }

    @Test
    void testWithInputCollectionAndValidator()
    {
        PartialResultValidator<String> validator = comb -> {
            String firstElement = comb.findFirst().get();
            if (Stream.of("a", "b").anyMatch(firstElement::equals))
                throw new ValidationException();
        };
        assertThat(new Permuter<>(validator, Arrays.asList("a", "b", "c")).permute().count())
                .isEqualTo(2);
    }

    @Test
    void testWithCheckpointManager()
    {
        CheckpointManager checkpointManager = new CheckpointManager(0,
                "0/1,2,3,4,5/2,3,4,5/3,4,5/5/4", "1/3/2/4/5/0", "2,3/3,4/0/1/5/4",
                "4/0/1/2,3,5/3,5/5", "5/4/3/2/1,0/0");
        assertThat(new Permuter<>("abcdef".chars().mapToObj(ch -> "" + (char) ch))
                .permute(checkpointManager)
                .count()).isEqualTo(273);
    }
}
